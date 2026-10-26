package com.app.dailylog.repository

import androidx.lifecycle.LiveData
import com.app.dailylog.utils.FileNameTemplate

/** One row of the "Add several shortcuts" dialog. [cursorIndex] counts characters of [text] as typed. */
data class ShortcutRow(val label: String, val text: String, val cursorIndex: Int)

interface ShortcutRepositoryInterface {
    val shortcutDao: ShortcutDao

    var shortcutLiveData: LiveData<List<Shortcut>>

    private suspend fun saveShortcutToDB(shortcut: Shortcut): Boolean {
        shortcutDao.add(shortcut)
        return true
    }

    private fun createShortcut(label: String, text: String, cursorIndex: Int, type: String): Shortcut {
        return  Shortcut(label = label, value = text, cursorIndex = cursorIndex, position = nextShortcutPosition(), type=type)
    }

    private suspend fun deleteShortcutFromDB(id: String): Boolean {
        shortcutDao.deleteById(id)
        return true
    }

    suspend fun saveAllShortcutsToDb(shortcuts: List<Shortcut>) {
        shortcutDao.updateAll(*shortcuts.toTypedArray())
    }

    suspend fun updateShortcut(id: String, label: String, text: String, cursorIndex: Int, position: Int, type: String): Boolean {
        val shortcut = Shortcut(id = id, label = label, value = text, cursorIndex = cursorIndex, position = position, type= type)
        shortcutDao.updateAll(shortcut)
        return true
    }

    suspend fun addShortcut(label: String, text: String, cursorIndex: Int, type: String) {
        val shortcut = createShortcut(label, text, cursorIndex, type)
        if (!shortcutDao.labelExistsSuspend(label) && label.isNotEmpty() && text.isNotEmpty()) {
            saveShortcutToDB(shortcut)
        }
    }

    suspend fun bulkAddShortcuts(shortcutInfoList: List<Array<String>>): Boolean {
        val results = ArrayList<Shortcut>()
        shortcutInfoList.forEachIndexed {
            index, list ->
            //TODO: create popup or Toast with failures
            if (validateShortcutRow(list, index + 1)) {
                val label = list[0]
                val text = list[1]
                val cursorIndex = list[2].toInt()
                val type = list[3]
                results.add(
                    Shortcut(
                        label = label,
                        value = text,
                        cursorIndex = cursorIndex,
                        position = nextShortcutPosition() + index,
                        type = type
                    )
                )
            }
        }
        shortcutDao.addAll(*results.toTypedArray())
        return true
    }

    /**
     * Adds shortcuts from bulk add's rows, after the existing ones. Each is a date shortcut when
     * its text holds a {DATETIME: …} token. Rows with a name that's taken or a blank field are
     * skipped, and a cursor index outside the text is kept inside it.
     */
    suspend fun addShortcuts(rows: List<ShortcutRow>) {
        val start = nextShortcutPosition()
        val shortcuts = rows
            .filter { row -> row.label.isNotEmpty() && row.text.isNotEmpty() && !shortcutDao.labelExistsSuspend(row.label) }
            .distinctBy { it.label }
            .mapIndexed { index, (label, text, cursorIndex) ->
                val type = if (FileNameTemplate.DATETIME_TOKEN.containsMatchIn(text)) ShortcutType.DATETIME else ShortcutType.TEXT
                Shortcut(label = label, value = text, cursorIndex = cursorIndex.coerceIn(0, text.length), position = start + index, type = type)
            }
        shortcutDao.addAll(*shortcuts.toTypedArray())
    }

    private fun nextShortcutPosition(): Int {
        return if (shortcutLiveData.value != null && shortcutLiveData.value!!.isNotEmpty()) {
            shortcutLiveData.value!!.last().position + 1
        } else {
            0
        }
    }

    fun cleanUpText(text: String): String {
        // Commas are the field separator in the bulk-add format. If a shortcut's text value
        // contains a comma, the user must wrap it in quotes (standard CSV escaping) so the
        // parser doesn't treat that comma as a field boundary. A simple split-on-comma parser
        // leaves those surrounding quotes in place, so we strip them here to recover the
        // actual text. We only strip quotes when a comma is present: quotes around text with
        // no comma are intentional content, not CSV escaping.
        val containsComma = text.contains(',')
        val startsAndEndsWithQuotes = text.startsWith('"') && text.endsWith('"')
        return if (!containsComma || !startsAndEndsWithQuotes) {
            text
        } else {
            text.substring(1, text.length-1)
        }
    }

    fun isCursorValid(cursor: String, text: String): Boolean {
        return try {
            val value = cursor.toInt()
            value >= 0 && value <= text.length
        } catch (e: NumberFormatException) {
            false
        }
    }

    fun isTextValid(text: String): Boolean {
        return text.isNotEmpty()
    }

    /** [excludeId] is the shortcut being edited, which may keep its own label. */
    fun isLabelValid(label: String, excludeId: String? = null): Boolean {
        if (label.isEmpty()) {
            return false
        }
        // orEmpty, not !!: the list is null until Room's first emission reaches the
        // observer, and validation can be asked before that (#120).
        return shortcutLiveData.value.orEmpty().none { it.label == label && it.id != excludeId }
    }

    fun validateShortcutRow(shortcutInfo: Array<String>, index: Int): Boolean {
        if (shortcutInfo.size != 4) {
            throw java.lang.IllegalArgumentException("Line $index: need exactly four values")
        }
        var (label, text, cursor, type) = shortcutInfo
        cursor = cursor.trim()
        text = cleanUpText(text)
        val validLabel = isLabelValid(label)
        val validType = ShortcutType.isTypeValid(type)
        if (validType != null && !validType) {
            throw java.lang.IllegalArgumentException("Line $index: shortcut Type must be one of" +
                    " the following: ${ShortcutType.validTypes().contentDeepToString()}")
        }
        if (!validLabel) {
            throw java.lang.IllegalArgumentException("Line $index: label must be unique and cannot be empty")
        }
        if (!isTextValid(text)) {
            throw java.lang.IllegalArgumentException("Line $index: text cannot be empty")
        }
        if (!isCursorValid(cursor, text)) {
            throw java.lang.IllegalArgumentException("Line $index: cursor must be an int. Cursor " +
                    "cannot be less than 0 or greater than the length of text.")
        }
        return true
    }

    suspend fun removeShortcut(id: String): Boolean {
        deleteShortcutFromDB(id)
        return true
    }

    suspend fun updateShortcutPositions(shortcuts: List<Shortcut>) {
        shortcuts.forEachIndexed { index, shortcut ->
            shortcut.position = index
        }
        saveAllShortcutsToDb(shortcuts)
    }

    fun getExportRows(): List<List<String>> {
        val results = arrayListOf<List<String>>()
        if (shortcutLiveData.value != null && shortcutLiveData.value!!.isNotEmpty()) {
            val shortcuts = shortcutLiveData.value
            if (shortcuts != null) {
                for (shortcut in shortcuts) {
                    val row = arrayListOf<String>()
                    row.add(shortcut.label)
                    row.add(shortcut.value)
                    row.add(shortcut.cursorIndex.toString())
                    row.add(shortcut.type)
                    results.add(row)
                }
            }
        }
        return results
    }
}