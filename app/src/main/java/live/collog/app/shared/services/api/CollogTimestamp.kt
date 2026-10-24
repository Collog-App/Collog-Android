package live.collog.app.shared.services.api

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * iOS `Date.fromCollogTimestamp`: 서버 시각에 시간대가 없으면 UTC(`Z`)로 본다.
 * 소수 초는 있어도 없어도 된다.
 */
object CollogTimestamp {
    fun parse(raw: String): Instant? {
        val normalized = if (hasTimeZone(raw)) raw else raw + "Z"
        return try {
            OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun format(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    private fun hasTimeZone(raw: String): Boolean {
        val timeStart = raw.indexOf('T')
        if (timeStart < 0) return false
        val time = raw.substring(timeStart)
        return time.endsWith("Z") || time.contains('+') || time.drop(1).contains('-')
    }
}

object CollogInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CollogInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return CollogTimestamp.parse(raw) ?: throw ApiError.Decoding(raw)
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(CollogTimestamp.format(value))
    }
}
