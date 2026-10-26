package com.app.dailylog.utils

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.app.dailylog.repository.Shortcut
import com.app.dailylog.repository.ShortcutType
import java.time.Clock

object ShortcutUtils {
    @RequiresApi(Build.VERSION_CODES.O)
    fun matchResultToDateString(clock: Clock?, matchResult: MatchResult): CharSequence {
        val format = matchResult.groupValues[1].trim()
        return getDateString(clock, format)
    }

    @SuppressLint("NewApi")
    private fun replaceDateTimePatterns(value: String, clock: Clock? = null,
                                        determineBuild: DetermineBuildInterface = DetermineBuild): String {
        return value.replace(FileNameTemplate.DATETIME_TOKEN,
            transform = { matchResult: MatchResult ->
                if (determineBuild.isOreoOrGreater()) {
                    matchResultToDateString(clock, matchResult)
                } else {
                    "DATETIME shortcuts only supported in SDKs O and higher.\n$value"
                }
            })
    }

    fun getValueOfShortcut(shortcut: Shortcut, clock: Clock? = null,
                           determineBuild: DetermineBuildInterface = DetermineBuild): String {
        val rawValue = shortcut.value

        when (shortcut.type) {
            ShortcutType.TEXT -> return rawValue
            ShortcutType.DATETIME -> return replaceDateTimePatterns(rawValue, clock, determineBuild)
        }
        return "type ${shortcut.type} not found. Value: $rawValue"
    }

    fun getAppliedShortcutCursorIndex(shortcut: Shortcut): Int {
        val defaultIndex = shortcut.cursorIndex
        when (shortcut.type) {
            ShortcutType.TEXT -> return defaultIndex
            ShortcutType.DATETIME -> {
                // A cursor inside a {DATETIME: …} token has no place in the date it becomes, and
                // counting the token's raw characters could put it past the inserted text.
                val index = FileNameTemplate.DATETIME_TOKEN.findAll(shortcut.value)
                    .firstOrNull { defaultIndex > it.range.first && defaultIndex <= it.range.last }
                    ?.let { it.range.last + 1 } ?: defaultIndex
                // Take the section the cursor is supposed to come after and calculate the length
                // of that once the DateTime patterns have been applies.
                return replaceDateTimePatterns(shortcut.value.slice(0 until index)).length
            }
        }
        return defaultIndex
    }
}