package com.app.dailylog.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import com.app.dailylog.R
import com.app.dailylog.utils.JsonShortcutUtils
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.*
import java.lang.Exception
import java.math.BigInteger
import java.security.MessageDigest



interface RepositoryInterface: FileRepositoryInterface, ShortcutRepositoryInterface {
    fun getCursorIndex(): Int
    fun setCursorIndex(index: Int)
    /** The log editor's text size in dp, or null to follow the phone's font size. */
    fun getEditorTextSize(): Int?
    fun setEditorTextSize(size: Int?)
    fun getAllShortcuts(): LiveData<List<Shortcut>>
    fun labelExists(label: String): LiveData<Boolean>

    @RequiresApi(Build.VERSION_CODES.O)
    fun exportShortcuts(uri: Uri)

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun importShortcuts(uri: Uri)

    @RequiresApi(Build.VERSION_CODES.O)
    fun exportShortcutsAsJson(uri: Uri)

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun importShortcutsFromJson(uri: Uri)
}

class Repository(override val context: Context): RepositoryInterface {
    override lateinit var filename : String
    override var lastSavedContentsHash: String = ""
    override var lastReadFailed: Boolean = false
    override var datedFilePath: String? = null
    override var shortcutDao = ShortcutDatabase.getDatabase(context).shortcutDao()
    override var shortcutLiveData: LiveData<List<Shortcut>> = shortcutDao.getAll()

    init {
        initializeFilename()
    }

    private fun getDatabaseSchemaVersion(): Int {
        // Get the schema version directly from the @Database annotation
        return ShortcutDatabase::class.java.getAnnotation(androidx.room.Database::class.java)?.version ?: 4
    }

    override fun getCursorIndex(): Int {
        val preferences =
            context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
        return preferences.getInt(
            Constants.CURSOR_KEY,
            Constants.DEFAULT_CURSOR_INDEX
        )
    }

    override fun setCursorIndex(index: Int) {
        val preferences =
            context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
        val editor = preferences.edit()
        editor.putInt(Constants.CURSOR_KEY, index)
        editor.apply()
    }

    override fun getEditorTextSize(): Int? {
        val preferences =
            context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
        return if (preferences.contains(Constants.EDITOR_TEXT_SIZE_KEY)) {
            preferences.getInt(Constants.EDITOR_TEXT_SIZE_KEY, 0)
        } else {
            null
        }
    }

    override fun setEditorTextSize(size: Int?) {
        val preferences =
            context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )
        val editor = preferences.edit()
        if (size == null) {
            editor.remove(Constants.EDITOR_TEXT_SIZE_KEY)
        } else {
            editor.putInt(Constants.EDITOR_TEXT_SIZE_KEY, size)
        }
        editor.apply()
    }

    override fun getAllShortcuts(): LiveData<List<Shortcut>> {
        return shortcutLiveData
    }

    override fun labelExists(label: String): LiveData<Boolean> {
        return shortcutDao.labelExists(label)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun exportShortcuts(uri: Uri) {
        shortcutLiveData.value?.let { exportShortcuts(uri, getExportRows()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun importShortcuts(uri: Uri) {
        val results = importShortcutValuesFromCSV(uri)
        if (results != null) {
            bulkAddShortcuts(shortcutInfoList = results)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun exportShortcutsAsJson(uri: Uri) {
        shortcutLiveData.value?.let { shortcuts ->
            val schemaVersion = getDatabaseSchemaVersion()
            val jsonContent = JsonShortcutUtils.exportShortcutsAsJson(shortcuts, schemaVersion)
            exportShortcutsAsJson(uri, jsonContent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun importShortcutsFromJson(uri: Uri) {
            val jsonContent = readJsonFile(uri)
            val shortcuts = JsonShortcutUtils.importShortcutsFromJson(jsonContent)
            
            if (shortcuts != null) {
                // Add all shortcuts to the database
                bulkAddShortcuts(shortcuts.map { 
                    arrayOf(it.label, it.value, it.cursorIndex.toString(), it.type) 
                })
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportShortcutsAsJson(uri: Uri, jsonContent: String) {
        try {
            val openFileDescriptor = context.contentResolver.openFileDescriptor(uri, "rwt")
            val fileDescriptor = openFileDescriptor?.fileDescriptor
            val fileStream = FileOutputStream(fileDescriptor)
            fileStream.write(jsonContent.toByteArray())
            fileStream.close()
            openFileDescriptor?.close()
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun readJsonFile(uri: Uri): String {
        val openFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        val fileDescriptor = openFileDescriptor?.fileDescriptor
        val fileStream = FileInputStream(fileDescriptor)
        
        val reader = BufferedReader(InputStreamReader(fileStream))
        val content = StringBuilder()
        var line: String?
        
        while (reader.readLine().also { line = it } != null) {
            content.append(line).append("\n")
        }
        
        reader.close()
        fileStream.close()
        openFileDescriptor?.close()
        
        return content.toString()
    }
}