package com.example.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppPreferences
import com.example.data.local.BhoomgaadiDatabase
import com.example.domain.model.DriveUploadStatus
import java.io.File

class DriveUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        val webhookUrl = prefs.driveWebhookUrl
        val secret = prefs.driveSyncSecret
        val folderId = prefs.driveRootFolderId
        if (webhookUrl.isBlank() || secret.isBlank()) {
            return Result.success()
        }

        val db = BhoomgaadiDatabase.getInstance(applicationContext)
        val billDao = db.billDao()
        val pendingBills = billDao.getPendingUploadBills()
        if (pendingBills.isEmpty()) return Result.success()

        val driveService = DriveService()
        var hadFailure = false

        for (bill in pendingBills) {
            val file = File(bill.pdfFilePath)
            val subFolders = listOf(
                "Bills",
                bill.procurementYear.toString(),
                bill.locationNameSnapshot.ifBlank { "General" },
                bill.operatorName.ifBlank { "FieldOfficer" }
            )
            val uploadResult = driveService.uploadFile(
                webhookUrl = webhookUrl,
                secret = secret,
                rootFolderId = folderId,
                file = file,
                mimeType = "application/pdf",
                subFolders = subFolders
            )
            if (uploadResult.isSuccess) {
                billDao.updateDriveUploadStatus(
                    billUuid = bill.billUuid,
                    status = DriveUploadStatus.UPLOADED,
                    driveFileId = uploadResult.getOrDefault(""),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                hadFailure = true
                billDao.updateDriveUploadStatus(
                    billUuid = bill.billUuid,
                    status = DriveUploadStatus.FAILED,
                    driveFileId = "",
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        return if (hadFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "bhoomgaadi_drive_pdf_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
