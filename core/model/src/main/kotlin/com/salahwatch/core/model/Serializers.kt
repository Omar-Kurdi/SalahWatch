package com.salahwatch.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * `java.time` serializers.
 *
 * [Instant] is encoded as epoch milliseconds rather than ISO text: this payload is what crosses the
 * Data Layer to the watch, where the 100 KB DataItem limit makes compactness worth the small loss of
 * readability. Millisecond precision is far finer than the one-minute resolution of prayer times.
 */
internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant): Unit =
        encoder.encodeLong(value.toEpochMilli())

    override fun deserialize(decoder: Decoder): Instant =
        Instant.ofEpochMilli(decoder.decodeLong())
}

internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate): Unit =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

internal object ZoneIdSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.ZoneId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ZoneId): Unit =
        encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder): ZoneId =
        ZoneId.of(decoder.decodeString())
}
