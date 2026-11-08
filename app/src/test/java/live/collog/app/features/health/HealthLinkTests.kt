package live.collog.app.features.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import live.collog.app.MemoryPreferences
import live.collog.app.TestEnvironment
import live.collog.app.features.health.models.HealthRecords
import live.collog.app.features.health.services.HealthAvailability
import live.collog.app.features.health.services.HealthLink
import live.collog.app.features.health.services.HealthLinkStatus
import live.collog.app.features.health.services.HealthLinkStep
import live.collog.app.features.health.services.HealthRecordSource
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHealthSource : HealthRecordSource {
    var availability = HealthAvailability.Available
    var granted: Set<String> = HealthLink.ReadPermissions
    var records = HealthRecords()
    val reads = mutableListOf<Set<String>>()
    var revoked = false

    override fun availability(): HealthAvailability = availability
    override suspend fun grantedPermissions(): Set<String> = granted
    override suspend fun read(days: List<LocalDate>, granted: Set<String>): HealthRecords {
        reads += granted
        return records
    }
    override suspend fun revokeAll() {
        revoked = true
    }
}

/** 건강 앱 연결 약속(2026-11-08). 서버가 아직 없으므로 경로·본문·응답 해석과 404 처리를 가짜 서버로 확인한다. */
class HealthLinkTests {
    private val today = LocalDate.of(2026, 11, 8)
    private var clock = Instant.parse("2026-11-08T01:00:00Z")
    private val document =
        """{"version":"health-link-2026-11-08.v1","title":"건강 앱 연결 (선택)","items":["하나","둘","셋","넷","다섯","여섯"]}"""

    private fun status(connected: Boolean, lastSyncedDay: String? = null, doc: String = document) =
        """{"connected":$connected,"consentVersion":null,"connectedAt":null,"document":$doc,
            "lastSyncedDay":${lastSyncedDay?.let { "\"$it\"" } ?: "null"}}"""

    private fun withLink(body: suspend (TestEnvironment, HealthLink, FakeHealthSource, () -> LocalDate) -> Unit) = runTest {
        val fixture = TestEnvironment()
        val source = FakeHealthSource()
        var day = today
        val link = HealthLink(source, MemoryPreferences(), today = { day }, now = { clock })
        try {
            body(fixture, link, source) { day = day.plusDays(1); day }
        } finally {
            fixture.cleanUp()
        }
    }

    private fun Request.json(): JsonObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private fun Request.dates(): List<String> = json()["days"]!!.jsonArray.map { it.jsonObject["date"]!!.jsonPrimitive.content }

    private fun TestEnvironment.puts(): List<Request> = requests.filter { it.method == "PUT" }

    @Test
    fun serverWithoutHealthApiIsQuietlyNotReady() = withLink { fixture, link, _, _ ->
        fixture.respond = { 404 to """{"detail":"Not Found"}""" }

        link.syncIfDue(fixture.environment)
        assertEquals(HealthLinkStatus.Unsupported, link.statusFor("me"))
        assertEquals("준비 중", link.rowText("me"))
        assertFalse(link.shouldShowHint(fixture.environment))
        assertEquals(listOf("/v1/health/link"), fixture.paths.toList())

        link.present()
        link.prepare(fixture.environment)
        assertEquals(HealthLinkStep.NotReady, link.step)
    }

    @Test
    fun notReadyIsAskedAgainWhenTheAppComesBackAfterThirtyMinutes() = withLink { fixture, link, _, _ ->
        var ready = false
        fixture.respond = { if (ready) 200 to status(connected = false) else 404 to """{"detail":"Not Found"}""" }

        link.syncIfDue(fixture.environment)
        assertEquals("준비 중", link.rowText("me"))

        clock = clock.plus(Duration.ofMinutes(29))
        link.syncIfDue(fixture.environment)
        assertEquals(1, fixture.requests.count { it.method == "GET" })

        // 그 사이 서버에 API 가 생겼다
        ready = true
        clock = clock.plus(Duration.ofMinutes(2))
        link.syncIfDue(fixture.environment)
        assertEquals(2, fixture.requests.count { it.method == "GET" })
        assertEquals("연결 안 됨", link.rowText("me"))
        assertTrue(link.shouldShowHint(fixture.environment))
    }

    @Test
    fun notConnectedShowsServerDocumentAndHomeHint() = withLink { fixture, link, _, _ ->
        fixture.respond = { 200 to status(connected = false) }

        link.prepare(fixture.environment)
        val step = link.step as HealthLinkStep.Consent
        assertEquals("health-link-2026-11-08.v1", step.document.version)
        assertEquals("건강 앱 연결 (선택)", step.document.title)
        assertEquals(6, step.document.items.size)
        assertEquals("연결 안 됨", link.rowText("me"))
        // 다른 계정의 상태는 보이지 않는다
        assertEquals(HealthLinkStatus.Unknown, link.statusFor("someone"))

        assertTrue(link.shouldShowHint(fixture.environment))
        link.dismissHint(fixture.environment)
        assertFalse(link.shouldShowHint(fixture.environment))
    }

    @Test
    fun missingOrOldHealthConnectAsksForInstallAndUnsupportedDeviceHidesTheHint() = withLink { fixture, link, source, _ ->
        fixture.respond = { 200 to status(connected = false) }

        source.availability = HealthAvailability.NeedsInstall
        link.prepare(fixture.environment)
        assertEquals(HealthLinkStep.NeedsInstall, link.step)
        assertTrue(link.shouldShowHint(fixture.environment))

        source.availability = HealthAvailability.Unavailable
        link.prepare(fixture.environment)
        assertEquals(HealthLinkStep.Unavailable, link.step)
        link.refreshStatus(fixture.environment)
        assertFalse(link.shouldShowHint(fixture.environment))
        assertEquals("지원 안 함", link.rowText("me"))
    }

    @Test
    fun noPermissionAtAllDoesNotLink() = withLink { fixture, link, source, _ ->
        fixture.respond = { 200 to status(connected = false) }
        source.granted = emptySet()
        link.prepare(fixture.environment)
        val consent = link.step as HealthLinkStep.Consent

        link.beginConnect(consent.document)
        link.completeConnect(fixture.environment)

        assertEquals(HealthLinkStep.PermissionDenied, link.step)
        assertTrue(fixture.requests.none { it.method == "POST" })
    }

    @Test
    fun anyPermissionLinksAndUploadsTodayRightAway() = withLink { fixture, link, source, _ ->
        source.granted = setOf(HealthLink.READ_STEPS, "android.permission.health.READ_WEIGHT")
        source.records = HealthRecords(stepsByDay = mapOf(today to 3210L))
        fixture.respond = { request ->
            when (request.method) {
                "GET" -> 200 to status(connected = false)
                "POST" -> 200 to status(connected = true)
                "PUT" -> 200 to """{"saved":1,"lastSyncedDay":"2026-11-08"}"""
                else -> 500 to "{}"
            }
        }
        link.prepare(fixture.environment)
        link.beginConnect((link.step as HealthLinkStep.Consent).document)
        link.completeConnect(fixture.environment)

        val post = fixture.requests.single { it.method == "POST" }
        assertEquals("/v1/health/link", post.url.encodedPath)
        assertEquals("health-link-2026-11-08.v1", post.json()["version"]!!.jsonPrimitive.content)
        assertEquals("HEALTH_CONNECT", post.json()["platform"]!!.jsonPrimitive.content)

        val put = fixture.requests.single { it.method == "PUT" }
        assertEquals("/v1/health/daily", put.url.encodedPath)
        val days = put.json()["days"]!!.jsonArray
        // 최근 14일을 모두 보낸다(오늘까지)
        assertEquals(14, days.size)
        assertEquals("2026-10-26", days.first().jsonObject["date"]!!.jsonPrimitive.content)
        val day = days.last().jsonObject
        assertEquals("2026-11-08", day["date"]!!.jsonPrimitive.content)
        assertEquals(3210, day["steps"]!!.jsonPrimitive.int)
        assertFalse("sleepMinutes" in day)
        // 우리 읽기 권한만 넘긴다
        assertEquals(listOf(setOf(HealthLink.READ_STEPS)), source.reads)

        assertEquals(HealthLinkStep.Linked(justConnected = true, lastSyncedDay = "2026-11-08", hasPermission = true), link.step)
        assertEquals("연결됨", link.rowText("me"))
        assertFalse(link.shouldShowHint(fixture.environment))
        assertTrue(link.revision > 0)
    }

    @Test
    fun changedConsentDocumentIsShownAgain() = withLink { fixture, link, _, _ ->
        val newer = """{"version":"health-link-2026-12-01.v2","title":"건강 앱 연결 (선택)","items":["바뀐 문서"]}"""
        var posted = false
        fixture.respond = { request ->
            when (request.method) {
                "POST" -> {
                    posted = true
                    409 to """{"code":"HEALTH_CONSENT_VERSION_MISMATCH","message":"동의 문서가 바뀌었어요"}"""
                }
                else -> 200 to status(connected = false, doc = if (posted) newer else document)
            }
        }
        link.prepare(fixture.environment)
        link.beginConnect((link.step as HealthLinkStep.Consent).document)
        link.completeConnect(fixture.environment)

        val step = link.step as HealthLinkStep.Consent
        assertEquals("health-link-2026-12-01.v2", step.document.version)
        assertNotNull(step.notice)
        assertTrue(fixture.requests.none { it.method == "PUT" })
    }

    @Test
    fun foregroundSyncRunsAtMostOncePerDay() = withLink { fixture, link, source, nextDay ->
        source.records = HealthRecords(stepsByDay = mapOf(today to 100L, today.plusDays(1) to 200L))
        fixture.respond = { request ->
            if (request.method == "PUT") 200 to """{"saved":1,"lastSyncedDay":"2026-11-08"}""" else 200 to status(connected = true)
        }

        link.syncIfDue(fixture.environment)
        link.syncIfDue(fixture.environment)
        assertEquals(1, fixture.requests.count { it.method == "PUT" })

        nextDay()
        link.syncIfDue(fixture.environment)
        assertEquals(2, fixture.requests.count { it.method == "PUT" })
        // 앱이 앞으로 올 때마다 상태를 다시 묻지 않는다
        assertEquals(1, fixture.requests.count { it.method == "GET" })
    }

    @Test
    fun uploadRefusedForUnlinkedUserMarksNotConnected() = withLink { fixture, link, source, _ ->
        source.records = HealthRecords(stepsByDay = mapOf(today to 100L))
        fixture.respond = { request ->
            if (request.method == "PUT") {
                403 to """{"code":"HEALTH_LINK_REQUIRED","message":"건강 앱 연결이 필요해요"}"""
            } else {
                200 to status(connected = true)
            }
        }
        link.syncIfDue(fixture.environment)

        val current = link.statusFor("me") as HealthLinkStatus.Loaded
        assertFalse(current.link.connected)
        assertEquals("연결 안 됨", link.rowText("me"))
    }

    @Test
    fun unprocessableUploadIsSentOnceMoreWithoutToday() = withLink { fixture, link, source, _ ->
        source.records = HealthRecords(stepsByDay = mapOf(today to 100L, today.minusDays(1) to 200L))
        var puts = 0
        fixture.respond = { request ->
            if (request.method == "PUT") {
                puts += 1
                if (puts == 1) {
                    422 to """{"code":"VALIDATION_ERROR","message":"date"}"""
                } else {
                    200 to """{"saved":13,"lastSyncedDay":"2026-11-07"}"""
                }
            } else {
                200 to status(connected = true)
            }
        }
        link.syncIfDue(fixture.environment)

        val (first, second) = fixture.puts()
        assertEquals(14, first.dates().size)
        assertEquals(13, second.dates().size)
        assertFalse("2026-11-08" in second.dates())
        assertEquals("2026-11-07", second.dates().last())
        assertEquals("2026-11-07", (link.statusFor("me") as HealthLinkStatus.Loaded).link.lastSyncedDay)

        // 보냈으니 오늘은 더 보내지 않는다
        link.syncIfDue(fixture.environment)
        assertEquals(2, fixture.puts().size)
    }

    @Test
    fun uploadRefusedAgainFoldsTheDayButServerErrorsAreRetried() = withLink { fixture, link, source, nextDay ->
        source.records = HealthRecords(stepsByDay = mapOf(today to 100L))
        var putStatus = 422
        fixture.respond = { request ->
            if (request.method == "PUT") putStatus to """{"code":"VALIDATION_ERROR","message":"범위"}""" else 200 to status(connected = true)
        }

        link.syncIfDue(fixture.environment)
        // 422 → 오늘을 빼고 한 번 더, 그래도 4xx 면 그날은 접는다
        assertEquals(2, fixture.puts().size)
        link.syncIfDue(fixture.environment)
        assertEquals(2, fixture.puts().size)

        // 다음 날 서버 오류(5xx)는 접지 않고 앱이 다시 앞으로 올 때 보낸다
        nextDay()
        putStatus = 503
        link.syncIfDue(fixture.environment)
        link.syncIfDue(fixture.environment)
        assertEquals(4, fixture.puts().size)
    }

    @Test
    fun daysWithoutAnyRecordAreStillSentSoDeletedRecordsAreClearedOnTheServer() = withLink { fixture, link, source, _ ->
        // Health Connect 에서 기록을 모두 지웠다
        source.records = HealthRecords()
        fixture.respond = { request ->
            if (request.method == "PUT") {
                200 to """{"saved":14,"lastSyncedDay":"2026-11-08"}"""
            } else {
                200 to status(connected = true, lastSyncedDay = "2026-11-07")
            }
        }
        link.syncIfDue(fixture.environment)

        val days = fixture.puts().single().json()["days"]!!.jsonArray
        assertEquals(14, days.size)
        // 값이 없는 칸은 보내지 않는다(서버가 null 로 받는다)
        assertTrue(days.all { it.jsonObject.keys == setOf("date") })
    }

    @Test
    fun firstSyncRightAfterConnectingSendsNothingWithoutAnyRecord() = withLink { fixture, link, source, _ ->
        source.records = HealthRecords()
        fixture.respond = { request ->
            when (request.method) {
                "GET" -> 200 to status(connected = false)
                "POST" -> 200 to status(connected = true)
                else -> 500 to "{}"
            }
        }
        link.prepare(fixture.environment)
        link.beginConnect((link.step as HealthLinkStep.Consent).document)
        link.completeConnect(fixture.environment)

        assertTrue(fixture.puts().isEmpty())
        assertEquals(HealthLinkStep.Linked(justConnected = true, lastSyncedDay = null, hasPermission = true), link.step)
    }

    @Test
    fun signingOutRevokesHealthConnectPermissionsOnlyWhenSomeWereGranted() = withLink { _, link, source, _ ->
        source.granted = emptySet()
        link.revokePermissions()
        // 받은 권한이 없으면 부르지 않는다(Android 14 이상은 거두면서 앱 프로세스를 끝낸다)
        assertFalse(source.revoked)

        source.granted = setOf(HealthLink.READ_STEPS)
        link.revokePermissions()
        assertTrue(source.revoked)
    }

    @Test
    fun readFailureSendsNothingAndRetriesNextTime() = withLink { fixture, _, _, _ ->
        var attempts = 0
        val failing = object : HealthRecordSource by FakeHealthSource() {
            override suspend fun read(days: List<LocalDate>, granted: Set<String>): HealthRecords {
                attempts += 1
                throw SecurityException("앱이 뒤로 갔어요")
            }
        }
        val failingLink = HealthLink(failing, MemoryPreferences(), today = { today })
        fixture.respond = { 200 to status(connected = true) }

        failingLink.syncIfDue(fixture.environment)
        failingLink.syncIfDue(fixture.environment)
        assertTrue(fixture.requests.none { it.method == "PUT" })
        assertEquals(2, attempts)
    }

    @Test
    fun disconnectDeletesOnServerAndRevokesPermissions() = withLink { fixture, link, source, _ ->
        fixture.respond = { request ->
            if (request.method == "DELETE") 204 to "" else 200 to status(connected = true, lastSyncedDay = "2026-11-07")
        }
        link.present()
        link.prepare(fixture.environment)
        assertEquals(HealthLinkStep.Linked(justConnected = false, lastSyncedDay = "2026-11-07", hasPermission = true), link.step)

        link.disconnect(fixture.environment)
        val delete = fixture.requests.single { it.method == "DELETE" }
        assertEquals("/v1/health/link", delete.url.encodedPath)
        assertTrue(source.revoked)
        assertEquals("연결 안 됨", link.rowText("me"))
        assertFalse(link.isPresented)
    }

    @Test
    fun linkedWithoutPermissionOffersToGrantAgain() = withLink { fixture, link, source, _ ->
        source.granted = emptySet()
        fixture.respond = { 200 to status(connected = true) }
        link.prepare(fixture.environment)
        assertEquals(HealthLinkStep.Linked(justConnected = false, lastSyncedDay = null, hasPermission = false), link.step)

        // 연결돼 있어도 권한이 없으면 아무것도 읽거나 보내지 않는다
        link.syncIfDue(fixture.environment)
        assertTrue(source.reads.isEmpty())
        assertTrue(fixture.requests.none { it.method == "PUT" })
    }

    @Test
    fun guestModeNeverCallsTheServer() = withLink { fixture, link, _, _ ->
        fixture.environment.settings.isGuestMode = true
        link.syncIfDue(fixture.environment)
        link.ensureStatus(fixture.environment)
        assertTrue(fixture.paths.isEmpty())
        assertFalse(link.shouldShowHint(fixture.environment))
        assertEquals("", link.rowText("me"))
    }
}
