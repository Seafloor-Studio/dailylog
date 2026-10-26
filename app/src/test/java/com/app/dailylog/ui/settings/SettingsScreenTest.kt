package com.app.dailylog.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.dailylog.MainActivity
import com.app.dailylog.R
import com.app.dailylog.repository.Constants
import com.app.dailylog.repository.Shortcut
import com.app.dailylog.repository.ShortcutDatabase
import com.app.dailylog.repository.ShortcutType
import com.app.dailylog.testutil.AppRobolectricTest
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPopupMenu
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest : AppRobolectricTest() {

    override val initialShortcuts = listOf(
        Shortcut("First", "one", 0, ShortcutType.TEXT, 0),
        Shortcut("Second", "two", 0, ShortcutType.TEXT, 1),
    )

    private fun openSettings(): ActivityScenario<MainActivity> =
        launchApp().also { onView(withId(R.id.btnSettings)).perform(click()) }

    private fun MainActivity.settings(): SettingsFragment =
        supportFragmentManager.findFragmentById(R.id.container) as SettingsFragment

    private fun MainActivity.dialog(tag: String): DialogFragment =
        settings().childFragmentManager.findFragmentByTag(tag) as DialogFragment

    private fun MainActivity.shortcuts(): List<Shortcut> {
        idleUntil(500) { false }
        return repository.getAllShortcuts().value.orEmpty()
    }

    private fun DialogFragment.type(id: Int, text: String) =
        requireView().findViewById<EditText>(id).setText(text)

    private fun DialogFragment.click(id: Int) {
        requireView().findViewById<View>(id).performClick()
        idleUntil(500) { false }
    }

    private fun MainActivity.answerFilePicker(uri: Uri): Intent {
        val request = shadowOf(this).nextStartedActivityForResult
        assertNotNull("expected a file picker", request)
        shadowOf(this).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        idleUntil(500) { false }
        return request.intent.getParcelableExtra(Intent.EXTRA_INTENT)!!
    }

    private fun MainActivity.chooseMenuItem(id: Int) {
        findViewById<View>(R.id.shortcutMenuButton).performClick()
        ShadowPopupMenu.getLatestPopupMenu().menu.performIdentifierAction(id, 0)
        idleUntil(500) { false }
    }

    /** Taps the "Log file" row and waits for the sub-screen. */
    private fun MainActivity.openLogFile(): LogFileSettingsFragment {
        findViewById<View>(R.id.logFileRow).performClick()
        idleUntil(500) { false }
        return supportFragmentManager.findFragmentById(R.id.container) as LogFileSettingsFragment
    }

    @Test
    fun listsShortcutsAndFile() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                idleUntil { activity.findViewById<RecyclerView>(R.id.recycler_view).childCount == 2 }
                assertEquals(View.GONE, activity.findViewById<View>(R.id.noShortcutsMessage).visibility)
                // The row shows a readable name, not the SAF URI behind it.
                assertEquals(
                    logFile.name,
                    activity.findViewById<android.widget.TextView>(R.id.logFileValue).text.toString()
                )
            }
        }
    }

    @Test
    fun addDialog_addsShortcut() {
        openSettings().use { scenario ->
            onView(withId(R.id.addShortcutButton)).perform(click())
            scenario.onActivity { activity ->
                val dialog = activity.dialog("fragment_add_shortcut")
                dialog.type(R.id.labelInput, "Third")
                dialog.type(R.id.textInput, "three")
                dialog.click(R.id.btnSaveShortcut)
                assertTrue(activity.shortcuts().any { it.label == "Third" && it.value == "three" })
                assertFalse(dialog.isAdded)
            }
        }
    }

    @Test
    fun addDialog_rejectsDuplicateLabel() {
        openSettings().use { scenario ->
            onView(withId(R.id.addShortcutButton)).perform(click())
            scenario.onActivity { activity ->
                val dialog = activity.dialog("fragment_add_shortcut")
                dialog.type(R.id.labelInput, "First")
                dialog.type(R.id.textInput, "duplicate")
                dialog.click(R.id.btnSaveShortcut)
                assertEquals("one", activity.shortcuts().single { it.label == "First" }.value)
                assertTrue(dialog.isAdded)
                assertEquals(
                    activity.getString(R.string.duplicateNameError),
                    dialog.requireView().findViewById<TextInputLayout>(R.id.labelInputLayout).error.toString()
                )
            }
        }
    }

    @Test
    fun addDialog_rejectsEmptyName() {
        openSettings().use { scenario ->
            onView(withId(R.id.addShortcutButton)).perform(click())
            scenario.onActivity { activity ->
                val dialog = activity.dialog("fragment_add_shortcut")
                dialog.type(R.id.textInput, "nameless")
                dialog.click(R.id.btnSaveShortcut)
                assertTrue(dialog.isAdded)
                assertEquals(
                    activity.getString(R.string.emptyNameError),
                    dialog.requireView().findViewById<TextInputLayout>(R.id.labelInputLayout).error.toString()
                )
            }
        }
    }

    @Test
    fun addDialog_previewFollowsTextAndSlider() {
        openSettings().use { scenario ->
            onView(withId(R.id.addShortcutButton)).perform(click())
            scenario.onActivity { activity ->
                val dialog = activity.dialog("fragment_add_shortcut")
                val preview = dialog.requireView().findViewById<TextView>(R.id.previewText)
                val slider = dialog.requireView().findViewById<Slider>(R.id.cursorSlider)

                dialog.type(R.id.textInput, "hello")
                assertEquals("Preview\nhello|", preview.text.toString())

                slider.value = 2f
                assertEquals("Preview\nhe|llo", preview.text.toString())
                assertEquals(activity.getString(R.string.previewDescription, "he", "llo"), preview.contentDescription)

                slider.value = 0f
                assertEquals("Preview\n|hello", preview.text.toString())

                // Tokens are previewed as typed: the saved cursor index counts the raw text.
                dialog.type(R.id.textInput, "{DATETIME: HH:mm}")
                assertEquals("Preview\n{DATETIME: HH:mm}|", preview.text.toString())

                dialog.type(R.id.textInput, "")
                assertEquals("", preview.text.toString())
            }
        }
    }

    @Test
    fun list_showsTextWithoutCursorMarker() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
                idleUntil { list.childCount == 2 }
                assertEquals("one", list.getChildAt(0).findViewById<TextView>(R.id.text).text.toString())
            }
        }
    }

    @Test
    fun editDialog_updatesShortcut() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
                idleUntil { list.childCount == 2 }
                list.getChildAt(0).performClick()
                idleUntil(500) { false }
                val dialog = activity.dialog("fragment_edit")
                assertEquals("First", dialog.requireView().findViewById<EditText>(R.id.labelInput).text.toString())
                assertEquals(
                    "Preview\n|one",
                    dialog.requireView().findViewById<TextView>(R.id.previewText).text.toString()
                )
                dialog.type(R.id.textInput, "uno")
                dialog.click(R.id.btnSaveShortcut)
                assertEquals("uno", activity.shortcuts().single { it.label == "First" }.value)
            }
        }
    }

    @Test
    fun editDialog_renamesShortcutKeepingId() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
                idleUntil { list.childCount == 2 }
                val id = activity.shortcuts().single { it.label == "First" }.id
                list.getChildAt(0).performClick()
                idleUntil(500) { false }
                val dialog = activity.dialog("fragment_edit")
                dialog.type(R.id.labelInput, "Renamed")
                dialog.click(R.id.btnSaveShortcut)
                idleUntil { list.getChildAt(0).findViewById<TextView>(R.id.label).text.toString() == "Renamed" }
                assertEquals("Renamed", list.getChildAt(0).findViewById<TextView>(R.id.label).text.toString())
                val renamed = activity.shortcuts().filter { it.id == id || it.label == "Renamed" }
                assertEquals(listOf(id to "Renamed"), renamed.map { it.id to it.label })
                assertEquals(2, activity.shortcuts().size)
            }
        }
    }

    @Test
    fun editDialog_rejectsOtherShortcutsLabel() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
                idleUntil { list.childCount == 2 }
                list.getChildAt(0).performClick()
                idleUntil(500) { false }
                val dialog = activity.dialog("fragment_edit")
                dialog.type(R.id.labelInput, "Second")
                dialog.click(R.id.btnSaveShortcut)
                assertNotNull(dialog.requireView().findViewById<TextInputLayout>(R.id.labelInputLayout).error)
                assertTrue(dialog.isAdded)
                assertEquals(listOf("First", "Second"), activity.shortcuts().map { it.label })
            }
        }
    }

    @Test
    fun deleteButton_removesShortcut() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
                idleUntil { list.childCount == 2 }
                list.getChildAt(0).findViewById<View>(R.id.removeShortcutButton).performClick()
                assertEquals(listOf("Second"), activity.shortcuts().map { it.label })
            }
        }
    }

    private fun MainActivity.openBulkAdd(): DialogFragment {
        findViewById<View>(R.id.addShortcutButton).performLongClick()
        idleUntil(500) { false }
        return dialog("fragment_bulk_add")
    }

    private fun DialogFragment.row(index: Int): View =
        requireView().findViewById<LinearLayout>(R.id.bulkRows).getChildAt(index)

    private fun DialogFragment.fillRow(index: Int, name: String, text: String) {
        row(index).findViewById<EditText>(R.id.rowLabel).setText(name)
        row(index).findViewById<EditText>(R.id.rowText).setText(text)
    }

    private fun DialogFragment.cursor(index: Int) = row(index).findViewById<EditText>(R.id.rowCursor)

    private fun DialogFragment.rowError(index: Int, layout: Int) =
        row(index).findViewById<TextInputLayout>(layout).error?.toString()

    private fun DialogFragment.addButton() = requireView().findViewById<Button>(R.id.btnSaveBulkShortcut)

    private fun DialogFragment.rowCount() = requireView().findViewById<LinearLayout>(R.id.bulkRows).childCount

    private fun MainActivity.labelsBecome(expected: List<String>): List<String> {
        idleUntil { repository.getAllShortcuts().value.orEmpty().map { it.label } == expected }
        return repository.getAllShortcuts().value.orEmpty().map { it.label }
    }

    @Test
    fun bulkAdd_addsFilledRowsWithCursorAtEnd() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                dialog.fillRow(0, "Third", "- [ ] three")
                dialog.click(R.id.btnAddRow)
                dialog.fillRow(1, "Date", "## {DATETIME: yyyy-MM-dd}")
                dialog.click(R.id.btnAddRow)
                assertEquals("Add 2", dialog.addButton().text.toString())

                dialog.click(R.id.btnSaveBulkShortcut)

                assertFalse(dialog.isAdded)
                assertEquals(listOf("First", "Second", "Third", "Date"), activity.labelsBecome(listOf("First", "Second", "Third", "Date")))
                val added = activity.shortcuts().associateBy { it.label }
                assertEquals("- [ ] three".length, added.getValue("Third").cursorIndex)
                assertEquals(ShortcutType.TEXT, added.getValue("Third").type)
                assertEquals(ShortcutType.DATETIME, added.getValue("Date").type)
            }
        }
    }

    @Test
    fun bulkAdd_cursorFollowsTheEndOfTheTextAsItIsTyped() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                val text = dialog.row(0).findViewById<EditText>(R.id.rowText)
                dialog.row(0).findViewById<EditText>(R.id.rowLabel).setText("Mood")
                text.setText("mood")
                assertEquals("4", dialog.cursor(0).text.toString())
                text.setText("mood: ")
                assertEquals("6", dialog.cursor(0).text.toString())
                text.setText("mo")
                assertEquals("2", dialog.cursor(0).text.toString())
                text.setText("mood, energy: ")

                dialog.click(R.id.btnSaveBulkShortcut)

                activity.labelsBecome(listOf("First", "Second", "Mood"))
                assertEquals("mood, energy: ".length, activity.shortcuts().single { it.label == "Mood" }.cursorIndex)
            }
        }
    }

    @Test
    fun bulkAdd_aTypedCursorStaysPutWhenTheTextChanges() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                dialog.fillRow(0, "Bold", "****")
                dialog.cursor(0).setText("2")
                dialog.row(0).findViewById<EditText>(R.id.rowText).setText("**  **")
                assertEquals("2", dialog.cursor(0).text.toString())
                dialog.click(R.id.btnAddRow)
                dialog.fillRow(1, "Link", "[[]]")
                dialog.cursor(1).setText("2")
                dialog.click(R.id.btnAddRow)
                dialog.fillRow(2, "Mood", "mood")

                // Removing a row moves the ones below it up; each keeps its own cursor state.
                dialog.row(0).findViewById<View>(R.id.removeRowButton).performClick()
                dialog.row(0).findViewById<EditText>(R.id.rowText).setText("[[ ]]")
                assertEquals("2", dialog.cursor(0).text.toString())
                dialog.row(1).findViewById<EditText>(R.id.rowText).setText("mood: ")
                assertEquals("6", dialog.cursor(1).text.toString())

                dialog.click(R.id.btnSaveBulkShortcut)

                activity.labelsBecome(listOf("First", "Second", "Link", "Mood"))
                val added = activity.shortcuts().associateBy { it.label }
                assertEquals(2, added.getValue("Link").cursorIndex)
                assertEquals(6, added.getValue("Mood").cursorIndex)
            }
        }
    }

    @Test
    fun bulkAdd_clearingTheCursorPutsItBackAtTheEnd() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                dialog.fillRow(0, "Mood", "mood")
                dialog.cursor(0).setText("1")
                dialog.cursor(0).setText("")
                dialog.row(0).findViewById<EditText>(R.id.rowText).setText("mood: ")
                assertEquals("6", dialog.cursor(0).text.toString())
            }
        }
    }

    @Test
    fun bulkAdd_aCursorPastTheEndOfTheTextIsMarkedAndBlocksAdd() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                dialog.fillRow(0, "Wrap", "**bold**")
                dialog.cursor(0).setText("6")
                assertEquals(null, dialog.rowError(0, R.id.rowCursorLayout))
                assertTrue(dialog.addButton().isEnabled)

                // The typed position stays put, so shortening the text can leave it past the end.
                dialog.row(0).findViewById<EditText>(R.id.rowText).setText("****")
                assertEquals(context.getString(R.string.cursorPastEndError, 4), dialog.rowError(0, R.id.rowCursorLayout))
                assertFalse(dialog.addButton().isEnabled)

                // Too long for an Int is past the end too, not a crash.
                dialog.cursor(0).setText("99999999999")
                assertEquals(context.getString(R.string.cursorPastEndError, 4), dialog.rowError(0, R.id.rowCursorLayout))

                // Some keyboards type their own script's digits.
                dialog.cursor(0).setText("\u0662")
                assertEquals(null, dialog.rowError(0, R.id.rowCursorLayout))
                dialog.click(R.id.btnSaveBulkShortcut)

                activity.labelsBecome(listOf("First", "Second", "Wrap"))
                assertEquals(2, activity.shortcuts().single { it.label == "Wrap" }.cursorIndex)
            }
        }
    }

    @Test
    fun bulkAdd_aTakenOrRepeatedNameIsMarkedAndBlocksAdd() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                // A keyboard suggestion's trailing space doesn't make the name new.
                dialog.fillRow(0, "First ", "again")
                assertEquals(context.getString(R.string.duplicateNameError), dialog.rowError(0, R.id.rowLabelLayout))
                assertFalse(dialog.addButton().isEnabled)

                dialog.fillRow(0, "Third", "three")
                dialog.click(R.id.btnAddRow)
                dialog.fillRow(1, "Third", "also three")
                assertEquals(null, dialog.rowError(0, R.id.rowLabelLayout))
                assertEquals(context.getString(R.string.duplicateRowError), dialog.rowError(1, R.id.rowLabelLayout))
                assertFalse(dialog.addButton().isEnabled)

                dialog.row(1).findViewById<View>(R.id.removeRowButton).performClick()
                assertTrue(dialog.addButton().isEnabled)

                // Removing the last row leaves an empty one to type into.
                dialog.row(0).findViewById<View>(R.id.removeRowButton).performClick()
                assertEquals(1, dialog.rowCount())
                assertEquals("", dialog.row(0).findViewById<EditText>(R.id.rowLabel).text.toString())
            }
        }
    }

    @Test
    fun bulkAdd_aHalfFilledRowIsFlaggedOnAddAndNothingIsSaved() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                dialog.fillRow(0, "Third", "three")
                dialog.click(R.id.btnAddRow)
                dialog.fillRow(1, "Fourth", "")
                // Not flagged while it's still being typed.
                assertEquals(null, dialog.rowError(1, R.id.rowTextLayout))

                dialog.click(R.id.btnSaveBulkShortcut)

                assertTrue(dialog.isAdded)
                assertEquals(context.getString(R.string.emptyTextError), dialog.rowError(1, R.id.rowTextLayout))
                assertEquals(listOf("First", "Second"), activity.shortcuts().map { it.label })

                // A row added after the tap isn't flagged while it's being typed.
                dialog.click(R.id.btnAddRow)
                dialog.row(2).findViewById<EditText>(R.id.rowLabel).setText("Fifth")
                assertEquals(null, dialog.rowError(2, R.id.rowTextLayout))
            }
        }
    }

    @Test
    fun bulkAdd_aLoneHalfFilledRowCanBeTappedToSeeWhatsMissing() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                assertFalse(dialog.addButton().isEnabled)
                dialog.fillRow(0, "Third", "")
                assertTrue(dialog.addButton().isEnabled)

                dialog.click(R.id.btnSaveBulkShortcut)

                assertTrue(dialog.isAdded)
                assertEquals(context.getString(R.string.emptyTextError), dialog.rowError(0, R.id.rowTextLayout))
                assertFalse(dialog.addButton().isEnabled)
            }
        }
    }

    @Test
    fun bulkAdd_rowsSurviveRotation() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                val dialog = activity.openBulkAdd()
                dialog.fillRow(0, "Third", "three")
                dialog.cursor(0).setText("1")
                dialog.click(R.id.btnAddRow)
                dialog.fillRow(1, "Fourth", "four")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                idleUntil { (activity.supportFragmentManager.findFragmentById(R.id.container) as? SettingsFragment)
                    ?.childFragmentManager?.findFragmentByTag("fragment_bulk_add")?.view != null }
                val dialog = activity.dialog("fragment_bulk_add")
                assertEquals(2, dialog.rowCount())
                assertEquals("Fourth", dialog.row(1).findViewById<EditText>(R.id.rowLabel).text.toString())
                assertEquals("four", dialog.row(1).findViewById<EditText>(R.id.rowText).text.toString())
                assertEquals("Add 2", dialog.addButton().text.toString())
                // The typed cursor stays put and the other still follows its text.
                dialog.row(0).findViewById<EditText>(R.id.rowText).setText("three!")
                dialog.row(1).findViewById<EditText>(R.id.rowText).setText("four!")
                assertEquals("1", dialog.cursor(0).text.toString())
                assertEquals("5", dialog.cursor(1).text.toString())
                dialog.click(R.id.btnSaveBulkShortcut)
                // The recreated activity's repository has no observer (the view model kept the
                // old one), so ask the database rather than its LiveData.
                val cursorOf = { label: String ->
                    runBlocking(Dispatchers.IO) {
                        ShortcutDatabase.getDatabase(activity)
                            .query("SELECT cursorIndex FROM Shortcut WHERE label = ?", arrayOf(label))
                            .use { if (it.moveToFirst()) it.getInt(0) else null }
                    }
                }
                assertTrue(idleUntil { cursorOf("Third") != null && cursorOf("Fourth") != null })
                assertEquals(1, cursorOf("Third"))
                assertEquals("four!".length, cursorOf("Fourth"))
                assertFalse(dialog.isAdded)
            }
        }
    }

    @Test
    fun menuBulkAdd_opensDialog() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.chooseMenuItem(R.id.bulkAdd)
                assertTrue(activity.dialog("fragment_bulk_add").isAdded)
            }
        }
    }

    @Test
    fun exportThenImportJson_roundTripsThroughFilePicker() {
        val exported = File(context.filesDir, "shortcuts.json")
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.shortcuts()
                activity.chooseMenuItem(R.id.exportShortcuts)
                activity.answerFilePicker(Uri.fromFile(exported))
                idleUntil { exported.exists() && exported.length() > 0 }
                assertTrue(exported.readText().contains("\"First\""))

                val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
                repeat(2) {
                    idleUntil { list.childCount > 0 }
                    list.getChildAt(0).findViewById<View>(R.id.removeShortcutButton).performClick()
                    activity.shortcuts()
                }
                assertTrue(activity.shortcuts().isEmpty())

                activity.chooseMenuItem(R.id.importShortcuts)
                activity.answerFilePicker(Uri.fromFile(exported))
                idleUntil { activity.repository.getAllShortcuts().value.orEmpty().size == 2 }
                assertEquals(listOf("First", "Second"), activity.shortcuts().map { it.label })
            }
        }
        exported.delete()
    }

    @Test
    fun importLegacyCsv_addsShortcuts() {
        val csv = File(context.filesDir, "legacy.csv").apply { writeText("\"Third\",\"three\",\"0\",\"TEXT\"\n") }
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.shortcuts()
                activity.chooseMenuItem(R.id.importShortcutsLegacy)
                activity.answerFilePicker(Uri.fromFile(csv))
                idleUntil { activity.repository.getAllShortcuts().value.orEmpty().size == 3 }
                assertTrue(activity.shortcuts().any { it.label == "Third" })
            }
        }
        csv.delete()
    }

    @Test
    fun selectFile_changesLogFile() {
        val other = File(context.filesDir, "other.md").apply { writeText("other\n") }
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.openLogFile()
                activity.findViewById<View>(R.id.selectFileButton).performClick()
                val picker = activity.answerFilePicker(Uri.fromFile(other))
                // A text/* filter greys out .md files that the provider reports with another type.
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
                assertEquals("*/*", picker.type)
                assertEquals(
                    other.name,
                    activity.findViewById<android.widget.TextView>(R.id.fileName).text.toString()
                )
                assertEquals(Uri.fromFile(other).toString(), activity.repository.filename)
                // Back to Settings: the row reflects the new file.
                activity.onBackPressedDispatcher.onBackPressed()
                idleUntil(500) { false }
                assertEquals(
                    other.name,
                    activity.findViewById<android.widget.TextView>(R.id.logFileValue).text.toString()
                )
            }
        }
        other.delete()
    }

    @Test
    fun selectFile_thenLeavingTheApp_leavesTheNewFileUntouched() {
        // CRLF and no trailing newline: reading normalizes both, so any save would change the bytes.
        val original = "first\r\nsecond"
        val other = File(context.filesDir, "other.md").apply { writeText(original) }
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.openLogFile()
                activity.findViewById<View>(R.id.selectFileButton).performClick()
                activity.answerFilePicker(Uri.fromFile(other))
                repeat(2) {
                    activity.onBackPressedDispatcher.onBackPressed()
                    idleUntil(500) { false }
                }
                assertEquals("first\nsecond\n", activity.findViewById<EditText>(R.id.todayLog).text.toString())
            }
            scenario.moveToState(Lifecycle.State.CREATED)
        }
        assertEquals(original, other.readText())
        other.delete()
    }

    private fun MainActivity.backToLog(): EditText {
        repeat(2) { onBackPressedDispatcher.onBackPressed() }
        assertTrue(idleUntil { findViewById<EditText>(R.id.todayLog) != null })
        return findViewById(R.id.todayLog)
    }

    private fun MainActivity.shownFileName() = findViewById<TextView>(R.id.fileName).text.toString()

    @Test
    fun newFile_switchesToTheCreatedFileAndStartsItsCursorAfresh() {
        // The save-as screen creates the (empty) file before handing back its URI.
        val created = File(context.filesDir, "garden-notes.md").apply { writeText("") }
        val oldUri = Uri.fromFile(logFile)
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(oldUri, grant)
        context.getSharedPreferences("SharedPreferences", android.content.Context.MODE_PRIVATE)
            .edit().putInt(Constants.CURSOR_KEY, 4).commit()
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.openLogFile()
                activity.findViewById<View>(R.id.newFileButton).performClick()
                val saveAs = activity.answerFilePicker(Uri.fromFile(created))
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, saveAs.action)
                assertEquals("text/markdown", saveAs.type)
                assertEquals("journal.md", saveAs.getStringExtra(Intent.EXTRA_TITLE))

                assertEquals(created.name, activity.shownFileName())
                assertEquals("Now writing to ${created.name}", ShadowToast.getTextOfLatestToast())
                assertEquals(Uri.fromFile(created).toString(), activity.repository.retrieveFilename())
                assertEquals(
                    listOf(Uri.fromFile(created)),
                    context.contentResolver.persistedUriPermissions.map { it.uri }
                )

                val log = activity.backToLog()
                assertEquals("", log.text.toString())
                assertEquals(Constants.DEFAULT_CURSOR_INDEX, activity.repository.getCursorIndex())
                log.setText("planted tomatoes\n")
                activity.findViewById<View>(R.id.btnSave).performClick()
            }
        }
        assertEquals("planted tomatoes\n", created.readText())
        assertEquals("Existing log\n", logFile.readText())
        created.delete()
    }

    @Test
    fun newFile_resultArrivingAfterRecreation_stillSwitchesFile() {
        val created = File(context.filesDir, "trail-log.md").apply { writeText("") }
        openSettings().use { scenario ->
            lateinit var saveAs: Intent
            scenario.onActivity { activity ->
                activity.openLogFile()
                activity.findViewById<View>(R.id.newFileButton).performClick()
                saveAs = shadowOf(activity).nextStartedActivityForResult.intent
            }
            // A rotation while the save-as screen is in front.
            scenario.recreate()
            scenario.onActivity { activity ->
                shadowOf(activity).receiveResult(saveAs, Activity.RESULT_OK, Intent().setData(Uri.fromFile(created)))
                assertTrue(idleUntil { activity.shownFileName() == created.name })
                assertEquals(Uri.fromFile(created).toString(), activity.repository.retrieveFilename())
            }
        }
        created.delete()
    }

    @Test
    fun newFile_cancelled_keepsTheCurrentFile() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.openLogFile()
                activity.findViewById<View>(R.id.newFileButton).performClick()
                val request = shadowOf(activity).nextStartedActivityForResult
                assertNotNull("expected the save-as screen", request)
                shadowOf(activity).receiveResult(request.intent, Activity.RESULT_CANCELED, null)
                shadowOf(android.os.Looper.getMainLooper()).idle()

                assertEquals(logFile.name, activity.shownFileName())
                assertEquals(Uri.fromFile(logFile).toString(), activity.repository.retrieveFilename())
                assertEquals("Existing log\n", activity.backToLog().text.toString())
            }
        }
    }

    @Test
    fun errorDialog_showsMessage() {
        launchApp().use { scenario ->
            scenario.onActivity { activity ->
                activity.showErrorDialog("Something broke")
                idleUntil(500) { false }
                val dialog = ShadowDialog.getLatestDialog() as android.app.AlertDialog
                assertTrue(dialog.isShowing)
                assertEquals("Something broke", shadowOf(dialog).message.toString())
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class EmptySettingsScreenTest : AppRobolectricTest() {
    @Test
    fun noShortcuts_showsInstructions() {
        launchApp().use { scenario ->
            onView(withId(R.id.btnSettings)).perform(click())
            scenario.onActivity { activity ->
                idleUntil(500) { false }
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.noShortcutsMessage).visibility)
            }
        }
    }
}
