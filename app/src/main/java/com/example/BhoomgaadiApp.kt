package com.example

import android.app.Application
import com.example.data.local.AppPreferences
import com.example.data.local.BhoomgaadiDatabase
import com.example.data.repository.ProcurementRepository
import com.example.export.CsvExportManager
import com.example.export.DatabaseBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BhoomgaadiApp : Application() {
    lateinit var appPreferences: AppPreferences
        private set

    lateinit var repository: ProcurementRepository
        private set

    lateinit var csvExportManager: CsvExportManager
        private set

    lateinit var backupManager: DatabaseBackupManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(this)
        val dbProvider: () -> BhoomgaadiDatabase = { BhoomgaadiDatabase.getInstance(this) }
        repository = ProcurementRepository(this, dbProvider, appPreferences)
        csvExportManager = CsvExportManager(this, dbProvider)
        backupManager = DatabaseBackupManager(this, dbProvider)

        // Warm up DB and ensure master catalog is seeded on first launch
        appScope.launch {
            val db = BhoomgaadiDatabase.getInstance(this@BhoomgaadiApp)
            db.openHelper.writableDatabase
            BhoomgaadiDatabase.seedReferenceMasters(db.masterDao())
        }
    }
}
