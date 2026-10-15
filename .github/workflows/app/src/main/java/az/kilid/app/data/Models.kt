package az.kilid.app.data

import java.time.ZonedDateTime

/** Həftənin günləri: 1 = Bazar, 2 = Bazar ertəsi … 7 = Şənbə (iOS versiyası ilə eyni). */
data class Schedule(
    val id: String,
    val name: String,
    val weekdays: Set<Int>,
    val startMinutes: Int,
    val endMinutes: Int,
    val enabled: Boolean = true,
    val useMainSelection: Boolean = true,
    val ownPackages: Set<String> = emptySet()
) {
    val isOvernight: Boolean get() = endMinutes < startMinutes
    val isValid: Boolean get() = ScheduleMath.isValid(weekdays, startMinutes, endMinutes)
    fun packages(main: Set<String>): Set<String> = if (useMainSelection) main else ownPackages

    companion object {
        fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppState(
    val selected: Set<String> = emptySet(),
    val manualLock: Boolean = false,
    val schedules: List<Schedule> = emptyList(),
    /** paket adı → açmanın bitmə vaxtı (ms). "*" açarı bütün tətbiqləri bildirir. */
    val unlocks: Map<String, Long> = emptyMap(),
    val onboarded: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 60,
    val theme: ThemeMode = ThemeMode.SYSTEM
)

/** Təmiz vaxt riyaziyyatı (JVM unit testləri ilə yoxlanır). */
object ScheduleMath {
    fun isValid(weekdays: Set<Int>, start: Int, end: Int): Boolean =
        weekdays.isNotEmpty() &&
            weekdays.all { it in 1..7 } &&
            start in 0..1439 &&
            end in 0..1439 &&
            start != end

    fun weekdayOf(t: ZonedDateTime): Int = (t.dayOfWeek.value % 7) + 1

    /** `weekdays` intervalın BAŞLADIĞI günlərdir. Gecə yarısını keçən interval ertəsi günə davam edir. */
    fun isActive(weekdays: Set<Int>, start: Int, end: Int, at: ZonedDateTime): Boolean {
        if (!isValid(weekdays, start, end)) return false
        val wd = weekdayOf(at)
        val minutes = at.hour * 60 + at.minute
        if (start < end) {
            return wd in weekdays && minutes >= start && minutes < end
        }
        val previous = if (wd == 1) 7 else wd - 1
        return (wd in weekdays && minutes >= start) || (previous in weekdays && minutes < end)
    }

    data class Transition(val at: ZonedDateTime, val isStart: Boolean)

    fun nextTransition(weekdays: Set<Int>, start: Int, end: Int, after: ZonedDateTime): Transition? {
        if (!isValid(weekdays, start, end)) return null
        var best: Transition? = null

        fun consider(candidate: ZonedDateTime, isStart: Boolean) {
            val current = best
            if (candidate.isAfter(after) && (current == null || candidate.isBefore(current.at))) {
                best = Transition(candidate, isStart)
            }
        }

        val firstDay = after.toLocalDate()
        for (offset in 0L..8L) {
            val day = firstDay.plusDays(offset)
            val wd = (day.dayOfWeek.value % 7) + 1
            if (wd !in weekdays) continue
            consider(day.atStartOfDay(after.zone).plusMinutes(start.toLong()), true)
            val endDay = if (end < start) day.plusDays(1) else day
            consider(endDay.atStartOfDay(after.zone).plusMinutes(end.toLong()), false)
        }
        return best
    }
}
