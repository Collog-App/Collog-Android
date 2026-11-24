package live.collog.app.features.timeline.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import live.collog.app.config.AppEnvironment
import live.collog.app.features.health.models.LifeRecord
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.models.fromDto
import live.collog.app.features.report.models.withCallStats
import live.collog.app.features.timeline.models.CallAnalysisBundle
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.features.timeline.models.TimelineWeekPage
import live.collog.app.features.timeline.models.WeekBounds
import live.collog.app.features.timeline.models.WeeklySpeechHistory
import live.collog.app.features.timeline.models.fromBundle
import live.collog.app.shared.services.AppEvents
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.CollogApi

/** iOS `TimelineViewModel`: 주 단위 페이지(리포트와 통화 기록)를 가족 구성원별로(나도 포함) 불러온다. */
class TimelineViewModel(selectedTabIndex: Int = 1) {
    var selectedTabIndex by mutableIntStateOf(selectedTabIndex)

    var pages by mutableStateOf<Map<Int, TimelineWeekPage>>(emptyMap())
        private set
    var selectedMember by mutableStateOf("가족")
        private set
    var selectedContactId by mutableStateOf<String?>(null)
        private set
    var selectedRelation by mutableStateOf<String?>(null)
        private set
    var weekOffset by mutableIntStateOf(0)
        private set

    /** 칩에서 "나"를 직접 골랐는지. 가족이 없어 대신 고른 "나"는 가족 목록이 오면 첫 가족으로 돌아간다. */
    private var pickedSelf = false

    /** 리포트에 들어왔지만 볼 사람이 아직 정해지지 않아 남기지 못한 `report_view` 의 code. */
    private var pendingReportView: String? = null

    private val baselineCache = mutableMapOf<String, Map<String, BaselineDto>>()
    private val historyCache = mutableMapOf<String, WeeklySpeechHistory>()
    private val usualMinutesCache = mutableMapOf<String, Int?>()
    private val loading = mutableSetOf<Int>()
    private var contentGeneration = 0

    val title: String get() = if (this.selectedTabIndex == 0) "리포트" else "타임라인"

    val canGoForward: Boolean get() = weekOffset < 0

    val week: TimelineWeekPage get() = page(weekOffset)

    fun page(offset: Int): TimelineWeekPage = pages[offset] ?: TimelineWeekPage(offset)

    fun setWeek(offset: Int) {
        if (offset > 0) return
        weekOffset = offset
    }

    /** 리포트·타임라인에서 고를 사람: 가족(지금 순서) 뒤에 "나". 통화 상대(`FamilyStore.contacts`)에는 넣지 않는다. */
    fun subjects(environment: AppEnvironment): List<FamilyContact> =
        environment.family.contacts + listOfNotNull(self(environment))

    fun selectMember(contact: FamilyContact) {
        if (selectedContactId == contact.id) return
        selectedContactId = contact.id
        pickedSelf = contact.id == SelfContactId
        selectedMember = contact.name
        selectedRelation = contact.relation
        weekOffset = 0
        pages = emptyMap()
        loading.clear()
        contentGeneration += 1
    }

    /**
     * 이용 기록 `report_view`: 사람이 리포트 화면에 들어왔거나 보는 사람을 바꿨다([code] = DIRECT·NOTIFICATION).
     * 자동 새로 고침·주 넘기기·당겨서 새로 고침은 부르지 않는다. 타임라인 탭은 남기지 않는다.
     */
    fun recordReportView(environment: AppEnvironment, code: String) {
        if (selectedTabIndex != 0) return
        pendingReportView = code
        recordPendingReportView(environment)
    }

    /** 가족 목록을 받기 전에 잠깐 고른 "나"로 남기지 않게, 목록을 받은 뒤(가족이 없으면 바로) 보고 있는 사람으로 남긴다. */
    fun recordPendingReportView(environment: AppEnvironment) {
        val code = pendingReportView ?: return
        if (!environment.family.isLoaded) return
        val subjectId = selectedContact(environment)?.userId ?: return
        pendingReportView = null
        environment.events.record(AppEvents.REPORT_VIEW, code = code, subjectId = subjectId)
    }

    suspend fun refresh(environment: AppEnvironment, forceReload: Boolean = false) {
        resolveSelection(environment)
        val generation = contentGeneration

        if (environment.settings.isGuestMode) {
            val anchor = weekOffset
            listOf(anchor, anchor - 1, anchor + 1).filter { it <= 0 }.forEach(::loadGuestPage)
            return
        }

        val context = loadContext(environment)
        if (context == null) {
            if (generation != contentGeneration) return
            setLoadFailure(weekOffset, environment)
            return
        }
        if (generation != contentGeneration) return

        val anchor = weekOffset
        // 강제 새로고침은 지금 주만 다시 받는다. 다른 주는 공유를 다시 켠 뒤처럼 낡았을 수 있어 버리고, 보일 때 다시 받는다
        if (forceReload) pages = pages.filterKeys { it == anchor }
        val offsets = if (forceReload) listOf(anchor) else listOf(anchor, anchor - 1, anchor + 1)
        for (offset in offsets) {
            if (offset > 0) continue
            load(offset, context, generation, forceReload)
        }
    }

    /**
     * 앱으로 돌아왔을 때: 그사이 가족이 공유를 껐거나 다시 켰으면 불러 둔 주를 모두 버리고 다시 읽는다(공유는 주마다가 아니라 사람마다 꺼진다).
     * 바뀌지 않았으면 화면을 건드리지 않게, 지금 주의 리포트와 통화 목록이 열리는지만 조용히 묻는다.
     */
    suspend fun revalidateSharing(environment: AppEnvironment) {
        if (environment.settings.isGuestMode) return
        val contact = selectedContact(environment)
        val parentId = contact?.userId ?: return
        if (contact.id == SelfContactId) return
        val offset = weekOffset
        val page = pages[offset]?.takeIf { it.isLoaded } ?: return
        val generation = contentGeneration
        val api = environment.api
        val bounds = TimelineWeekPage.bounds(offset)

        val report = ApiError.accessCheck { api.report(parentId, date = TimelineWeekPage.isoDate.format(bounds.anchor)) }
        val calls = ApiError.accessCheck {
            api.calls(
                parentId,
                from = TimelineWeekPage.isoDate.format(bounds.start),
                to = TimelineWeekPage.isoDate.format(bounds.end),
            )
        }
        if (generation != contentGeneration) return
        val reportChanged = report != null && report.first != page.reportNotShared
        val timelineChanged = calls != null && calls.first != page.timelineNotShared
        if (!reportChanged && !timelineChanged) return

        pages = emptyMap()
        loading.clear()
        contentGeneration += 1
        refresh(environment)
    }

    suspend fun loadPage(offset: Int, environment: AppEnvironment, forceReload: Boolean = false) {
        resolveSelection(environment)
        val generation = contentGeneration

        if (environment.settings.isGuestMode) {
            loadGuestPage(offset)
            return
        }

        if (offset > 0) return
        val context = loadContext(environment)
        if (context == null) {
            if (generation != contentGeneration) return
            setLoadFailure(offset, environment)
            return
        }
        if (generation != contentGeneration) return
        load(offset, context, generation, forceReload)
    }

    private fun setLoadFailure(offset: Int, environment: AppEnvironment) {
        pages = pages + (
            offset to TimelineWeekPage(
                offset = offset,
                isLoaded = true,
                reportError = environment.family.loadError,
                timelineError = environment.family.loadError,
            )
            )
    }

    private fun loadGuestPage(offset: Int) {
        if (offset > 0 || pages[offset]?.isLoaded == true) return
        pages = pages + (
            offset to TimelineWeekPage(
                offset = offset,
                report = WeeklyReport.sample(selectedRelation, offset),
                entries = if (offset >= -3) listOf(CallTimelineEntry.sample(offset, selectedRelation)) else emptyList(),
                isLoaded = true,
            )
            )
    }

    private class Context(
        val parentId: String,
        val api: CollogApi,
        val baselines: Map<String, BaselineDto>,
        val history: WeeklySpeechHistory,
        val usualCallMinutes: Int?,
        /** 로그인한 나. 분석에서 빠진 통화는 내가 낀 통화만 추출 결과를 읽을 수 있다. */
        val userId: String?,
    )

    private suspend fun loadContext(environment: AppEnvironment): Context? {
        val parentId = selectedContact(environment)?.userId ?: return null
        val api = environment.api

        val baselines = baselineCache[parentId] ?: run {
            val loaded = (orNull { api.baselines(parentId) } ?: emptyList())
                .filter { it.kind == "ROLLING" && it.isReady }
                .associateBy { it.metric }
            baselineCache[parentId] = loaded
            loaded
        }

        val history = historyCache.getOrPut(parentId) { WeeklySpeechHistory(api, parentId, baselines["SPEECH_RATE"]) }
        val usualMinutes = if (usualMinutesCache.containsKey(parentId)) {
            usualMinutesCache[parentId]
        } else {
            val usual = usualCallMinutes(orNull { api.calls(parentId) } ?: emptyList())
            usualMinutesCache[parentId] = usual
            usual
        }

        return Context(parentId, api, baselines, history, usualMinutes, environment.session.user?.id)
    }

    private fun resolveSelection(environment: AppEnvironment) {
        val contact = selectedContact(environment)
        if (contact == null) {
            if (selectedContactId != null) {
                selectedContactId = null
                selectedRelation = null
                pages = emptyMap()
                loading.clear()
                contentGeneration += 1
            }
            selectedMember = environment.session.user?.let { it.nickname ?: it.name } ?: selectedMember
            return
        }
        if (selectedContactId != contact.id) {
            pages = emptyMap()
            loading.clear()
            contentGeneration += 1
        }
        selectedContactId = contact.id
        selectedMember = contact.name
        selectedRelation = contact.relation
    }

    private fun selectedContact(environment: AppEnvironment): FamilyContact? {
        val contacts = environment.family.contacts
        val self = self(environment)
        if (pickedSelf && self != null) return self
        return contacts.firstOrNull { it.id == selectedContactId } ?: contacts.firstOrNull()
            // 가족이 없으면 나를 본다. 가족 목록을 못 불러온 때는 그 오류를 보인다
            ?: self?.takeIf { environment.family.loadError == null }
    }

    /** 분석 대상으로서의 나(`/parents/{내 id}/...`). 둘러보기에는 없다. */
    private fun self(environment: AppEnvironment): FamilyContact? {
        if (environment.settings.isGuestMode) return null
        val user = environment.session.user ?: return null
        return FamilyContact(id = SelfContactId, userId = user.id, name = "나", relation = "", lastCallText = "")
    }

    private suspend fun load(
        offset: Int,
        context: Context,
        generation: Int,
        forceReload: Boolean = false,
    ) {
        val parentId = context.parentId
        val api = context.api
        val baselines = context.baselines
        if (generation != contentGeneration) return
        if (offset in loading) return
        if (!forceReload && pages[offset]?.isLoaded == true) return
        loading += offset
        try {
            val bounds = TimelineWeekPage.bounds(offset)
            var page = pages[offset] ?: TimelineWeekPage(offset)
            page = page.copy(
                reportError = null,
                timelineError = null,
                reportNotShared = false,
                timelineNotShared = false,
                isLoaded = false,
            )
            pages = pages + (offset to page)

            try {
                val dto = api.report(parentId, date = TimelineWeekPage.isoDate.format(bounds.anchor))
                // 새 피그마 음향 추세: 최근 6주 주별 평균(통화가 없는 주는 건너뜀)
                context.history.record(offset, dto)
                val trend = if (dto.analyzedCallCount > 0) context.history.trend(offset) else null

                page = page.copy(report = WeeklyReport.fromDto(dto, trend))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                page = if (e.isNotShared) page.copy(reportNotShared = true) else page.copy(reportError = e.message)
            }

            try {
                val content = entries(parentId, api, baselines, bounds, context.usualCallMinutes, context.userId)
                val previous = TimelineWeekPage.bounds(offset - 1)
                val previousCalls = orNull {
                    api.calls(
                        parentId,
                        from = TimelineWeekPage.isoDate.format(previous.start),
                        to = TimelineWeekPage.isoDate.format(previous.end),
                    )
                }
                page = page.copy(
                    calls = content.first,
                    entries = content.second,
                    report = page.report.withCallStats(content.first, previousCalls),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                page = if (e.isNotShared) page.copy(timelineNotShared = true) else page.copy(timelineError = e.message)
            }
            if (selectedTabIndex == 0) page = page.copy(lifeRecord = lifeRecord(api, parentId, bounds))
            if (generation != contentGeneration) return
            pages = pages + (offset to page.copy(isLoaded = true))
        } finally {
            if (generation == contentGeneration) loading -= offset
        }
    }

    private suspend fun entries(
        parentId: String,
        api: CollogApi,
        baselines: Map<String, BaselineDto>,
        bounds: WeekBounds,
        usualCallMinutes: Int?,
        userId: String?,
    ): Pair<List<CallSummaryDto>, List<CallTimelineEntry>> {
        val calls = api.calls(
            parentId,
            from = TimelineWeekPage.isoDate.format(bounds.start),
            to = TimelineWeekPage.isoDate.format(bounds.end),
        )
        // 짧아서 분석에서 빠진 통화도 서버가 요약을 남겼으면 보인다. 최근 것부터 5개까지.
        // 빠진 통화의 추출 결과는 통화한 두 사람만 읽을 수 있어(다른 사람은 403) 내가 낀 통화만 묻는다
        val candidates = calls.filter {
            it.isAnalyzed || (it.isAnalysisExcluded && userId != null && userId in listOf(it.parentId, it.childId))
        }
        if (candidates.isEmpty()) return calls to emptyList()

        val result = mutableListOf<CallTimelineEntry>()
        for (call in candidates) {
            if (result.size >= 5) break
            val bundle = if (call.isAnalyzed) {
                coroutineScope {
                    val features = async { orNull { api.acousticFeatures(call.callId) } }
                    val extraction = async { orNull { api.extraction(call.callId) } }
                    val transcript = async { orNull { api.transcript(call.callId) } }
                    CallAnalysisBundle(call, features.await(), extraction.await(), transcript.await())
                }
            } else {
                // 음향 지표·되물음은 없으므로 요약만 받는다. 요약이 없으면(404 포함) 지금처럼 숨긴다
                val extraction = orNull { api.extraction(call.callId) }
                if (extraction?.summary.isNullOrBlank()) continue
                CallAnalysisBundle(call, features = null, extraction = extraction, transcript = null)
            }
            result += CallTimelineEntry.fromBundle(bundle, baselines, usualCallMinutes)
        }

        return calls to result
    }

    /**
     * 리포트 「생활 기록」: 그 주 끝(이번 주는 오늘)까지 30일을 받아 7일 막대와 최근 측정값을 만든다.
     * 서버에 아직 API 가 없거나(404) 공유가 막혔으면 null 이라 카드를 숨긴다.
     */
    private suspend fun lifeRecord(api: CollogApi, parentId: String, bounds: WeekBounds): LifeRecord? {
        val today = TimelineWeekPage.healthToday()
        val end = minOf(bounds.end, today)
        val dto = orNull {
            api.parentHealthDaily(
                parentId,
                from = TimelineWeekPage.isoDate.format(end.minusDays(29)),
                to = TimelineWeekPage.isoDate.format(end),
            )
        } ?: return null
        return LifeRecord.from(dto, end, today)
    }

    /** 새 피그마 「평소 N분」: 분석된 최근 통화(최대 10건)의 평균 길이. 3건 미만이면 null. */
    private fun usualCallMinutes(calls: List<CallSummaryDto>): Int? {
        val recent = calls.filter { it.isAnalyzed && (it.durationSec ?: 0) > 0 }
            .sortedByDescending { it.startedAt }
            .take(10)
        if (recent.size < 3) return null
        return (recent.sumOf { it.durationSec ?: 0 } / recent.size + 59) / 60
    }

    /** 가족이 「가족 공유 데이터 범위」에서 그 항목 공유를 끄면 대상(`/parents/{id}/...`) 요청이 403 이다. */
    private val Exception.isNotShared: Boolean get() = this is ApiError.Server && status == 403

    /** Swift `try?`: 실패하면 null. 취소는 삼키지 않는다. */
    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** "나" 항목의 id. 가족 구성원 id(UUID)와 겹치지 않는다. 탭은 사용자마다 새로 그려지므로 고정값이어도 된다. */
        const val SelfContactId = "self"
    }
}
