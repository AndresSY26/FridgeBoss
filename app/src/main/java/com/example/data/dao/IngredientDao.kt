package com.example.data.dao

import androidx.room.*
import com.example.data.model.Ingredient
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients ORDER BY expiryTimestamp ASC")
    fun getAllIngredients(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE status = 'IN_FRIDGE' ORDER BY expiryTimestamp ASC")
    fun getIngredientsInFridge(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE status = 'IN_FRIDGE' AND expiryTimestamp <= :maxTimestamp")
    suspend fun getExpiringIngredients(maxTimestamp: Long): List<Ingredient>

    @Query("SELECT * FROM ingredients WHERE status = 'CONSUMED' ORDER BY addedTimestamp DESC")
    fun getConsumedIngredients(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE status = 'WASTED' ORDER BY addedTimestamp DESC")
    fun getWastedIngredients(): Flow<List<Ingredient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: Ingredient)

    @Update
    suspend fun updateIngredient(ingredient: Ingredient)

    @Delete
    suspend fun deleteIngredient(ingredient: Ingredient)

    @Query("DELETE FROM ingredients")
    suspend fun clearAllIngredients()
}
