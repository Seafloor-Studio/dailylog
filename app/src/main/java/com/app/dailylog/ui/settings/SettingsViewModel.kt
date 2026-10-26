package com.app.dailylog.ui.settings

import android.annotation.SuppressLint
import android.net.Uri
import androidx.lifecycle.*
import com.app.dailylog.repository.RepositoryInterface
import com.app.dailylog.repository.Shortcut
import com.app.dailylog.repository.ShortcutRow
import com.app.dailylog.utils.DetermineBuildInterface
import com.app.dailylog.utils.FileNameTemplate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModelFactory(private var repository: RepositoryInterface,
                               private var build: DetermineBuildInterface,
                               private var showToastOnActivity: (String) -> Unit,
                               private val dispatcher: CoroutineDispatcher = Dispatchers.IO
): ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(repository, build, showToastOnActivity, dispatcher) as T
    }

}

class SettingsViewModel(
    private var repository: RepositoryInterface,
    private var build: DetermineBuildInterface,
    var showToastOnActivity: (String) -> Unit,
    private var dispatcher: CoroutineDispatcher
) : ViewModel() {
    var exportFileUri: Uri? = null

    fun removeCallback(id: String) = viewModelScope.launch(dispatcher) {
        repository.removeShortcut(id)
    }

    fun saveFilename(filename: String) {
        repository.storeFilename(filename)
    }

    fun updateShortcut(id: String, label: String, text: String, cursor: Int, position: Int, type:String) = viewModelScope.launch(dispatcher) {
        repository.updateShortcut(id, label, text, cursor, position, type)
    }

    fun addShortcuts(rows: List<ShortcutRow>) = viewModelScope.launch(dispatcher) {
        repository.addShortcuts(rows)
    }

    fun addShortcut(label: String, text: String, cursor: Int, type: String) = viewModelScope.launch(dispatcher) {
        repository.addShortcut(label, text, cursor, type)
    }

    fun getEditorTextSize(): Int? = repository.getEditorTextSize()

    fun setEditorTextSize(size: Int?) = repository.setEditorTextSize(size)

    fun getFilename(): String {
        return repository.retrieveFilename()
    }

    fun isDatedMode(): Boolean = repository.isDatedMode()

    fun useOneFile() = repository.setDatedMode(false)

    /** Returns false when no folder has been chosen yet, so the caller should ask for one. */
    fun useDatedFiles(): Boolean {
        if (repository.retrieveLogFolder() == null) return false
        repository.setDatedMode(true)
        return true
    }

    fun chooseLogFolder(folder: Uri) {
        repository.storeLogFolder(folder)
        repository.setDatedMode(true)
    }

    fun getLogFolder(): Uri? = repository.retrieveLogFolder()

    fun getLogFolderName(): String? = repository.retrieveLogFolderName()

    fun getFileNameTemplate(): String = repository.retrieveFileNameTemplate()

    /** Saves [template] when it can name a file, otherwise returns why it can't. */
    @SuppressLint("NewApi")
    fun setFileNameTemplate(template: String): String? {
        val error = FileNameTemplate.error(template)
        if (error == null) repository.storeFileNameTemplate(template.trim())
        return error
    }

    fun getAllShortcuts(): LiveData<List<Shortcut>> {
        return repository.getAllShortcuts()
    }

    fun labelExists(label: String): LiveData<Boolean> {
        return repository.labelExists(label)
    }

    fun updateShortcutPositions(shortcuts: List<Shortcut>) = viewModelScope.launch(dispatcher) {
        repository.updateShortcutPositions(shortcuts)
    }

    // Suppress NewApi lint here because we know we're checking for the right build
    @SuppressLint("NewApi")
    fun exportShortcuts(): Error? {
        if (this.exportFileUri == null) {
            return Error("No export file selected")
        }
        if (build.isOreoOrGreater()) {
            try {
                this.exportFileUri?.let { repository.exportShortcutsAsJson(it) }
            } catch(ex: Exception) {
                // printStackTrace() returns Unit, so this message used to read "Error:
                // kotlin.Unit". message is null for some exceptions, hence the class name as a
                // fallback: a type name is still something to search for, "null" is not.
                return Error("Error: ${ex.message ?: ex.javaClass.simpleName}")
            }
        } else {
            return Error("Need OS of Oreo or greater to export to JSON")
        }
        return null
    }

    // Suppress NewApi lint here because we know we're checking for the right build
    @SuppressLint("NewApi") fun importShortcutsLegacy(uri: Uri) = viewModelScope.launch(dispatcher) {
        if (build.isOreoOrGreater()) {
            try {
                repository.importShortcuts(uri)
            } catch(ex: java.lang.Exception) {
                ex.message?.let { showToastOnActivity(it) }
            }
        } else {
            showToastOnActivity("Need OS of Oreo or greater to import from CSV")
        }
    }

    // Suppress NewApi lint here because we know we're checking for the right build
    @SuppressLint("NewApi") fun importShortcuts(uri: Uri) = viewModelScope.launch(dispatcher) {
        if (build.isOreoOrGreater()) {
            try {
                repository.importShortcutsFromJson(uri)
            } catch(ex: java.lang.Exception) {
                ex.message?.let { showToastOnActivity(it) }
            }
        } else {
            showToastOnActivity("Need OS of Oreo or greater to import from CSV")
        }
    }

    fun createShortcutDialogViewModel(): ShortcutDialogViewModel {
        return ShortcutDialogViewModel(repository)
    }
}