package com.app.dailylog.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.dailylog.testutil.TestDocumentsProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DatedFileRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folderDir = File(context.filesDir, "Journal")
    private val singleFile = File(context.filesDir, "log.md")
    private lateinit var folder: Uri
    private lateinit var repository: Repository

    private val oct3: Clock = Clock.fixed(Instant.parse("2026-10-03T09:00:00Z"), ZoneOffset.UTC)
    private val oct4: Clock = Clock.fixed(Instant.parse("2026-10-04T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        folder = TestDocumentsProvider.install(context, folderDir)
        singleFile.writeText("single file\n")
        repository = Repository(context)
        repository.storeFilename(Uri.fromFile(singleFile).toString())
        repository.storeLogFolder(folder)
        repository.storeFileNameTemplate("{DATETIME: yyyy}/{DATETIME: MM-dd}-journal.md")
    }

    @After
    fun tearDown() {
        ShortcutDatabase.resetForTesting()
        context.deleteDatabase("shortcut_database")
        context.getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE).edit().clear().commit()
        folderDir.deleteRecursively()
        singleFile.delete()
    }

    @Test
    fun oneFileModeIgnoresTheFolder() {
        repository.openCurrentFile(oct3)
        assertFalse(repository.isDatedMode())
        assertEquals("single file\n", repository.readFile(true))
        assertNull(repository.datedFilePath)
    }

    @Test
    fun aNewDayStartsEmptyAndCreatesNothingUntilSaved() {
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)

        assertEquals("2026/10-03-journal.md", repository.datedFilePath)
        assertEquals("", repository.readFile(true))
        assertFalse("an unchanged empty file isn't saved", repository.saveToFile("", false))
        assertFalse(File(folderDir, "2026").exists())

        assertTrue(repository.saveToFile("hello\n", false))
        assertEquals("hello\n", File(folderDir, "2026/10-03-journal.md").readText())
        assertEquals("hello\n", repository.readFile(false))
        assertEquals("text/markdown", TestDocumentsProvider.createdMimeTypes["2026/10-03-journal.md"])
    }

    @Test
    fun reopensTodaysFileAndKeepsTheCursor() {
        File(folderDir, "2026").mkdirs()
        File(folderDir, "2026/10-03-journal.md").writeText("earlier today\n")
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        repository.setCursorIndex(4)

        val second = Repository(context)
        assertFalse(second.openCurrentFile(oct3))
        assertEquals("earlier today\n", second.readFile(true))
        assertEquals(4, second.getCursorIndex())
    }

    @Test
    fun theNextDaySwitchesFileAndResetsTheCursor() {
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        repository.readFile(true)
        repository.saveToFile("day one\n", false)
        repository.setCursorIndex(3)

        assertTrue(repository.openCurrentFile(oct4))
        assertEquals("2026/10-04-journal.md", repository.datedFilePath)
        assertEquals(Constants.DEFAULT_CURSOR_INDEX, repository.getCursorIndex())
        assertEquals("", repository.readFile(true))
        repository.saveToFile("day two\n", false)

        assertEquals("day one\n", File(folderDir, "2026/10-03-journal.md").readText())
        assertEquals("day two\n", File(folderDir, "2026/10-04-journal.md").readText())
    }

    @Test
    fun switchingBackToOneFileReopensIt() {
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        repository.setDatedMode(false)

        assertTrue(repository.openCurrentFile(oct3))
        assertEquals("single file\n", repository.readFile(true))
    }

    @Test
    fun existingUsersKeepTheirCursorAfterUpdating() {
        repository.setCursorIndex(5)
        assertFalse(repository.openCurrentFile(oct3))
        assertEquals(5, repository.getCursorIndex())
    }

    @Test
    fun datedModeNeedsAFolder() {
        context.getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE).edit()
            .remove(Constants.LOG_FOLDER_PREF_KEY).commit()
        repository.setDatedMode(true)
        assertFalse(repository.isDatedMode())
    }

    @Test
    fun noFileNeededWhenWritingToAFolder() {
        context.getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE).edit()
            .remove(Constants.FILENAME_PREF_KEY).commit()
        assertTrue(repository.userMustSelectFile())
        repository.setDatedMode(true)
        assertFalse(repository.userMustSelectFile())
    }

    @Test
    fun aFileThatAppearsAfterOpening_isKeptAndAddedTo() {
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        repository.readFile(true)
        // e.g. a sync app brings in today's file from another device
        File(folderDir, "2026").mkdirs()
        File(folderDir, "2026/10-03-journal.md").writeText("from the laptop\n")

        assertTrue(repository.saveToFile("from the phone\n", false))
        assertEquals("from the laptop\nfrom the phone\n", File(folderDir, "2026/10-03-journal.md").readText())
        assertEquals(listOf("10-03-journal.md"), File(folderDir, "2026").list()!!.toList())
    }

    @Test
    fun aFileThatAppearsButCantBeRead_isLeftAlone() {
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        repository.readFile(true)
        File(folderDir, "2026").mkdirs()
        File(folderDir, "2026/10-03-journal.md").writeText("from the laptop\n")
        TestDocumentsProvider.failReads = true

        assertFalse(repository.saveToFile("from the phone\n", false))
        assertEquals("from the laptop\n", File(folderDir, "2026/10-03-journal.md").readText())
    }

    @Test
    fun aFailedRead_doesNotBlockTheNextDaysNewFile() {
        File(folderDir, "2026").mkdirs()
        File(folderDir, "2026/10-03-journal.md").writeText("day one\n")
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        TestDocumentsProvider.failReads = true
        assertEquals("", repository.readFile(true))
        assertFalse(repository.saveToFile("", false))

        repository.openCurrentFile(oct4)
        assertEquals("", repository.readFile(true))
        assertTrue(repository.saveToFile("day two\n", false))
        assertEquals("day two\n", File(folderDir, "2026/10-04-journal.md").readText())
        assertEquals("day one\n", File(folderDir, "2026/10-03-journal.md").readText())
    }

    @Test
    fun aFileDeletedElsewhere_isNotRecreatedEmpty() {
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)
        repository.readFile(true)
        repository.saveToFile("hello\n", false)
        File(folderDir, "2026/10-03-journal.md").delete()

        assertFalse(repository.openCurrentFile(oct3))
        assertEquals("", repository.readFile(true))
        assertFalse(repository.saveToFile("", false))
        assertFalse(File(folderDir, "2026/10-03-journal.md").exists())
    }

    @Test
    fun anUnreachableFolder_failsToSaveWithoutCrashing() {
        repository.storeLogFolder(Uri.parse("content://com.example.gone/tree/root"))
        repository.setDatedMode(true)
        repository.openCurrentFile(oct3)

        assertNull(repository.retrieveLogFolderName())
        assertEquals("", repository.readFile(true))
        assertFalse(repository.saveToFile("hello\n", false))
    }

    @Test
    fun namesTheFolder() {
        assertEquals("Journal", repository.retrieveLogFolderName())
    }
}
