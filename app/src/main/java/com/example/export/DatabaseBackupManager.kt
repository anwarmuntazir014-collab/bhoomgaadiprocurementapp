package com.example.export

import android.content.Context
import android.net.Uri
import com.example.data.local.BhoomgaadiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseBackupManager(
    private val context: Context,
    private val dbProvider: () -> BhoomgaadiDatabase
) {
    private val timestampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun createLocalBackupFile(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Flush WAL so single .db file contains all committed transactions
            val writableDb = dbProvider().openHelper.writableDatabase
            writableDb.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val dbFile = context.getDatabasePath(BhoomgaadiDatabase.DB_NAME)
            if (!dbFile.exists()) throw IllegalStateException("SQLite database file not found.")

            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val outFile = File(backupDir, "Bhoomgaadi_Backup_${timestampFmt.format(Date())}.db")
            dbFile.copyTo(outFile, overwrite = true)
            outFile
        }
    }

    suspend fun exportBackupToUri(destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val backupFile = createLocalBackupFile().getOrThrow()
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                backupFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Could not open selected output destination.")
            Unit
        }
    }

    suspend fun restoreFromUri(sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(context.cacheDir, "restore_candidate.db")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to read selected backup file.")

            // Verify SQLite magic header ("SQLite format 3\u0000")
            val headerBytes = ByteArray(16)
            tempFile.inputStream().use { it.read(headerBytes) }
            val headerString = String(headerBytes, Charsets.US_ASCII)
            if (!headerString.startsWith("SQLite format 3")) {
                tempFile.delete()
                throw IllegalArgumentException("Selected file is not a valid SQLite database backup.")
            }

            BhoomgaadiDatabase.closeAndClearInstance()

            val dbFile = context.getDatabasePath(BhoomgaadiDatabase.DB_NAME)
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            // Re-open to verify integrity
            BhoomgaadiDatabase.getInstance(context).openHelper.readableDatabase
            Unit
        }
    }
}
