package com.binancealert.app.data

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_coins")
data class WatchCoin(
    @PrimaryKey val symbol: String,
    val addedAt: Long,
    val favorite: Boolean = false
)

/** status: ACTIVE, INACTIVE (paused) or HIT (triggered, kept as history). */
@Entity(tableName = "alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val targetPrice: Double,
    val status: String,
    val createdAt: Long,
    val hitPrice: Double? = null,
    val hitAt: Long? = null,
    val direction: String? = null,
    val note: String = ""
)

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val keepAwake: Boolean = true,
    val monitoring: Boolean = false
)

@Dao
interface AppDao {
    @Query("SELECT * FROM watch_coins ORDER BY addedAt")
    fun watchCoins(): Flow<List<WatchCoin>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCoin(coin: WatchCoin)

    @Query("DELETE FROM watch_coins WHERE symbol = :symbol")
    suspend fun removeCoin(symbol: String)

    @Query("UPDATE watch_coins SET favorite = :favorite WHERE symbol = :symbol")
    suspend fun setFavorite(symbol: String, favorite: Boolean)

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun alerts(): Flow<List<PriceAlert>>

    @Query("SELECT * FROM alerts WHERE status = 'ACTIVE'")
    fun activeAlerts(): Flow<List<PriceAlert>>

    @Insert
    suspend fun addAlert(alert: PriceAlert): Long

    @Query("UPDATE alerts SET status = :status WHERE id = :id AND status != 'HIT'")
    suspend fun setStatus(id: Long, status: String)

    /** Returns 1 only for the first caller; this is what prevents duplicate notifications. */
    @Query("UPDATE alerts SET status = 'HIT', hitPrice = :price, hitAt = :time, direction = :direction WHERE id = :id AND status = 'ACTIVE'")
    suspend fun markHit(id: Long, price: Double, time: Long, direction: String): Int

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteAlert(id: Long)

    @Query("DELETE FROM alerts WHERE status = 'HIT'")
    suspend fun clearHistory()

    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settingsOnce(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}

@Database(
    entities = [WatchCoin::class, PriceAlert::class, AppSettings::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_coins ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alerts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDb::class.java, "price_alert.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
