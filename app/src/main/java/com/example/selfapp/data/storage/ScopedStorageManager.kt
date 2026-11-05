package com.example.selfapp.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScopedStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Copies a single file Uri to internal storage.
     * Returns the absolute path of the local copied file.
     */
    fun copyFileToLocal(sourceUri: Uri, questionId: Long, timestamp: Long, originalName: String): String {
        val destDir = File(context.filesDir, "submissions/q_$questionId/s_$timestamp").apply {
            if (!exists()) mkdirs()
        }
        val destFile = File(destDir, originalName)

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return destFile.absolutePath
    }

    /**
     * Recursively copies a Document Tree Uri (folder) to internal storage.
     * Returns a list of absolute paths of the local copied files.
     */
    fun copyDirectoryToLocal(treeUri: Uri, questionId: Long, timestamp: Long): List<String> {
        val destDir = File(context.filesDir, "submissions/q_$questionId/s_$timestamp").apply {
            if (!exists()) mkdirs()
        }
        val copiedPaths = mutableListOf<String>()

        val rootDocument = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        copyDocumentTree(rootDocument, destDir, copiedPaths)

        return copiedPaths
    }

    private fun copyDocumentTree(document: DocumentFile, destDir: File, copiedPaths: MutableList<String>) {
        if (document.isDirectory) {
            val subDir = File(destDir, document.name ?: "folder").apply { if (!exists()) mkdirs() }
            for (file in document.listFiles()) {
                copyDocumentTree(file, subDir, copiedPaths)
            }
        } else {
            val destFile = File(destDir, document.name ?: "file")
            try {
                context.contentResolver.openInputStream(document.uri)?.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                copiedPaths.add(destFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reads content of a local file as text.
     */
    fun readFileContent(filePath: String): String {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                "File not found: $filePath"
            }
        } catch (e: Exception) {
            "Error reading file $filePath: ${e.message}"
        }
    }
}
