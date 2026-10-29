package com.app.dailylog.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
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
import com.app.dailylog.repository.ShortcutType
import com.app.dailylog.testutil.AppRobolectricTest
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
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

    @Test
    fun bulkAdd_addsValidLines() {
        openSettings().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.addShortcutButton).performLongClick()
                idleUntil(500) { false }
                val dialog = activity.dialog("fragment_bulk_add")
                dialog.type(R.id.bulkInput, "Third,three,0,TEXT\nFourth,four,2,TEXT")
                dialog.click(R.id.btnSaveBulkShortcut)
                val added = activity.shortcuts().associate { it.label to it.value }
                assertEquals("three", added["Third"])
                assertEquals("four", added["Fourth"])
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
