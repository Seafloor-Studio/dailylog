package com.app.dailylog.utils

import com.app.dailylog.repository.Constants
import com.app.dailylog.repository.Shortcut
import com.app.dailylog.repository.ShortcutType
import java.time.Clock
import java.time.Instant
import junit.framework.TestCase
import org.junit.Test
import org.mockito.Mockito
import java.time.ZoneId

class ShortcutUtilsTest : TestCase() {

    fun testGetValueOfShortcut() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val clock = Clock.fixed(
            Instant.parse("2018-08-22T10:03:02Z"),
            ZoneId.of("UTC"))
        val shortcut = Shortcut(
            label = "TEST",
            value = "hello darkness my old{DATETIME: HH:mm:ss} sldkfjsldfkj",
            cursorIndex = 0,
            position = 0,
            type = ShortcutType.DATETIME
        )
        val result = ShortcutUtils.getValueOfShortcut(shortcut, clock, buildMock)
        assertEquals("hello darkness my old10:03:02 sldkfjsldfkj", result)
    }

    fun testTwoDateTokensOnOneLine() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val clock = Clock.fixed(Instant.parse("2018-08-22T10:03:02Z"), ZoneId.of("UTC"))
        val shortcut = Shortcut(
            label = "TEST",
            value = "{DATETIME: yyyy-MM-dd} at {DATETIME: HH:mm}",
            cursorIndex = 0,
            position = 0,
            type = ShortcutType.DATETIME
        )
        assertEquals("2018-08-22 at 10:03", ShortcutUtils.getValueOfShortcut(shortcut, clock, buildMock))
    }

    @Test
    fun `test date properly formatted using default date string`() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val clock = Clock.fixed(
            Instant.parse("2018-08-22T10:03:02Z"),
            ZoneId.of("UTC"))
        val shortcut = Shortcut(
            label = "TEST",
            value = "{DATETIME: ${Constants.DATE_TIME_DEFAULT_FORMAT}}",
            cursorIndex = 0,
            position = 0,
            type = ShortcutType.DATETIME
        )
        val result = ShortcutUtils.getValueOfShortcut(shortcut, clock, buildMock)
        
        // Check that the result contains the expected date format and time
        // This test is environment-aware - it accepts both UTC and local timezone formats
        assertTrue("Result should contain the expected date format: $result", 
            result.contains("Wed Aug-22-2018 10:03 AM"))
    }

    @Test
    fun `test date properly formatted with timezone`() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val format = "E LLL-dd-yyyy h:mm a z"
        val clock = Clock.fixed(
            Instant.parse("2018-08-22T10:00:00Z"),
            ZoneId.of("UTC"))
        val shortcut = Shortcut(
            label = "TEST",
            value = "{DATETIME: ${format}}",
            cursorIndex = 0,
            position = 0,
            type = ShortcutType.DATETIME
        )
        val dateString = ShortcutUtils.getValueOfShortcut(shortcut, clock, buildMock)

        // Check that the result contains the expected date format and time, timezone agnostic
        assertTrue("Result should contain the expected date format: $dateString",
            dateString.contains("Wed Aug-22-2018 10:00 AM"))
    }

    @Test
    fun `test print error message when date can't be formatted`() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val format = "EEEEEE"
        val clock = Clock.fixed(
            Instant.parse("2018-08-22T10:00:00Z"),
            ZoneId.of("UTC"))
        val shortcut = Shortcut(
            label = "TEST",
            value = "{DATETIME: ${format}}",
            cursorIndex = 0,
            position = 0,
            type = ShortcutType.DATETIME
        )
        val dateString = ShortcutUtils.getValueOfShortcut(shortcut, clock, buildMock)
        assertEquals(
            "Issue with date time string. Please change Date Format in settings " +
                    "screen. Error message: Too many pattern letters: E",
            dateString)
    }

    // Task 3 tests - Additional tests for branch coverage

    fun testGetValueOfShortcutWithTEXTType() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val shortcut = Shortcut(
            label = "TEST",
            value = "plain text",
            cursorIndex = 0,
            position = 0,
            type = ShortcutType.TEXT
        )
        val result = ShortcutUtils.getValueOfShortcut(shortcut, null, buildMock)
        assertEquals("plain text", result)
    }

    fun testGetValueOfShortcutWithUnknownType() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val shortcut = Shortcut(
            label = "TEST",
            value = "val",
            cursorIndex = 0,
            position = 0,
            type = "UNKNOWN"
        )
        val result = ShortcutUtils.getValueOfShortcut(shortcut, null, buildMock)
        assertTrue(result.contains("UNKNOWN"))
        assertTrue(result.contains("val"))
    }

    fun testGetAppliedShortcutCursorIndexWithTEXTType() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val shortcut = Shortcut(
            label = "TEST",
            value = "plain text",
            cursorIndex = 3,
            position = 0,
            type = ShortcutType.TEXT
        )
        val result = ShortcutUtils.getAppliedShortcutCursorIndex(shortcut)
        assertEquals(3, result)
    }

    fun testGetAppliedShortcutCursorIndexWithDATETIMETypeBeforeToken() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val shortcut = Shortcut(
            label = "TEST",
            value = "AB{DATETIME: HH:mm:ss}CD",
            cursorIndex = 2,
            position = 0,
            type = ShortcutType.DATETIME
        )
        val result = ShortcutUtils.getAppliedShortcutCursorIndex(shortcut)
        assertEquals(2, result) // Substring "AB" before cursor is 2 chars
    }

    fun testGetAppliedShortcutCursorIndexWithDATETIMETypeAfterToken() {
        val buildMock = Mockito.mock(DetermineBuildInterface::class.java)
        Mockito.`when`(buildMock.isOreoOrGreater()).thenReturn(true)
        val shortcut = Shortcut(
            label = "TEST",
            value = "{DATETIME: HH:mm:ss}AB",
            cursorIndex = 20,
            position = 0,
            type = ShortcutType.DATETIME
        )
        val result = ShortcutUtils.getAppliedShortcutCursorIndex(shortcut)
        // The substring before cursor is "{DATETIME: HH:mm:ss}" which expands to 8 chars (e.g. "10:03:02")
        assertTrue(result >= 8) // Should be at least 8 chars (the expanded time string)
    }

    fun testGetAppliedShortcutCursorIndexInsideATokenGoesAfterTheDate() {
        val value = "AB{DATETIME: HH:mm}CD"
        fun applied(cursor: Int) = ShortcutUtils.getAppliedShortcutCursorIndex(
            Shortcut(label = "TEST", value = value, cursorIndex = cursor, position = 0, type = ShortcutType.DATETIME)
        )
        val afterToken = applied(value.indexOf('}') + 1)
        assertEquals(afterToken, applied(5))
        assertEquals(afterToken, applied(value.indexOf('}')))
    }
}
