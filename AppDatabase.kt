package com.phoneassistant.data.db

import android.content.Context
import androidx.room.*
import com.phoneassistant.data.model.*

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    suspend fun getAll(): List<BlockedNumber>

    @Query("SELECT * FROM blocked_numbers WHERE normalizedNumber = :n LIMIT 1")
    suspend fun findByNumber(n: String): BlockedNumber?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: BlockedNumber)

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :n")
    suspend fun deleteByNumber(n: String)
}

@Dao
interface ContactNoteDao {
    @Query("SELECT * FROM contact_notes WHERE contactId = :id LIMIT 1")
    suspend fun get(id: Long): ContactNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(note: ContactNote)

    @Query("DELETE FROM contact_notes WHERE contactId = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt ASC")
    suspend fun getAll(): List<FavoriteContact>

    @Query("SELECT * FROM favorites WHERE contactId = :id LIMIT 1")
    suspend fun find(id: Long): FavoriteContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: FavoriteContact)

    @Query("DELETE FROM favorites WHERE contactId = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CallerIdCacheDao {
    @Query("SELECT * FROM caller_id_cache WHERE normalizedNumber = :n LIMIT 1")
    suspend fun get(n: String): CallerIdCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CallerIdCache)

    @Query("DELETE FROM caller_id_cache WHERE cachedAt < :threshold")
    suspend fun evictOld(threshold: Long)
}

@Database(
    entities = [BlockedNumber::class, ContactNote::class, FavoriteContact::class, CallerIdCache::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun contactNoteDao(): ContactNoteDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun callerIdCacheDao(): CallerIdCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "phone_assistant.db")
                .build().also { INSTANCE = it }
        }
    }
}
