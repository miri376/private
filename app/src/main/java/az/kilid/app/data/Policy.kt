package az.kilid.app.data

import java.time.ZonedDateTime

data class NextChange(val scheduleName: String, val at: ZonedDateTime, val isStart: Boolean)

data class RestrictionStatus(
    val isRestricting: Boolean,
    val restrictedCount: Int,
    val activeScheduleNames: List<String>,
    val allUnlockedUntil: Long?,
    val latestUnlockUntil: Long?,
    val nextChange: NextChange?
)

/**
 * Vahid məhdudiyyət siyasəti. PRİORİTETLƏR (yuxarı üstündür):
 *  1. Müvəqqəti açma ("*" və ya konkret paket) müddət bitənədək həmin tətbiqi bütün mənbələrdən azad edir.
 *  2. Məhdudlaşdırılan toplu = (əl ilə kilid seçimi) ∪ (hazırda aktiv bütün cədvəllərin seçimləri).
 * Hər yoxlamada saat əsasında sıfırdan hesablanır, buna görə bir cədvəlin bitməsi başqa
 * aktiv cədvəlin və ya əl ilə kilidin məhdudiyyətini silə bilməz.
 * Əsas icazələr (istifadə statistikası + üstündə göstərmə) yoxdursa tətbiq edilə bilmir; bu, UI-da açıq göstərilir.
 */
object Policy {
    fun restrictedPackages(state: AppState, now: ZonedDateTime): Set<String> {
        val result = mutableSetOf<String>()
        if (state.manualLock) result += state.selected
        for (schedule in state.schedules) {
            if (schedule.enabled && schedule.isValid &&
                ScheduleMath.isActive(schedule.weekdays, schedule.startMinutes, schedule.endMinutes, now)
            ) {
                result += schedule.packages(state.selected)
            }
        }
        return result
    }

    fun isLocked(pkg: String, state: AppState, now: ZonedDateTime, nowMs: Long): Boolean {
        if (pkg !in restrictedPackages(state, now)) return false
        if ((state.unlocks["*"] ?: 0L) > nowMs) return false
        if ((state.unlocks[pkg] ?: 0L) > nowMs) return false
        return true
    }

    fun status(state: AppState, now: ZonedDateTime, nowMs: Long): RestrictionStatus {
        val usable = state.schedules.filter { it.enabled && it.isValid }
        val active = usable.filter {
            ScheduleMath.isActive(it.weekdays, it.startMinutes, it.endMinutes, now)
        }
        val restricted = restrictedPackages(state, now)
        val allUnlockedUntil = state.unlocks["*"]?.takeIf { it > nowMs }
        val stillLocked = if (allUnlockedUntil != null) 0 else restricted.count { (state.unlocks[it] ?: 0L) <= nowMs }
        val next = usable.mapNotNull { s ->
            ScheduleMath.nextTransition(s.weekdays, s.startMinutes, s.endMinutes, now)
                ?.let { NextChange(s.name, it.at, it.isStart) }
        }.minByOrNull { it.at.toInstant() }
        return RestrictionStatus(
            isRestricting = stillLocked > 0,
            restrictedCount = stillLocked,
            activeScheduleNames = active.map { it.name },
            allUnlockedUntil = allUnlockedUntil,
            latestUnlockUntil = state.unlocks.values.filter { it > nowMs }.maxOrNull(),
            nextChange = next
        )
    }
}
