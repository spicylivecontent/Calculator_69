package com.calculator.app.data.dao

import androidx.room.*
import com.calculator.app.data.entity.DislikeRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DislikeRuleDao {

    @Query("SELECT * FROM dislike_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<DislikeRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: DislikeRuleEntity)

    @Delete
    suspend fun deleteRule(rule: DislikeRuleEntity)

    @Query("DELETE FROM dislike_rules WHERE type = :type AND value = :value")
    suspend fun deleteByTypeAndValue(type: String, value: String)

    @Query("SELECT COUNT(*) FROM dislike_rules")
    fun getCount(): Flow<Int>
}
