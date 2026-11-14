package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.AnniversaryEntity

@Dao
interface AnniversaryDAO {
    @Insert
    suspend fun insert(anniversary: AnniversaryEntity): Long

    @Update
    suspend fun update(anniversary: AnniversaryEntity)

    @Delete
    suspend fun delete(anniversary: AnniversaryEntity)

    @Query("SELECT * FROM anniversaries ORDER BY month ASC, day ASC")
    fun flowAll(): Flow<List<AnniversaryEntity>>

    @Query("SELECT * FROM anniversaries WHERE id = :id")
    suspend fun getById(id: Long): AnniversaryEntity?

    /** 当天匹配的纪念日(月+日相等),每年重复或单次都会命中——单次的由调用方按需过滤 */
    @Query("SELECT * FROM anniversaries WHERE month = :month AND day = :day")
    suspend fun getByMonthDay(month: Int, day: Int): List<AnniversaryEntity>
}
