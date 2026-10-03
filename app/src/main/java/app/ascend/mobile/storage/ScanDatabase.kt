package app.ascend.mobile.storage

import androidx.room3.Dao
import androidx.room3.ColumnInfo
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

@Entity(tableName = "local_scans")
internal data class ScanRow(
    @PrimaryKey val id: String,
    val owner: String,
    val referenceModel: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val profileSide: String?,
    @ColumnInfo(defaultValue = "0") val deleting: Boolean = false,
)

@Entity(tableName = "local_photos", primaryKeys = ["scanId", "view"])
internal data class PhotoRow(
    val scanId: String,
    val view: String,
    val assetId: String,
    val width: Int,
    val height: Int,
    val origin: String,
    val crop: String,
    val validation: String,
    val reasons: String,
    val qualityPolicyVersion: String,
    val imagePolicyVersion: String,
    val meanLuma: Double,
    val laplacianVariance: Double,
    val profileSide: String?,
)

/** Future local landmarks/results/cache/queues share scan ownership and deletion transaction. */
@Entity(tableName = "scan_payloads", primaryKeys = ["scanId", "kind"])
internal data class ScanPayloadRow(val scanId: String, val kind: String, val payload: ByteArray)

@Dao
internal abstract class ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveScan(row: ScanRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun savePhoto(row: PhotoRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun savePayload(row: ScanPayloadRow)
    @Query("SELECT * FROM scan_payloads WHERE scanId = :id AND kind = :kind") abstract suspend fun payload(id: String, kind: String): ScanPayloadRow?
    @Query("SELECT * FROM local_scans WHERE owner = :owner AND deleting = 0 ORDER BY createdAt DESC") abstract suspend fun list(owner: String): List<ScanRow>
    @Query("SELECT * FROM local_scans WHERE id = :id AND owner = :owner AND deleting = 0") abstract suspend fun get(owner: String, id: String): ScanRow?
    @Query("SELECT * FROM local_scans") abstract suspend fun all(): List<ScanRow>
    @Query("SELECT * FROM local_photos WHERE scanId = :id") abstract suspend fun photos(id: String): List<PhotoRow>
    @Query("SELECT assetId FROM local_photos") abstract suspend fun liveAssetIds(): List<String>
    @Query("DELETE FROM local_photos WHERE scanId = :id AND view = :view") abstract suspend fun removePhoto(id: String, view: String)
    @Query("DELETE FROM local_photos WHERE scanId = :id") abstract suspend fun removePhotos(id: String)
    @Query("DELETE FROM scan_payloads WHERE scanId = :id") abstract suspend fun invalidateDerived(id: String)
    @Query("DELETE FROM local_scans WHERE id = :id") abstract suspend fun removeScan(id: String)
    @Query("UPDATE local_scans SET deleting = 1") abstract suspend fun markAllDeleting()

    @Transaction open suspend fun replaceCapture(scan: ScanRow, photo: PhotoRow) {
        invalidateDerived(scan.id)
        savePhoto(photo)
        saveScan(scan)
    }

    @Transaction open suspend fun retake(scan: ScanRow, view: String) {
        invalidateDerived(scan.id)
        removePhoto(scan.id, view)
        saveScan(scan)
    }

    @Transaction open suspend fun updatePhotoStatus(scan: ScanRow, photo: PhotoRow) {
        savePhoto(photo)
        saveScan(scan)
    }

    @Transaction open suspend fun finishDelete(id: String) {
        invalidateDerived(id)
        removePhotos(id)
        removeScan(id)
    }

    @Transaction open suspend fun complete(scan: ScanRow, payload: ScanPayloadRow) {
        savePayload(payload)
        saveScan(scan)
    }
}

@Database(entities = [ScanRow::class, PhotoRow::class, ScanPayloadRow::class], version = 2, exportSchema = true)
internal abstract class ScanDatabase : RoomDatabase() {
    abstract fun scans(): ScanDao

    companion object {
        /** V1 already had per-view evidence; V2 adds interrupted-delete recovery. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare("ALTER TABLE local_scans ADD COLUMN deleting INTEGER NOT NULL DEFAULT 0").use { it.step() }
            }
        }
    }
}
