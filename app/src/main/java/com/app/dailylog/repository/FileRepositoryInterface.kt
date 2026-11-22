package com.app.dailylog.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.app.dailylog.R
import com.app.dailylog.utils.FileNameTemplate
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.*
import java.lang.Exception
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Clock



interface FileRepositoryInterface {
    /** URI of the file being edited, or [Constants.FILE_NOT_CREATED] for a dated file not saved yet. */
    var filename: String
    val context: Context
    var lastSavedContentsHash: String
    /**
     * True when the last read of [filename] failed. The editor then shows nothing, so a save would
     * replace the whole file with whatever was typed over that blank.
     */
    var lastReadFailed: Boolean
    /** In dated mode, the path of the file being edited inside the log folder. */
    var datedFilePath: String?

    private val preferences
        get() = context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )

    fun initializeFilename() {
        filename = retrieveFilename()
    }

    fun userMustSelectFile(): Boolean {
        return !isDatedMode() && retrieveFilename() == Constants.NO_FILE_SELECTED
    }

    fun retrieveFilename(): String {
        return preferences.getString(Constants.FILENAME_PREF_KEY, Constants.NO_FILE_SELECTED)
            ?: Constants.NO_FILE_SELECTED
    }

    // Date templates need java.time, so older phones always use one file.
    fun isDatedMode(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            preferences.getString(Constants.FILE_MODE_PREF_KEY, null) == Constants.FILE_MODE_DATED &&
            retrieveLogFolder() != null

    fun setDatedMode(dated: Boolean) {
        preferences.edit()
            .putString(Constants.FILE_MODE_PREF_KEY, if (dated) Constants.FILE_MODE_DATED else null)
            .apply()
    }

    fun retrieveLogFolder(): Uri? =
        preferences.getString(Constants.LOG_FOLDER_PREF_KEY, null)?.let { Uri.parse(it) }

    /** Null when no folder is chosen, or the app can no longer reach it. */
    fun retrieveLogFolderName(): String? = retrieveLogFolder()?.let { LogFolder(context, it).name }

    fun storeLogFolder(folder: Uri) {
        preferences.edit().putString(Constants.LOG_FOLDER_PREF_KEY, folder.toString()).apply()
    }

    fun retrieveFileNameTemplate(): String =
        preferences.getString(Constants.FILE_NAME_TEMPLATE_PREF_KEY, null) ?: FileNameTemplate.DEFAULT

    fun storeFileNameTemplate(template: String) {
        preferences.edit().putString(Constants.FILE_NAME_TEMPLATE_PREF_KEY, template).apply()
    }

    /**
     * Points [filename] at the file the settings call for now: the chosen file, or today's file in
     * the log folder. Returns true when that is a different file from last time, in which case the
     * saved cursor position belonged to the old file and is reset.
     */
    fun openCurrentFile(clock: Clock? = null): Boolean {
        val folder = retrieveLogFolder()
        val key: String
        if (isDatedMode() && folder != null) {
            val path = FileNameTemplate.resolve(retrieveFileNameTemplate(), clock)
            filename = LogFolder(context, folder).find(path)?.toString() ?: Constants.FILE_NOT_CREATED
            datedFilePath = path
            key = "$folder/$path"
        } else {
            filename = retrieveFilename()
            datedFilePath = null
            key = filename
        }
        val previous = preferences.getString(Constants.OPEN_FILE_PREF_KEY, null)
        if (previous == key) return false
        // Before this pref existed only the chosen file was ever open, so its saved cursor still applies.
        val changed = previous != null || datedFilePath != null
        val editor = preferences.edit().putString(Constants.OPEN_FILE_PREF_KEY, key)
        if (changed) editor.putInt(Constants.CURSOR_KEY, Constants.DEFAULT_CURSOR_INDEX)
        editor.apply()
        return changed
    }

    fun storeFilename(filename: String) {
        val preferences =
            context.getSharedPreferences(
                context.getString(
                    R.string.preference_file_key
                ), Context.MODE_PRIVATE
            )
        val editor = preferences.edit()
        editor.putString(Constants.FILENAME_PREF_KEY, filename)
        editor.apply()
        this.filename = filename
    }

    fun readFile(firstTime: Boolean): String {
        if (filename == Constants.FILE_NOT_CREATED) {
            if (firstTime) updateLastSavedHash("")
            lastReadFailed = false
            return ""
        }
        try {
            val stringBuilder = StringBuilder()
            val uri = Uri.parse(filename)
            // A provider may hand back no stream rather than throw; that's no more a read than an error.
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("No stream for $uri")
            stream.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        stringBuilder.append(System.lineSeparator())
                        line = reader.readLine()
                    }
                }
                inputStream.close()
            }
            val fileData = stringBuilder.toString()
            if (firstTime) {
                updateLastSavedHash(fileData)
            }
            lastReadFailed = false
            return fileData
        } catch (ex: Exception) {
            lastReadFailed = true
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG)
                .show()
        }
        return ""
    }

    private fun md5(data: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = BigInteger(1, md.digest(data.toByteArray())).toString(16)
        return hash
    }

    private fun updateLastSavedHash(data: String) {
        lastSavedContentsHash = md5(data)
    }

    private fun shouldSave(data: String): Boolean {
        return md5(data) != lastSavedContentsHash
    }

    fun saveToFile(data: String, overrideSmartSave: Boolean): Boolean {
        if (!overrideSmartSave && !shouldSave(data)) {
            return false
        }
        return try {
            var contents = data
            if (filename == Constants.FILE_NOT_CREATED) {
                val folder = retrieveLogFolder()?.let { LogFolder(context, it) }
                val path = datedFilePath
                // The file can appear after the log opened it as new, e.g. from a sync app. Keep
                // what's in it rather than overwriting it with what was typed here.
                val existing = path?.let { folder?.find(it) }
                if (existing != null) {
                    filename = existing.toString()
                    contents = readFile(false) + data
                } else {
                    val created = path?.let { folder?.create(it) }
                    if (created == null) {
                        Toast.makeText(context, "Could not create $path", Toast.LENGTH_LONG).show()
                        return false
                    }
                    filename = created.toString()
                }
            }
            if (lastReadFailed) {
                Toast.makeText(context, R.string.log_not_read, Toast.LENGTH_LONG).show()
                return false
            }
            val uri = Uri.parse(filename)
            val openFileDescriptor = context.contentResolver.openFileDescriptor(uri, "rwt")
            val fileDescriptor = openFileDescriptor?.fileDescriptor
            val fileStream = FileOutputStream(fileDescriptor)
            fileStream.write((contents).toByteArray())
            fileStream.close()
            openFileDescriptor?.close()
            updateLastSavedHash(contents)
            true
        } catch (ex: IllegalArgumentException) {
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show()
            false
        } catch (ex: Exception) {
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun exportShortcuts(uri: Uri, rows: List<List<String>>): Boolean {
        return try {
            val openFileDescriptor = context.contentResolver.openFileDescriptor(uri, "rwt")
            val fileDescriptor = openFileDescriptor?.fileDescriptor
            val fileStream = FileOutputStream(fileDescriptor)
            val writer = CSVWriter(OutputStreamWriter(fileStream))
            for (row in rows) {
                writer.writeNext(row.toTypedArray());
            }
            writer.close();
            fileStream.close()
            openFileDescriptor?.close()
            true
        } catch (ex: IllegalArgumentException) {
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show()
            false
        } catch (ex: Exception) {
            print(ex.stackTrace)
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show()
            false
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun importShortcutValuesFromCSV(uri: Uri): List<Array<String>>? {
        var results: List<Array<String>>? = null
        return try {
            val openFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val fileDescriptor = openFileDescriptor?.fileDescriptor
            val fileStream = FileInputStream(fileDescriptor)
            val reader = CSVReader(InputStreamReader(fileStream))
            results = reader.readAll()
            reader.close()
            fileStream.close()
            openFileDescriptor?.close()
            results
        } catch (ex: IllegalArgumentException) {
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show()
            null
        } catch (ex: Exception) {
            print(ex.stackTrace)
            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show()
            null
        }
    }
}