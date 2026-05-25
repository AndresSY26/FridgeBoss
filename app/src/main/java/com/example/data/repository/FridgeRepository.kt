package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.dao.IngredientDao
import com.example.data.dao.RecipeDao
import com.example.data.model.Ingredient
import com.example.data.model.Recipe
import com.example.gemini.GeminiRetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Calendar

class FridgeRepository(
    private val ingredientDao: IngredientDao,
    private val recipeDao: RecipeDao
) {
    val allIngredients: Flow<List<Ingredient>> = ingredientDao.getAllIngredients()
    val ingredientsInFridge: Flow<List<Ingredient>> = ingredientDao.getIngredientsInFridge()
    val consumedIngredients: Flow<List<Ingredient>> = ingredientDao.getConsumedIngredients()
    val wastedIngredients: Flow<List<Ingredient>> = ingredientDao.getWastedIngredients()
    val savedRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    /**
     * Requirement 1: Función de Análisis de Inventario
     * Escanea e interroga la base de datos de Room y filtra los productos activos que 
     * poseen 3 días o menos de estimado de vida útil (días vida estimados).
     */
    fun getCriticalIngredientsFlow(maxDays: Int = 3): Flow<List<Ingredient>> {
        return ingredientDao.getIngredientsInFridge().map { list ->
            list.filter { ingredient ->
                val diff = ingredient.expiryTimestamp - System.currentTimeMillis()
                val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                val daysUntilExpiry = if (days < 0 && Math.abs(diff) < (1000 * 60 * 60 * 2)) 0 else days
                daysUntilExpiry <= maxDays
            }
        }
    }

    suspend fun getExpiringIngredients(maxTimestamp: Long): List<Ingredient> {
        return ingredientDao.getExpiringIngredients(maxTimestamp)
    }

    fun procesarReciboConIA(imageBitmap: Bitmap): Flow<Resource<List<Ingredient>>> = flow {
        emit(Resource.Loading)
        try {
            val ocrResult = GeminiRetrofitClient.processReceiptWithIa(imageBitmap)
            val addedIngredients = mutableListOf<Ingredient>()
            
            for (prod in ocrResult.productos) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, prod.diasVidaEstimados)
                
                val ingredient = Ingredient(
                    name = prod.nombre,
                    quantity = prod.cantidad,
                    unit = prod.unidad,
                    category = prod.categoria,
                    expiryTimestamp = calendar.timeInMillis,
                    purchasePrice = prod.precioUnitario * prod.cantidad,
                    status = Ingredient.STATUS_IN_FRIDGE
                )
                
                insertIngredient(ingredient)
                addedIngredients.add(ingredient)
            }
            emit(Resource.Success(addedIngredients))
        } catch (t: Throwable) {
            emit(Resource.Error(t))
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    suspend fun insertIngredient(ingredient: Ingredient) {
        ingredientDao.insertIngredient(ingredient)
    }

    suspend fun updateIngredient(ingredient: Ingredient) {
        ingredientDao.updateIngredient(ingredient)
    }

    suspend fun deleteIngredient(ingredient: Ingredient) {
        ingredientDao.deleteIngredient(ingredient)
    }

    suspend fun insertRecipe(recipe: Recipe) {
        recipeDao.insertRecipe(recipe)
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        recipeDao.deleteRecipe(recipe)
    }

    suspend fun clearAllData() {
        ingredientDao.clearAllIngredients()
        recipeDao.clearAllRecipes()
    }
}
