package com.app.dailylog.repository

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logFile = File(context.filesDir, "log.md")
    private val exportFile = File(context.filesDir, "export")
    private lateinit var repository: Repository
    private val keepLiveDataActive = Observer<List<Shortcut>> {}

    @Before
    fun setUp() {
        repository = Repository(context)
        // Validation and positions read the LiveData's value, which only updates while observed.
        repository.getAllShortcuts().observeForever(keepLiveDataActive)
        // Exports write nothing until Room's first load lands, which can take longer than idle()
        // on a busy runner.
        val deadline = System.currentTimeMillis() + 5000
        while (repository.getAllShortcuts().value == null) {
            assertTrue("shortcuts never loaded", System.currentTimeMillis() < deadline)
            idle()
        }
    }

    @After
    fun tearDown() {
        repository.getAllShortcuts().removeObserver(keepLiveDataActive)
        ShortcutDatabase.resetForTesting()
        context.deleteDatabase("shortcut_database")
        context.getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE).edit().clear().commit()
        logFile.delete()
        exportFile.delete()
    }

    private fun idle() {
        // Room delivers LiveData updates from a background thread.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun shortcuts(): List<Shortcut> {
        idle()
        return repository.getAllShortcuts().value.orEmpty()
    }

    private fun useLogFile(contents: String) {
        logFile.writeText(contents)
        repository.storeFilename(Uri.fromFile(logFile).toString())
    }

    @Test
    fun noFileSelected_userMustSelectFile() {
        assertTrue(repository.userMustSelectFile())
    }

    @Test
    fun storedFilename_isRestoredByNewRepository() {
        useLogFile("")
        val reopened = Repository(context)
        assertFalse(reopened.userMustSelectFile())
        assertEquals(Uri.fromFile(logFile).toString(), reopened.filename)
    }

    @Test
    fun cursorIndex_defaultsThenPersists() {
        assertEquals(Constants.DEFAULT_CURSOR_INDEX, repository.getCursorIndex())
        repository.setCursorIndex(7)
        assertEquals(7, repository.getCursorIndex())
    }

    @Test
    fun readFile_returnsContents() {
        useLogFile("line one\nline two\n")
        assertEquals("line one\nline two\n", repository.readFile(true))
    }

    @Test
    fun readFile_missingFile_returnsEmpty() {
        repository.storeFilename(Uri.fromFile(File(context.filesDir, "missing.md")).toString())
        assertEquals("", repository.readFile(true))
    }

    @Test
    fun smartSave_skipsUnchangedContents() {
        useLogFile("hello\n")
        val contents = repository.readFile(true)
        assertFalse(repository.saveToFile(contents, false))
    }

    @Test
    fun smartSave_writesChangedContents() {
        useLogFile("hello\n")
        repository.readFile(true)
        assertTrue(repository.saveToFile("hello again\n", false))
        assertEquals("hello again\n", logFile.readText())
        // The new contents become the baseline for the next smart save.
        assertFalse(repository.saveToFile("hello again\n", false))
    }

    @Test
    fun forceSave_writesEvenWhenUnchanged() {
        useLogFile("hello\n")
        val contents = repository.readFile(true)
        assertTrue(repository.saveToFile(contents, true))
    }

    @Test
    fun readFile_afterFirstTime_doesNotResetSmartSaveBaseline() {
        useLogFile("original\n")
        repository.readFile(true)
        logFile.writeText("edited elsewhere\n")
        repository.readFile(false)
        assertTrue(repository.saveToFile("edited elsewhere\n", false))
    }

    @Test
    fun save_toUnwritableLocation_returnsFalse() {
        repository.storeFilename("content://nonexistent.provider/log.md")
        assertFalse(repository.saveToFile("text", true))
    }

    @Test
    fun addShortcut_assignsIncreasingPositions() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        shortcuts()
        repository.addShortcut("b", "two", 0, ShortcutType.TEXT)
        assertEquals(listOf("a" to 0, "b" to 1), shortcuts().map { it.label to it.position })
    }

    @Test
    fun addShortcut_rejectsDuplicateAndEmpty() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        repository.addShortcut("a", "different", 0, ShortcutType.TEXT)
        repository.addShortcut("", "text", 0, ShortcutType.TEXT)
        repository.addShortcut("b", "", 0, ShortcutType.TEXT)
        assertEquals(listOf("a" to "one"), shortcuts().map { it.label to it.value })
    }

    @Test
    fun updateShortcut_changesValue() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        val id = shortcuts().single().id
        repository.updateShortcut(id, "a", "uno", 2, 0, ShortcutType.DATETIME)
        val updated = shortcuts().single()
        assertEquals("uno", updated.value)
        assertEquals(2, updated.cursorIndex)
        assertEquals(ShortcutType.DATETIME, updated.type)
    }

    @Test
    fun updateShortcut_renamesLabelKeepingId() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        val id = shortcuts().single().id
        repository.updateShortcut(id, "renamed", "one", 0, 0, ShortcutType.TEXT)
        assertEquals(listOf(id to "renamed"), shortcuts().map { it.id to it.label })
    }

    @Test
    fun removeShortcut_deletesIt() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        repository.removeShortcut(shortcuts().single().id)
        assertTrue(shortcuts().isEmpty())
    }

    @Test
    fun updateShortcutPositions_followsListOrder() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        shortcuts()
        repository.addShortcut("b", "two", 0, ShortcutType.TEXT)
        repository.updateShortcutPositions(shortcuts().reversed())
        assertEquals(listOf("b", "a"), shortcuts().map { it.label })
    }

    @Test
    fun bulkAdd_placesNewShortcutsAfterExisting() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        shortcuts()
        repository.bulkAddShortcuts(
            listOf(arrayOf("b", "two", "0", ShortcutType.TEXT), arrayOf("c", "three", "1", ShortcutType.TEXT))
        )
        assertEquals(listOf("a" to 0, "b" to 1, "c" to 2), shortcuts().map { it.label to it.position })
    }

    @Test
    fun bulkAdd_numbersLinesFromOne() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.bulkAddShortcuts(
                    listOf(arrayOf("a", "one", "0", ShortcutType.TEXT), arrayOf("b", "two", "99", ShortcutType.TEXT))
                )
            }
        }
        assertTrue(error.message!!, error.message!!.startsWith("Line 2:"))
    }

    @Test
    fun isLabelValid_whenTheListHasNotLoadedYet() {
        // No observer on this instance, so its LiveData has never emitted and its value is
        // null. That is the state a bulk add hits when it runs before Room's first emission,
        // and it used to throw NullPointerException from inside isLabelValid.
        val beforeFirstEmission = Repository(context)
        assertTrue(beforeFirstEmission.isLabelValid("anything"))
    }

    @Test
    fun labelExists_reflectsDatabase() = runBlocking {
        repository.addShortcut("a", "one", 0, ShortcutType.TEXT)
        val exists = repository.labelExists("a")
        val missing = repository.labelExists("zzz")
        exists.observeForever {}
        missing.observeForever {}
        idle()
        assertEquals(true, exists.value)
        assertEquals(false, missing.value)
    }

    @Test
    fun getExportRows_isEmptyWithNoShortcuts() {
        assertTrue(repository.getExportRows().isEmpty())
    }

    @Test
    fun jsonExportThenImport_roundTrips() = runBlocking {
        repository.addShortcut("a", "one", 1, ShortcutType.TEXT)
        shortcuts()
        repository.addShortcut("b", "{DATETIME: yyyy}", 0, ShortcutType.DATETIME)
        shortcuts()
        repository.exportShortcutsAsJson(Uri.fromFile(exportFile))
        val exported = exportFile.readText()
        assertTrue(exported.contains("\"schemaVersion\""))
        assertFalse(exported.contains("\"id\""))

        shortcuts().forEach { repository.removeShortcut(it.id) }
        assertTrue(shortcuts().isEmpty())

        repository.importShortcutsFromJson(Uri.fromFile(exportFile))
        assertEquals(
            listOf(Triple("a", "one", 1), Triple("b", "{DATETIME: yyyy}", 0)),
            shortcuts().map { Triple(it.label, it.value, it.cursorIndex) }
        )
    }

    @Test
    fun csvExportThenImport_roundTrips() = runBlocking {
        repository.addShortcut("a", "one, with comma", 3, ShortcutType.TEXT)
        shortcuts()
        repository.exportShortcuts(Uri.fromFile(exportFile))
        repository.removeShortcut(shortcuts().single().id)
        assertTrue(shortcuts().isEmpty())

        repository.importShortcuts(Uri.fromFile(exportFile))
        assertEquals(listOf("a" to "one, with comma"), shortcuts().map { it.label to it.value })
    }

    @Test
    fun csvImport_ofMissingFile_returnsNull() {
        assertNull(repository.importShortcutValuesFromCSV(Uri.fromFile(File(context.filesDir, "nope.csv"))))
    }

    @Test
    fun jsonExport_withNoShortcuts_importsNothing() = runBlocking {
        repository.exportShortcutsAsJson(Uri.fromFile(exportFile))
        repository.importShortcutsFromJson(Uri.fromFile(exportFile))
        assertTrue(shortcuts().isEmpty())
    }
}
