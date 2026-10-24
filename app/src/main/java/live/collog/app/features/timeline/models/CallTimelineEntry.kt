package live.collog.app.features.timeline.models

import java.util.UUID
import kotlin.math.abs
import live.collog.app.shared.components.chart.KeywordMark
import live.collog.app.shared.components.chart.KeywordTone
import live.collog.app.shared.components.chart.RangeGauge
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.components.stat.StatNote
import live.collog.app.shared.services.api.ApiFormat

/** iOS `CallTimelineEntry`: 타임라인에 놓이는 통화 하나. */
data class CallTimelineEntry(
    val dateText: String,
    val durationText: String,
    val summaryStats: List<CallStat>,
    val story: String,
    val keywords: List<KeywordMark>,
    val gauges: List<RangeGauge>,
    val counts: List<CallStat>,
) {
    val id: UUID = UUID.randomUUID()

    companion object {
        val sample: CallTimelineEntry by lazy {
            CallTimelineEntry(
                dateText = "8월 14일 금요일",
                durationText = "14분 27초",
                summaryStats = CallStat.samples,
                story = "수면이 얕고 자주 깬다고 하셨으며\n" + "혈압약은 꾸준히 복용 중이라고 하셨어요.",
                keywords = listOf(
                    KeywordMark(0.08, KeywordTone.Positive, null),
                    KeywordMark(0.22, KeywordTone.Positive, "수면"),
                    KeywordMark(0.34, KeywordTone.Caution, null),
                    KeywordMark(0.41, KeywordTone.Concern, "허리 통증"),
                    KeywordMark(0.58, KeywordTone.Positive, null),
                    KeywordMark(0.66, KeywordTone.Neutral, "혈압약"),
                    KeywordMark(0.84, KeywordTone.Neutral, null),
                ),
                gauges = listOf(
                    RangeGauge("말 사이 쉼", 0.1..0.6, 0.72, "평소보다 길어짐"),
                    RangeGauge("목소리 높낮이", 0.15..0.7, 0.45, "평소 범위 안"),
                ),
                counts = listOf(
                    CallStat("기침", "4", "회", StatNote("평소 2회")),
                    CallStat("되물으심", "2", "회", StatNote("평소 1회")),
                    CallStat("웃음", "6", "회", StatNote("평소 6회")),
                ),
            )
        }

        /** iOS `sample(offset:relation:)`: 체험 모드에서 주마다 조금씩 다르게 보이는 견본. */
        fun sample(offset: Int, relation: String? = null): CallTimelineEntry {
            val distance = abs(offset)
            val variant = distance % 4
            val bounds = TimelineWeekPage.bounds(offset)
            val date = TimelineWeekPage.instant(bounds.start.plusDays(1L + variant))
            val isFather = relation == "FATHER"
            val duration = (if (isFather) 11 else 14) + (distance * 2) % 5
            val speechRate = (if (isFather) 194 else 210) + (distance * 3) % 9 - 4

            return CallTimelineEntry(
                dateText = ApiFormat.longDate(date),
                durationText = "${duration}분 ${8 + variant * 11}초",
                summaryStats = listOf(
                    CallStat(
                        "통화 길이", "$duration", "분",
                        StatNote(if (isFather) "평소 10분" else "평소 12분"),
                    ),
                    CallStat(
                        "말씀 속도", "$speechRate", "음절/분",
                        StatNote(if (isFather) "평소 190" else "평소 208"),
                    ),
                ),
                story = sampleStory(relation, variant),
                keywords = sampleKeywords(isFather, variant),
                gauges = sampleGauges(isFather, variant),
                counts = sampleCounts(isFather, distance),
            )
        }

        private fun sampleStory(relation: String?, variant: Int): String {
            val motherStories = listOf(
                "수면이 얕고 자주 깬다고 하셨으며\n혈압약은 꾸준히 복용 중이라고 하셨어요.",
                "오전에 장을 보고 오셨고\n저녁에는 일찍 쉬실 예정이라고 하셨어요.",
                "점심 식사를 맛있게 드셨고\n주말에 가까운 공원에 다녀오셨어요.",
                "어젯밤 편하게 주무셨고\n오늘은 집에서 쉬고 계셨어요.",
            )
            val fatherStories = listOf(
                "아침 산책을 다녀오셨고\n점심으로 국수를 드셨다고 하셨어요.",
                "친구와 바둑을 두셨고\n저녁 약도 챙겨 드셨다고 하셨어요.",
                "오전에 병원에 다녀오셨고\n검사 결과는 괜찮다고 하셨어요.",
                "주말에 텃밭을 살펴보셨고\n평소보다 일찍 주무셨다고 하셨어요.",
            )
            return if (relation == "FATHER") fatherStories[variant] else motherStories[variant]
        }

        private fun sampleKeywords(isFather: Boolean, variant: Int): List<KeywordMark> {
            val motherKeywords = listOf(
                listOf(
                    KeywordMark(0.12, KeywordTone.Positive, null),
                    KeywordMark(0.24, KeywordTone.Positive, "수면"),
                    KeywordMark(0.42, KeywordTone.Concern, "허리 통증"),
                    KeywordMark(0.68, KeywordTone.Neutral, "혈압약"),
                    KeywordMark(0.86, KeywordTone.Neutral, null),
                ),
                listOf(
                    KeywordMark(0.09, KeywordTone.Neutral, null),
                    KeywordMark(0.27, KeywordTone.Positive, "장보기"),
                    KeywordMark(0.49, KeywordTone.Caution, "피로"),
                    KeywordMark(0.73, KeywordTone.Positive, "휴식"),
                ),
                listOf(
                    KeywordMark(0.14, KeywordTone.Positive, "점심"),
                    KeywordMark(0.36, KeywordTone.Neutral, null),
                    KeywordMark(0.58, KeywordTone.Positive, "공원"),
                    KeywordMark(0.81, KeywordTone.Positive, null),
                ),
                listOf(
                    KeywordMark(0.1, KeywordTone.Positive, null),
                    KeywordMark(0.31, KeywordTone.Positive, "숙면"),
                    KeywordMark(0.56, KeywordTone.Neutral, "휴식"),
                    KeywordMark(0.79, KeywordTone.Caution, "입맛"),
                ),
            )
            val fatherKeywords = listOf(
                listOf(
                    KeywordMark(0.11, KeywordTone.Positive, null),
                    KeywordMark(0.29, KeywordTone.Positive, "산책"),
                    KeywordMark(0.55, KeywordTone.Neutral, "점심"),
                    KeywordMark(0.82, KeywordTone.Positive, null),
                ),
                listOf(
                    KeywordMark(0.08, KeywordTone.Neutral, null),
                    KeywordMark(0.3, KeywordTone.Positive, "바둑"),
                    KeywordMark(0.57, KeywordTone.Neutral, "저녁 약"),
                    KeywordMark(0.78, KeywordTone.Positive, null),
                ),
                listOf(
                    KeywordMark(0.13, KeywordTone.Neutral, null),
                    KeywordMark(0.34, KeywordTone.Caution, "병원"),
                    KeywordMark(0.61, KeywordTone.Neutral, "검사"),
                    KeywordMark(0.85, KeywordTone.Positive, null),
                ),
                listOf(
                    KeywordMark(0.16, KeywordTone.Positive, "텃밭"),
                    KeywordMark(0.39, KeywordTone.Neutral, null),
                    KeywordMark(0.64, KeywordTone.Positive, "수면"),
                    KeywordMark(0.87, KeywordTone.Positive, null),
                ),
            )
            return if (isFather) fatherKeywords[variant] else motherKeywords[variant]
        }

        private fun sampleGauges(isFather: Boolean, variant: Int): List<RangeGauge> {
            val pauseRanges = if (isFather) {
                listOf(0.15..0.72, 0.13..0.69, 0.16..0.74, 0.14..0.7)
            } else {
                listOf(0.1..0.6, 0.12..0.63, 0.09..0.58, 0.13..0.62)
            }
            val pitchRanges = if (isFather) {
                listOf(0.18..0.76, 0.2..0.73, 0.17..0.71, 0.19..0.75)
            } else {
                listOf(0.15..0.7, 0.18..0.72, 0.16..0.69, 0.2..0.74)
            }
            val pauseMarkers = if (isFather) listOf(0.48, 0.63, 0.41, 0.76) else listOf(0.72, 0.56, 0.43, 0.68)
            val pitchMarkers = if (isFather) listOf(0.53, 0.46, 0.69, 0.38) else listOf(0.45, 0.57, 0.74, 0.51)
            val pauseRange = pauseRanges[variant]
            val pitchRange = pitchRanges[variant]
            val pauseMarker = pauseMarkers[variant]
            val pitchMarker = pitchMarkers[variant]

            return listOf(
                RangeGauge("말 사이 쉼", pauseRange, pauseMarker, gaugeCaption(pauseRange, pauseMarker, "평소보다 길어짐")),
                RangeGauge(
                    "목소리 높낮이", pitchRange, pitchMarker,
                    gaugeCaption(pitchRange, pitchMarker, "평소보다 변화가 큼"),
                ),
            )
        }

        private fun gaugeCaption(range: ClosedFloatingPointRange<Double>, marker: Double, highText: String): String {
            if (marker in range) return "평소 범위 안"
            if (marker > range.endInclusive) return highText
            return "평소보다 낮음"
        }

        private fun sampleCounts(isFather: Boolean, distance: Int): List<CallStat> {
            val cough = (if (isFather) 1 else 4) + distance % 3
            val repeatCount = (if (isFather) 1 else 2) + distance % 2
            val laugh = (if (isFather) 8 else 6) - distance % 3
            return listOf(
                CallStat("기침", "$cough", "회", StatNote("평소 2회")),
                CallStat("되물으심", "$repeatCount", "회", StatNote("평소 1회")),
                CallStat("웃음", "$laugh", "회", StatNote("평소 6회")),
            )
        }
    }
}
