package az.kilid.app

import az.kilid.app.data.AppState
import az.kilid.app.data.Policy
import az.kilid.app.data.Schedule
import az.kilid.app.data.ScheduleMath
import az.kilid.app.security.PinCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleMathTest {
    private val utc = ZoneId.of("UTC")

    /** 2026-10-12 = Bazar ertəsi (weekday 2) */
    private fun t(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, utc)

    @Test
    fun weekdayMapping() {
        assertEquals(2, ScheduleMath.weekdayOf(t(12, 10)))
        assertEquals(1, ScheduleMath.weekdayOf(t(11, 10)))
        assertEquals(7, ScheduleMath.weekdayOf(t(17, 10)))
    }

    @Test
    fun sameDayWindow() {
        val wd = setOf(2)
        assertTrue(ScheduleMath.isActive(wd, 9 * 60, 17 * 60, t(12, 10)))
        assertFalse(ScheduleMath.isActive(wd, 9 * 60, 17 * 60, t(12, 17)))
        assertFalse(ScheduleMath.isActive(wd, 9 * 60, 17 * 60, t(13, 10)))
    }

    @Test
    fun overnightWindowSpillsIntoNextDay() {
        val wd = setOf(2)
        assertTrue(ScheduleMath.isActive(wd, 22 * 60, 6 * 60, t(12, 23)))
        assertTrue(ScheduleMath.isActive(wd, 22 * 60, 6 * 60, t(13, 5, 59)))
        assertFalse(ScheduleMath.isActive(wd, 22 * 60, 6 * 60, t(13, 6)))
        assertFalse(ScheduleMath.isActive(wd, 22 * 60, 6 * 60, t(12, 5)))
    }

    @Test
    fun sundayToMondayWrap() {
        assertTrue(ScheduleMath.isActive(setOf(1), 22 * 60, 6 * 60, t(12, 2)))
    }

    @Test
    fun invalidWindows() {
        assertFalse(ScheduleMath.isValid(emptySet(), 60, 120))
        assertFalse(ScheduleMath.isValid(setOf(2), 60, 60))
        assertFalse(ScheduleMath.isValid(setOf(8), 60, 120))
        assertFalse(ScheduleMath.isValid(setOf(2), 60, 1440))
    }

    @Test
    fun nextTransition() {
        val next = ScheduleMath.nextTransition(setOf(2), 22 * 60, 6 * 60, t(12, 23))
        assertEquals(t(13, 6), next?.at)
        assertEquals(false, next?.isStart)
        assertNull(ScheduleMath.nextTransition(emptySet(), 60, 120, t(12, 1)))
    }
}

class PolicyTest {
    private val utc = ZoneId.of("UTC")
    private fun t(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, utc)
    private val all = (1..7).toSet()

    @Test
    fun manualLockLocksSelectedOnly() {
        val s = AppState(selected = setOf("a", "b"), manualLock = true)
        assertTrue(Policy.isLocked("a", s, t(12, 10), 0))
        assertFalse(Policy.isLocked("c", s, t(12, 10), 0))
    }

    @Test
    fun noRestrictionWhenNothingActive() {
        val s = AppState(selected = setOf("a"))
        assertFalse(Policy.isLocked("a", s, t(12, 10), 0))
    }

    @Test
    fun endingOneScheduleKeepsOverlappingOne() {
        // A: 22:00–06:00 (a), B: 01:00–03:00 (b). 04:00-də yalnız A aktivdir, "a" hələ bloklanıb.
        val a = Schedule("A", "A", all, 22 * 60, 6 * 60, useMainSelection = false, ownPackages = setOf("a"))
        val b = Schedule("B", "B", all, 1 * 60, 3 * 60, useMainSelection = false, ownPackages = setOf("b"))
        val s = AppState(schedules = listOf(a, b))
        assertTrue(Policy.isLocked("a", s, t(13, 2), 0))
        assertTrue(Policy.isLocked("b", s, t(13, 2), 0))
        assertTrue(Policy.isLocked("a", s, t(13, 4), 0))
        assertFalse(Policy.isLocked("b", s, t(13, 4), 0))
    }

    @Test
    fun scheduleEndDoesNotClearManualLock() {
        val a = Schedule("A", "A", all, 22 * 60, 6 * 60)
        val s = AppState(selected = setOf("a"), manualLock = true, schedules = listOf(a))
        assertTrue(Policy.isLocked("a", s, t(13, 12), 0)) // cədvəl bitib, əl ilə kilid qalır
    }

    @Test
    fun temporaryUnlockExpires() {
        val s = AppState(selected = setOf("a"), manualLock = true, unlocks = mapOf("a" to 1_000L))
        assertFalse(Policy.isLocked("a", s, t(12, 10), 500))   // açıq
        assertTrue(Policy.isLocked("a", s, t(12, 10), 1_000))  // müddət bitib → yenidən bloklanır
    }

    @Test
    fun unlockAllOpensEverything() {
        val s = AppState(selected = setOf("a", "b"), manualLock = true, unlocks = mapOf("*" to 5_000L))
        assertFalse(Policy.isLocked("a", s, t(12, 10), 100))
        assertFalse(Policy.isLocked("b", s, t(12, 10), 100))
        assertTrue(Policy.isLocked("b", s, t(12, 10), 6_000))
    }

    @Test
    fun unlockOfOneAppKeepsOthersLocked() {
        val s = AppState(selected = setOf("a", "b"), manualLock = true, unlocks = mapOf("a" to 5_000L))
        assertFalse(Policy.isLocked("a", s, t(12, 10), 100))
        assertTrue(Policy.isLocked("b", s, t(12, 10), 100))
    }
}

class PinCryptoTest {
    @Test
    fun deriveIsDeterministicAndSaltDependent() {
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }
        val a = PinCrypto.derive("123456", salt1, 1_000)
        val b = PinCrypto.derive("123456", salt1, 1_000)
        val c = PinCrypto.derive("123456", salt2, 1_000)
        val d = PinCrypto.derive("654321", salt1, 1_000)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
        assertFalse(a.contentEquals(d))
        assertEquals(32, a.size)
    }

    @Test
    fun constantTimeEquals() {
        assertTrue(PinCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(PinCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(PinCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun lockoutDelays() {
        assertNull(PinCrypto.delayMillis(4))
        assertEquals(30_000L, PinCrypto.delayMillis(5))
        assertEquals(60_000L, PinCrypto.delayMillis(6))
        assertEquals(300_000L, PinCrypto.delayMillis(7))
        assertEquals(900_000L, PinCrypto.delayMillis(12))
        assertNotEquals(PinCrypto.delayMillis(5), PinCrypto.delayMillis(6))
    }
}
