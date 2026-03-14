package com.lotus.lapiswifimanager.helpers

import android.content.Context
import android.os.Environment
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/*** VERWENDUNG

        // Logging auf verschiedenen Ebenen
        Timber.v("Verbose Log-Nachricht")
        Timber.d("Debug Log-Nachricht")
        Timber.i("Info Log-Nachricht")
        Timber.w("Warning Log-Nachricht")
        Timber.e("Error Log-Nachricht")
        Timber.tag(TAG).e("FEHLER")

        // Logging mit Exception
        try {
        // eine Operation
        } catch (e: Exception) {
        Timber.e(e, "Ein Fehler ist aufgetreten")
        }

 */


class FileLoggingTree(private val context: Context) : Timber.Tree() {

    private val lock = ReentrantLock()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    companion object {
        private const val LOG_DIR_NAME = "AppLogs"
        private const val LOG_FILE_PREFIX = "app_log"
        private const val LOG_FILE_EXTENSION = ".txt"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        lock.withLock {
            try {
                val logFile = getCurrentLogFile()
                if (logFile.length() > MAX_LOG_FILE_SIZE) {
                    rotateLogFile()
                }

                writeLogToFile(priority, tag, message, t)
            } catch (e: Exception) {
                // Einfacher Log ohne Timber
                Timber.tag("FileLoggingTree").e("Fehler beim Schreiben des Logs: ${e.message}")
            }
        }
    }

    private fun getCurrentLogFile(): File {
        val logDir = getLogDirectory()
        val currentDate = dateFormat.format(Date())
        val fileName = "$LOG_FILE_PREFIX$currentDate$LOG_FILE_EXTENSION"
        return File(logDir, fileName)
    }

    private fun getLogDirectory(): File {
        return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOG_DIR_NAME)
        } else {
            File(context.filesDir, LOG_DIR_NAME)
        }.apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun writeLogToFile(priority: Int, tag: String?, message: String, t: Throwable?) {
        val logFile = getCurrentLogFile()
        val timestamp = timeFormat.format(Date())
        val priorityStr = getPriorityString(priority)

        FileOutputStream(logFile, true).use { fos ->
            PrintWriter(fos).use { writer ->
                writer.println("$timestamp $priorityStr/${tag ?: "NO_TAG"}: $message")

                t?.let { throwable ->
                    writer.println("$timestamp $priorityStr/${tag ?: "NO_TAG"}: Stacktrace:")
                    throwable.printStackTrace(writer)
                }

                writer.flush()
            }
        }
    }

    private fun rotateLogFile() {
        val currentFile = getCurrentLogFile()
        if (currentFile.exists()) {
            val timestamp = SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(Date())
            val rotatedName = "${currentFile.nameWithoutExtension}_$timestamp${currentFile.extension}"
            val rotatedFile = File(currentFile.parent, rotatedName)
            currentFile.renameTo(rotatedFile)
        }
    }

    private fun getPriorityString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }

    fun getLogFiles(): List<File> {
        return getLogDirectory().listFiles()?.toList() ?: emptyList()
    }

    fun clearOldLogs(days: Int = 7) {
        val logDir = getLogDirectory()
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)

        logDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }
}