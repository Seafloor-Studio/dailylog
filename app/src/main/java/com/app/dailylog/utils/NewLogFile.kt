package com.app.dailylog.utils

import android.content.Intent

/** The save-as screen for a new log file, shared by the Welcome and File screens. */
object NewLogFile {
    const val DEFAULT_NAME = "journal.md"

    fun intent(): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/markdown"
        putExtra(Intent.EXTRA_TITLE, DEFAULT_NAME)
    }
}
