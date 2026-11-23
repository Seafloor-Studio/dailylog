package com.app.dailylog.testutil

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import org.robolectric.Robolectric
import java.io.File

/**
 * Serves a plain directory the way a documents provider serves a picked folder.
 * Robolectric calls the query overload that [android.provider.DocumentsProvider] makes final and
 * unsupported, so this answers the document URIs DocumentFile uses directly.
 */
class TestDocumentsProvider : ContentProvider() {

    override fun onCreate() = true

    private fun fileFor(documentId: String) = if (documentId == ROOT_ID) root!! else File(root, documentId)

    private fun idFor(file: File) = file.relativeTo(root!!).path.ifEmpty { ROOT_ID }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        val file = fileFor(DocumentsContract.getDocumentId(uri))
        if (uri.lastPathSegment == "children") {
            file.listFiles()?.sortedBy { it.name }?.forEach { addRow(cursor, it) }
        } else if (file.exists()) {
            addRow(cursor, file)
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_CREATE_DOCUMENT || extras == null) return super.call(method, arg, extras)
        @Suppress("DEPRECATION")
        val parentUri = extras.getParcelable<Uri>(EXTRA_URI)!!
        val mimeType = extras.getString(Document.COLUMN_MIME_TYPE)!!
        val file = File(fileFor(DocumentsContract.getDocumentId(parentUri)), extras.getString(Document.COLUMN_DISPLAY_NAME)!!)
        if (mimeType == Document.MIME_TYPE_DIR) file.mkdirs() else file.createNewFile()
        createdMimeTypes[idFor(file)] = mimeType
        val treeId = DocumentsContract.getTreeDocumentId(parentUri)
        val created = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(AUTHORITY, treeId), idFor(file)
        )
        return Bundle().apply { putParcelable(EXTRA_URI, created) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode == "r" && failReads) throw java.io.FileNotFoundException("provider unavailable")
        return ParcelFileDescriptor.open(fileFor(DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun addRow(cursor: MatrixCursor, file: File) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, idFor(file))
        row.add(Document.COLUMN_DISPLAY_NAME, file.name)
        row.add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "text/plain")
        row.add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_WRITE)
        row.add(Document.COLUMN_SIZE, file.length())
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    companion object {
        const val AUTHORITY = "com.app.dailylog.test.documents"
        private const val ROOT_ID = "root"
        // DocumentsContract.EXTRA_URI and METHOD_CREATE_DOCUMENT are hidden from the SDK.
        private const val EXTRA_URI = "uri"
        private const val METHOD_CREATE_DOCUMENT = "android:createDocument"
        private val DEFAULT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )

        private var root: File? = null
        val createdMimeTypes = mutableMapOf<String, String>()
        /** Makes opening a document for reading fail, as a cloud-backed provider can. */
        var failReads = false

        /** Registers the provider over [directory] and returns the tree URI a folder pick would give. */
        fun install(context: Context, directory: File): Uri {
            root = directory.also { it.mkdirs() }
            createdMimeTypes.clear()
            failReads = false
            Robolectric.buildContentProvider(TestDocumentsProvider::class.java).create(AUTHORITY)
            return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
        }
    }
}
