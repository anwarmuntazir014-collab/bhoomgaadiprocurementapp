package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*
import com.example.domain.model.RecordStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

@Database(
    entities = [
        UserEntity::class,
        BlockEntity::class,
        ClusterEntity::class,
        VillageEntity::class,
        FarmerEntity::class,
        ProductEntity::class,
        LocationEntity::class,
        GodownEntity::class,
        BillHeaderEntity::class,
        BillItemEntity::class,
        AuditLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BhoomgaadiDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun geoDao(): GeoDao
    abstract fun farmerDao(): FarmerDao
    abstract fun masterDao(): MasterDao
    abstract fun billDao(): BillDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DB_NAME = "bhoomgaadi_procurement.db"

        @Volatile
        private var INSTANCE: BhoomgaadiDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Users (
                        userUuid TEXT NOT NULL PRIMARY KEY,
                        username TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        role TEXT NOT NULL,
                        pinSalt TEXT NOT NULL,
                        pinHash TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Users_username ON Users (username)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Blocks (
                        blockId TEXT NOT NULL PRIMARY KEY,
                        blockName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Blocks_blockName ON Blocks (blockName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Clusters (
                        clusterId TEXT NOT NULL PRIMARY KEY,
                        blockId TEXT NOT NULL,
                        clusterName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(blockId) REFERENCES Blocks(blockId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Clusters_blockId ON Clusters (blockId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Clusters_blockId_clusterName ON Clusters (blockId, clusterName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Villages (
                        villageId TEXT NOT NULL PRIMARY KEY,
                        clusterId TEXT NOT NULL,
                        villageName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(clusterId) REFERENCES Clusters(clusterId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Villages_clusterId ON Villages (clusterId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Villages_clusterId_villageName ON Villages (clusterId, villageName)")

                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN procurementAmountInr REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN membershipEnrolled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN membershipAmountInr REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN revisionNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN lastEditedAtMillis INTEGER")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN lastEditedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN lastEditReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE BillHeaders ADD COLUMN publicPdfDisplayPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE BillHeaders SET procurementAmountInr = totalAmountInr WHERE procurementAmountInr = 0.0")

                db.execSQL("ALTER TABLE BillItems ADD COLUMN weightPerBagKg REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Farmers ADD COLUMN farmerPhotoPath TEXT NOT NULL DEFAULT ''")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch {
                            val instance = getInstance(appContext)
                            seedReferenceMasters(instance.masterDao(), instance.geoDao())
                        }
                    }
                })
                .build()
        }

        suspend fun seedReferenceMasters(masterDao: MasterDao, geoDao: GeoDao) {
            val now = System.currentTimeMillis()
            val year = Calendar.getInstance().get(Calendar.YEAR)

            val defaultProducts = listOf(
                ProductEntity("prod_little_millet", "Little Millet", 36.0, year, 1, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_kodo", "Kodo Millet", 35.0, year, 2, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_ragi", "Finger Millet (Ragi)", 42.0, year, 3, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_paddy", "Jawaphool Aromatic Paddy", 36.0, year, 4, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_black_rice", "Organic Black Rice Paddy", 65.0, year, 5, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_mahua", "Dried Mahua Flower", 52.0, year, 6, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_tamarind", "Desi Tamarind (Deseeded)", 72.0, year, 7, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_cashew", "Raw Cashew Nut", 135.0, year, 8, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_maize", "Desi Maize", 28.0, year, 9, RecordStatus.ACTIVE, now, now),
                ProductEntity("prod_turmeric", "Organic Whole Turmeric", 95.0, year, 10, RecordStatus.ACTIVE, now, now)
            )
            masterDao.insertProducts(defaultProducts)

            val defaultLocations = listOf(
                LocationEntity("loc_katekalyan", "Katekalyan Haat", RecordStatus.ACTIVE, now, now),
                LocationEntity("loc_dantewada", "Dantewada Main Center", RecordStatus.ACTIVE, now, now),
                LocationEntity("loc_geedam", "Geedam Cluster Hub", RecordStatus.ACTIVE, now, now),
                LocationEntity("loc_kuakonda", "Kuakonda Haat Bazaar", RecordStatus.ACTIVE, now, now)
            )
            masterDao.insertLocations(defaultLocations)

            val defaultGodowns = listOf(
                GodownEntity("gdw_ktk_01", "loc_katekalyan", "Muchnar TG-01", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_ktk_02", "loc_katekalyan", "Katekalyan Primary Shed", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_dtw_01", "loc_dantewada", "Dantewada Central Godown A", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_dtw_02", "loc_dantewada", "Dantewada Millet Shed", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_gdm_01", "loc_geedam", "Geedam SHG Storage Hall", RecordStatus.ACTIVE, now, now),
                GodownEntity("gdw_kkn_01", "loc_kuakonda", "Kuakonda Panchayat Godown", RecordStatus.ACTIVE, now, now)
            )
            masterDao.insertGodowns(defaultGodowns)

            // Seed default Block -> Cluster -> Village cascade (extended automatically on Excel import)
            val defaultBlocks = listOf(
                BlockEntity("blk_katekalyan", "Katekalyan", now),
                BlockEntity("blk_dantewada", "Dantewada", now),
                BlockEntity("blk_geedam", "Geedam", now),
                BlockEntity("blk_kuakonda", "Kuakonda", now)
            )
            geoDao.insertBlocks(defaultBlocks)

            val defaultClusters = listOf(
                ClusterEntity("cls_ktk_1", "blk_katekalyan", "Cluster 1", now),
                ClusterEntity("cls_ktk_2", "blk_katekalyan", "Cluster 2", now),
                ClusterEntity("cls_dtw_1", "blk_dantewada", "Dantewada Central", now),
                ClusterEntity("cls_gdm_1", "blk_geedam", "Geedam North", now),
                ClusterEntity("cls_kkn_1", "blk_kuakonda", "Kuakonda East", now)
            )
            geoDao.insertClusters(defaultClusters)

            val defaultVillages = listOf(
                VillageEntity("vil_muchnar", "cls_ktk_1", "Muchnar", now),
                VillageEntity("vil_gatam", "cls_ktk_1", "Gatam", now),
                VillageEntity("vil_parcheli", "cls_ktk_2", "Parcheli", now),
                VillageEntity("vil_kumharas", "cls_dtw_1", "Kumharras", now),
                VillageEntity("vil_jawanga", "cls_gdm_1", "Jawanga", now),
                VillageEntity("vil_nakulnar", "cls_kkn_1", "Nakulnar", now)
            )
            geoDao.insertVillages(defaultVillages)
        }
    }
}
