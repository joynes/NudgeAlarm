package se.joynes.nudgealarm.database

import org.junit.Assert.*
import org.junit.Test

class NagStatusTest {

    @Test
    fun allStatusEntriesExist() {
        val statuses = NagStatus.entries
        assertEquals(5, statuses.size)
    }

    @Test
    fun activeStatusExists() {
        assertEquals("ACTIVE", NagStatus.ACTIVE.name)
    }

    @Test
    fun snoozedStatusExists() {
        assertEquals("SNOOZED", NagStatus.SNOOZED.name)
    }

    @Test
    fun completedStatusExists() {
        assertEquals("COMPLETED", NagStatus.COMPLETED.name)
    }

    @Test
    fun expiredStatusExists() {
        assertEquals("EXPIRED", NagStatus.EXPIRED.name)
    }

    @Test
    fun cancelledStatusExists() {
        assertEquals("CANCELLED", NagStatus.CANCELLED.name)
    }

    @Test
    fun statusOrdinalOrder() {
        assertEquals(0, NagStatus.ACTIVE.ordinal)
        assertEquals(1, NagStatus.SNOOZED.ordinal)
        assertEquals(2, NagStatus.COMPLETED.ordinal)
        assertEquals(3, NagStatus.EXPIRED.ordinal)
        assertEquals(4, NagStatus.CANCELLED.ordinal)
    }

    @Test
    fun valueOfReturnsCorrectStatus() {
        assertEquals(NagStatus.ACTIVE, NagStatus.valueOf("ACTIVE"))
        assertEquals(NagStatus.SNOOZED, NagStatus.valueOf("SNOOZED"))
        assertEquals(NagStatus.COMPLETED, NagStatus.valueOf("COMPLETED"))
        assertEquals(NagStatus.EXPIRED, NagStatus.valueOf("EXPIRED"))
        assertEquals(NagStatus.CANCELLED, NagStatus.valueOf("CANCELLED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun valueOfInvalidNameThrows() {
        NagStatus.valueOf("INVALID")
    }
}
