package com.salahwatch.core.calculation

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.IqamaMode
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.MosqueIqamaOffsets
import com.salahwatch.core.model.MosqueIqamaTimes
import com.salahwatch.core.model.PrayerTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Derives Iqama times from Adhan times.
 *
 * Adhan times are astronomical; Iqama times are a human decision made by a mosque, so they can only
 * ever be configuration. Two mechanisms ship: the user's own fixed offsets, and whatever the chosen
 * mosque publishes — which may itself be offsets *or* wall-clock times. The remaining
 * [IqamaMode] values exist in the type system so that adding them later is a new branch here rather
 * than a redesign.
 */
public object IqamaCalculator {

    /**
     * Returns [schedule] with Iqama times filled in according to [settings].
     *
     * @param mosqueOffsets the chosen mosque's own offsets, used when the mode asks for them.
     * @param mosqueTimes the chosen mosque's own wall-clock times, which take precedence over
     *   [mosqueOffsets] because they say when the congregation starts rather than how long after
     *   the Adhan it does.
     *
     * Both are null when no mosque is selected or it publishes neither, in which case the user's
     * fixed offsets are used instead — a mode that silently produced no Iqama would look like a bug.
     */
    public fun apply(
        schedule: DaySchedule,
        settings: IqamaSettings,
        mosqueOffsets: MosqueIqamaOffsets? = null,
        mosqueTimes: MosqueIqamaTimes? = null,
    ): DaySchedule = schedule.copy(
        times = schedule.times.map {
            it.withIqama(settings, mosqueOffsets, mosqueTimes, schedule.date, schedule.zone)
        },
    )

    private fun PrayerTime.withIqama(
        settings: IqamaSettings,
        mosqueOffsets: MosqueIqamaOffsets?,
        mosqueTimes: MosqueIqamaTimes?,
        date: LocalDate,
        zone: ZoneId,
    ): PrayerTime = when (settings.mode) {
        IqamaMode.FIXED_OFFSET -> withOffset(settings.offsetFor(prayer))

        IqamaMode.FROM_MOSQUE ->
            mosqueTimes?.instantFor(this, date, zone)?.let { copy(iqama = it) }
                ?: withOffset(mosqueOffsets?.offsetFor(prayer) ?: settings.offsetFor(prayer))

        // Not yet implemented — deliberately produces no Iqama rather than inventing one.
        // Showing a wrong congregation time would be worse than showing none.
        IqamaMode.EXPLICIT_TIMES, IqamaMode.MOSQUE_PROFILE -> copy(iqama = null)
    }

    private fun PrayerTime.withOffset(minutes: Int?): PrayerTime =
        copy(iqama = minutes?.let { adhan.plus(Duration.ofMinutes(it.toLong())) })

    /**
     * A published `HH:mm` resolved against the day the prayer falls on.
     *
     * Returns null on anything unparseable rather than throwing: a mosque's published data is
     * user-entered, and one malformed field should cost that prayer its Iqama, not the whole day
     * its schedule.
     *
     * A congregation timed before its own Adhan is read as belonging to the next day, which is what
     * an Isha Iqama published as `00:15` means. That rule is also how bad data gets in: a Dhuhr
     * Iqama mistyped as `12:00` against a 12:25 Adhan becomes a congregation twenty-three hours
     * away, which passes every type check and is nonsense. So the result has to land within
     * [MAX_IQAMA_DELAY] of the Adhan or it is discarded, and the user's own offset is used instead.
     */
    private fun MosqueIqamaTimes.instantFor(
        time: PrayerTime,
        date: LocalDate,
        zone: ZoneId,
    ): Instant? {
        val published = timeFor(time.prayer) ?: return null
        val parsed = try {
            LocalTime.parse(published)
        } catch (_: DateTimeParseException) {
            return null
        }

        val sameDay = parsed.atDate(date).atZone(zone).toInstant()
        val resolved = if (sameDay < time.adhan) {
            parsed.atDate(date.plusDays(1)).atZone(zone).toInstant()
        } else {
            sameDay
        }

        return resolved.takeIf { Duration.between(time.adhan, it) <= MAX_IQAMA_DELAY }
    }

    /**
     * The longest a congregation can plausibly follow its own Adhan.
     *
     * Two hours is far beyond any real Iqama offset — the widest this app ships as a default is 25
     * minutes — so nothing legitimate is rejected, while a mistyped time that the next-day rule
     * would otherwise turn into a day-long wait is.
     */
    private val MAX_IQAMA_DELAY: Duration = Duration.ofHours(2)
}
