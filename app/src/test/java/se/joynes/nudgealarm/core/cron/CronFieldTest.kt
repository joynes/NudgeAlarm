package se.joynes.nudgealarm.core.cron

import org.junit.Assert.*
import org.junit.Test

class CronFieldTest {

    @Test
    fun parseAsteriskReturnsAny() {
        val field = CronField.parse("*", 0, 59)
        assertTrue(field is CronField.Any)
    }

    @Test
    fun anyMatchesAllValues() {
        val field = CronField.Any
        assertTrue(field.matches(0))
        assertTrue(field.matches(30))
        assertTrue(field.matches(59))
    }

    @Test
    fun parseSingleValueReturnsSingle() {
        val field = CronField.parse("30", 0, 59)
        assertTrue(field is CronField.Single)
        assertEquals(30, (field as CronField.Single).value)
    }

    @Test
    fun singleMatchesOnlyThatValue() {
        val field = CronField.Single(30)
        assertFalse(field.matches(29))
        assertTrue(field.matches(30))
        assertFalse(field.matches(31))
    }

    @Test
    fun parseRangeReturnsRange() {
        val field = CronField.parse("1-5", 0, 6)
        assertTrue(field is CronField.Range)
        val range = field as CronField.Range
        assertEquals(1, range.start)
        assertEquals(5, range.end)
    }

    @Test
    fun rangeMatchesValuesInRangeInclusive() {
        val field = CronField.Range(1, 5)
        assertFalse(field.matches(0))
        assertTrue(field.matches(1))
        assertTrue(field.matches(3))
        assertTrue(field.matches(5))
        assertFalse(field.matches(6))
    }

    @Test
    fun parseListReturnsList() {
        val field = CronField.parse("0,6", 0, 6)
        assertTrue(field is CronField.List)
        val list = field as CronField.List
        assertEquals(listOf(0, 6), list.values)
    }

    @Test
    fun listMatchesOnlyListedValues() {
        val field = CronField.List(listOf(0, 6))
        assertTrue(field.matches(0))
        assertFalse(field.matches(1))
        assertFalse(field.matches(5))
        assertTrue(field.matches(6))
    }

    @Test
    fun parseMixedListWithRangeReturnsList() {
        val field = CronField.parse("1-3,5", 0, 6)
        assertTrue(field is CronField.List)
        val list = field as CronField.List
        assertEquals(listOf(1, 2, 3, 5), list.values)
    }

    @Test
    fun mixedListMatchesCorrectly() {
        val field = CronField.List(listOf(1, 2, 3, 5))
        assertFalse(field.matches(0))
        assertTrue(field.matches(1))
        assertTrue(field.matches(2))
        assertTrue(field.matches(3))
        assertFalse(field.matches(4))
        assertTrue(field.matches(5))
        assertFalse(field.matches(6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseValueBelowMinThrows() {
        CronField.parse("-1", 0, 59)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseValueAboveMaxThrows() {
        CronField.parse("60", 0, 59)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseInvalidRangeThrows() {
        CronField.parse("5-2", 0, 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseNonNumericValueThrows() {
        CronField.parse("abc", 0, 59)
    }

    @Test
    fun nextMatchForSingleReturnsValueIfGreaterOrEqualToCurrent() {
        val field = CronField.Single(30)
        assertEquals(30, field.nextMatch(0, 59))
        assertEquals(30, field.nextMatch(30, 59))
        assertNull(field.nextMatch(31, 59))
    }

    @Test
    fun nextMatchForRangeReturnsFirstValueInRangeGreaterOrEqualToCurrent() {
        val field = CronField.Range(10, 20)
        assertEquals(10, field.nextMatch(0, 59))
        assertEquals(15, field.nextMatch(15, 59))
        assertEquals(20, field.nextMatch(20, 59))
        assertNull(field.nextMatch(21, 59))
    }

    @Test
    fun nextMatchForListReturnsFirstValueGreaterOrEqualToCurrent() {
        val field = CronField.List(listOf(5, 15, 25))
        assertEquals(5, field.nextMatch(0, 59))
        assertEquals(15, field.nextMatch(10, 59))
        assertEquals(25, field.nextMatch(25, 59))
        assertNull(field.nextMatch(26, 59))
    }

    @Test
    fun nextMatchForAnyReturnsCurrent() {
        val field = CronField.Any
        assertEquals(0, field.nextMatch(0, 59))
        assertEquals(30, field.nextMatch(30, 59))
    }
}
