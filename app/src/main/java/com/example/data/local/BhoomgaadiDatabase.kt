package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.BillDao
import com.example.data.local.dao.DashboardDao
import com.example.data.local.dao.FarmerDao
import com.example.data.local.dao.MasterDao
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.BillHeaderEntity
import com.example.data.local.entities.BillItemEntity
import com.example.data.local.entities.FarmerEntity
import com.example.data.local.entities.GodownEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.local.entities.ProductEntity
import com.example.domain.model.RecordStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

@Database(
    entities = [
        FarmerEntity::class,
        ProductEntity::class,
        LocationEntity::class,
        GodownEntity::class,
        BillHeaderEntity::class,
        BillItemEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BhoomgaadiDatabase : RoomDatabase() {
    abstract fun farmerDao(): FarmerDao
    abstract fun masterDao(): MasterDao
    abstract fun billDao(): BillDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DB_NAME = "bhoomgaadi_procurement.db"

        @Volatile
        private var INSTANCE: BhoomgaadiDatabase? = null

        fun getInstance(context: Context): BhoomgaadiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun closeAndClearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(appContext: Context): BhoomgaadiDatabase {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return Room.databaseBuilder(
                appContext,
                BhoomgaadiDatabase::class.java,
                DB_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch {
                            seedReferenceMasters(getInstance(appContext).masterDao())
                        }
                    }
                })
                .build()
        }

        /**
         * Seeds standard Bhoomgaadi organic produce catalog, procurement centers, and temporary
         * godowns on first database creation so field billing dropdowns work immediately offline.
         */
        suspend fun seedReferenceMasters(masterDao: MasterDao) {
            val now = System.currentTimeMillis()
            val year = Calendar.getInstance().get(Calendar.YEAR)

            val defaultProducts = listOf(
                ProductEntity("prod_kodo", "Organic Kodo Millet (Raw)", 38.0, year, 1, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_kutki", "Organic Kutki Millet (Raw)", 44.0, year, 2, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_ragi", "Organic Finger Millet (Ragi)", 42.0, year, 3, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_jawaphool", "Jawaphool Aromatic Paddy", 36.0, year, 4, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_black_rice", "Organic Black Rice Paddy", 65.0, year, 5, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_mahua", "Dried Mahua Flower (Food Grade)", 52.0, year, 6, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_tamarind", "Desi Tamarind (Deseeded)", 72.0, year, 7, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_cashew", "Raw Cashew Nut (Bastar)", 135.0, year, 8, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_amchur", "Sun-Dried Amchur Slices", 140.0, year, 9, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_turmeric", "Organic Whole Turmeric Bulb", 95.0, year, 10, RecordStatus.ACTIVE, now, now)
            )
            masterDao.insertProducts(defaultProducts)

            val defaultLocations = listOf(
                LocationEntity("loc_dantewada", "Dantewada Main Center", RecordStatus.ACTIVE, now, now),
                LocationEntity("loc_geedam", "Geedam Cluster Hub", RecordStatus.ACTIVE, now, now),
                LocationEntity("loc_kuakonda", "Kuakonda Haat Bazaar", RecordStatus.ACTIVE, now, now),
                LocationEntity("loc_katekalyan", "Katekalyan Collection Center", RecordStatus.ACTIVE, now, now)
            )
            masterDao.insertLocations(defaultLocations)

            val defaultGodowns = listOf(
                GodownEntity("gdw_dtw_01", "loc_dantewada", "Dantewada Central Godown A", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_dtw_02", "loc_dantewada", "Dantewada Millet Processing Shed", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_gdm_01", "loc_geedam", "Geedam SHG Storage Hall", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_gdm_02", "loc_geedam", "Geedam Temp Transit Godown", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_kkn_01", "loc_kuakonda", "Kuakonda Panchayat Godown", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_ktk_01", "loc_katekalyan", "Katekalyan Primary Shed", RecordStatus.ACTIVE, now, now)
            )
            masterDao.insertGodowns(defaultGodowns)
        }
    }
}
