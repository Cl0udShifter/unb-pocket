package com.unbpocket.core.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_snapshots")
data class SnapshotEntity(
    @PrimaryKey val cacheKey: String,
    val domain: String,
    val payloadJson: String,
    val origin: String,
    val syncedAtEpochMillis: Long,
)

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM cached_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    fun observeByKey(cacheKey: String): Flow<SnapshotEntity?>

    @Query("SELECT * FROM cached_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): SnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnapshotEntity)

    @Query("DELETE FROM cached_snapshots WHERE domain = :domain")
    suspend fun clearDomain(domain: String)

    @Query("DELETE FROM cached_snapshots WHERE cacheKey = :cacheKey")
    suspend fun clearKey(cacheKey: String)

    @Query("DELETE FROM cached_snapshots")
    suspend fun clearAll()
}

@Database(
    entities = [SnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UnbPocketDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile
        private var instance: UnbPocketDatabase? = null

        fun build(context: Context): UnbPocketDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UnbPocketDatabase::class.java,
                    "unb_pocket_cache.db",
                ).build().also { instance = it }
            }
    }
}
