package live.collog.app.features.timeline.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import live.collog.app.config.AppEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.models.fromDto
import live.collog.app.features.timeline.models.CallAnalysisBundle
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.features.timeline.models.TimelineWeekPage
import live.collog.app.features.timeline.models.WeekBounds
import live.collog.app.features.timeline.models.fromBundle
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.chart.from
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.CollogApi

/** iOS `TimelineViewModel`: 주 단위 페이지(리포트와 통화 기록)를 가족 구성원별로 불러온다. */
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

    private val baselineCache = mutableMapOf<String, Map<String, BaselineDto>>()
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

    fun selectMember(contact: FamilyContact) {
        if (selectedContactId == contact.id) return
        selectedContactId = contact.id
        selectedMember = contact.name
        selectedRelation = contact.relation
        weekOffset = 0
        pages = emptyMap()
        loading.clear()
        contentGeneration += 1
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
        val offsets = if (forceReload) listOf(anchor) else listOf(anchor, anchor - 1, anchor + 1)
        for (offset in offsets) {
            if (offset > 0) continue
            load(offset, context.parentId, context.api, context.baselines, generation, forceReload)
        }
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
        load(offset, context.parentId, context.api, context.baselines, generation, forceReload)
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

    private class Context(val parentId: String, val api: CollogApi, val baselines: Map<String, BaselineDto>)

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

        return Context(parentId, api, baselines)
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
            selectedMember = environment.session.user?.name ?: selectedMember
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
        return contacts.firstOrNull { it.id == selectedContactId } ?: contacts.firstOrNull()
    }

    private suspend fun load(
        offset: Int,
        parentId: String,
        api: CollogApi,
        baselines: Map<String, BaselineDto>,
        generation: Int,
        forceReload: Boolean = false,
    ) {
        if (generation != contentGeneration) return
        if (offset in loading) return
        if (!forceReload && pages[offset]?.isLoaded == true) return
        loading += offset
        try {
            val bounds = TimelineWeekPage.bounds(offset)
            var page = pages[offset] ?: TimelineWeekPage(offset)
            page = page.copy(reportError = null, timelineError = null, isLoaded = false)
            pages = pages + (offset to page)

            try {
                val dto = api.report(parentId, date = TimelineWeekPage.isoDate.format(bounds.anchor))
                val history = dto.recentAcousticHistory ?: dto.acousticTrends
                val trend = history
                    .firstOrNull { it.metric == "SPEECH_RATE" }
                    ?.let { TrendSeries.from(it, baselines["SPEECH_RATE"]) }

                page = page.copy(report = WeeklyReport.fromDto(dto, trend))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                page = page.copy(reportError = e.message)
            }

            try {
                val content = entries(parentId, api, baselines, bounds)
                page = page.copy(calls = content.first, entries = content.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                page = page.copy(timelineError = e.message)
            }
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
    ): Pair<List<CallSummaryDto>, List<CallTimelineEntry>> {
        val calls = api.calls(
            parentId,
            from = TimelineWeekPage.isoDate.format(bounds.start),
            to = TimelineWeekPage.isoDate.format(bounds.end),
        )
        val analyzed = calls.filter { it.isAnalyzed }.take(5)
        if (analyzed.isEmpty()) return calls to emptyList()

        val result = mutableListOf<CallTimelineEntry>()
        for (call in analyzed) {
            val bundle = coroutineScope {
                val features = async { orNull { api.acousticFeatures(call.callId) } }
                val extraction = async { orNull { api.extraction(call.callId) } }
                val transcript = async { orNull { api.transcript(call.callId) } }
                CallAnalysisBundle(call, features.await(), extraction.await(), transcript.await())
            }
            result += CallTimelineEntry.fromBundle(bundle, baselines)
        }

        return calls to result
    }

    /** Swift `try?`: 실패하면 null. 취소는 삼키지 않는다. */
    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
