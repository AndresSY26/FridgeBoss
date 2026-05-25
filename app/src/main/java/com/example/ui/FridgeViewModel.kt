package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Ingredient
import com.example.data.model.Recipe
import com.example.data.repository.FridgeRepository
import com.example.data.repository.GamificationManager
import com.example.gemini.GeminiRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar

// State representing the AI Recipe Generation
sealed interface AiRecipeState {
    object Idle : AiRecipeState
    object Loading : AiRecipeState
    data class Success(val recipeMarkdown: String) : AiRecipeState
    data class Error(val message: String, val isKeyMissing: Boolean = false) : AiRecipeState
}

class FridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FridgeRepository
    private val gamificationManager = GamificationManager(application)

    // Trigger state change whenever statistics update
    private val _gamificationStatsTrigger = MutableStateFlow(0)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FridgeRepository(db.ingredientDao(), db.recipeDao())
    }

    // Reactive streams from Database
    val ingredientsInFridge: StateFlow<List<Ingredient>> = repository.ingredientsInFridge
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consumedIngredients: StateFlow<List<Ingredient>> = repository.consumedIngredients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wastedIngredients: StateFlow<List<Ingredient>> = repository.wastedIngredients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRecipes: StateFlow<List<Recipe>> = repository.savedRecipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive stream for critical ingredients (expires in 3 days or less)
    val criticalIngredients: StateFlow<List<Ingredient>> = repository.getCriticalIngredientsFlow(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ingredients active in the current recipe
    private val _activeRecipeIngredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val activeRecipeIngredients: StateFlow<List<Ingredient>> get() = _activeRecipeIngredients

    // UI state for Recipe Generation
    private val _aiRecipeState = MutableStateFlow<AiRecipeState>(AiRecipeState.Idle)
    val aiRecipeState: StateFlow<AiRecipeState> get() = _aiRecipeState

    // In-app alert state simulated notifications
    private val _inAppNotification = MutableStateFlow<String?>(null)
    val inAppNotification: StateFlow<String?> get() = _inAppNotification

    // OCR scanning state simulation
    private val _isOcrScanning = MutableStateFlow(false)
    val isOcrScanning: StateFlow<Boolean> get() = _isOcrScanning

    // Quick Stats state derived dynamically
    val statsState = combine(consumedIngredients, wastedIngredients, _gamificationStatsTrigger) { consumed, wasted, _ ->
        val moneySaved = gamificationManager.dineroSalvado
        val moneyWasted = wasted.sumOf { it.purchasePrice }
        val totalItemsHandled = consumed.size + wasted.size
        val itemsRescuedPercent = if (totalItemsHandled > 0) {
            (consumed.size.toFloat() / totalItemsHandled * 100).toInt()
        } else {
            0
        }
        val co2SavedKg = consumed.size * 1.8 // Assume 1.8kg CO2 per food rescued on average
        val streak = gamificationManager.rachaActual
        Stats(moneySaved, moneyWasted, itemsRescuedPercent, co2SavedKg, streak)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats(0.0, 0.0, 0, 0.0, 0))

    // Core Ingredient functions
    fun addIngredient(name: String, quantity: Double, unit: String, category: String, expiryDays: Int, price: Double) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, expiryDays)
            val expiryTimestamp = calendar.timeInMillis

            val newIngredient = Ingredient(
                name = name,
                quantity = quantity,
                unit = unit,
                category = category,
                expiryTimestamp = expiryTimestamp,
                purchasePrice = price,
                status = Ingredient.STATUS_IN_FRIDGE
            )
            repository.insertIngredient(newIngredient)
            checkExpiryAndTriggerAlert(newIngredient)
        }
    }

    fun markAsConsumed(ingredient: Ingredient) {
        viewModelScope.launch {
            val updated = ingredient.copy(status = Ingredient.STATUS_CONSUMED)
            repository.updateIngredient(updated)
            
            val isBeforeExpiry = System.currentTimeMillis() <= ingredient.expiryTimestamp
            if (isBeforeExpiry) {
                gamificationManager.onIngredientConsumed(ingredient.purchasePrice)
                _gamificationStatsTrigger.value += 1
            }
            
            triggerInAppNotification("🎉 ¡Felicidades! Consumiste '${ingredient.name}' y salvaste ${String.format("%.2f", ingredient.purchasePrice)} €")
        }
    }

    fun markAsWasted(ingredient: Ingredient) {
        viewModelScope.launch {
            val updated = ingredient.copy(status = Ingredient.STATUS_WASTED)
            repository.updateIngredient(updated)
            
            val isExpired = System.currentTimeMillis() > ingredient.expiryTimestamp
            if (isExpired) {
                gamificationManager.onStreakBroken()
                _gamificationStatsTrigger.value += 1
            }
            
            triggerInAppNotification("⚠️ '${ingredient.name}' marcado como desperdiciado (${String.format("%.2f", ingredient.purchasePrice)} € perdidos)")
        }
    }

    fun deleteIngredient(ingredient: Ingredient) {
        viewModelScope.launch {
            val isExpired = System.currentTimeMillis() > ingredient.expiryTimestamp
            if (isExpired) {
                gamificationManager.onStreakBroken()
                _gamificationStatsTrigger.value += 1
            }
            repository.deleteIngredient(ingredient)
        }
    }

    // Interactive ticket OCR scan simulation
    fun processCapturedReceiptBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            repository.procesarReciboConIA(bitmap).collect { resource ->
                when (resource) {
                    is com.example.data.repository.Resource.Loading -> {
                        _isOcrScanning.value = true
                        triggerInAppNotification("🤖 Analizando imagen capturada con el motor de IA...")
                    }
                    is com.example.data.repository.Resource.Success -> {
                        _isOcrScanning.value = false
                        val items = resource.data
                        if (items.isNotEmpty()) {
                            triggerInAppNotification("📸 ¡Escaneo Exitoso! Añadidos ${items.size} alimentos del recibo procesador por IA.")
                        } else {
                            triggerInAppNotification("📸 Escaneo finalizado pero no se encontraron alimentos.")
                        }
                    }
                    is com.example.data.repository.Resource.Error -> {
                        _isOcrScanning.value = false
                        val errorMsg = resource.exception.message ?: "Ocurrió un error"
                        triggerInAppNotification("❌ Error al procesar: $errorMsg")
                    }
                }
            }
        }
    }

    // Interactive ticket OCR scan simulation
    fun simulateTicketOcrScan() {
        viewModelScope.launch {
            _isOcrScanning.value = true
            kotlinx.coroutines.delay(2000) // Simulate OCR delay

            // Prefilled list representing scannable supermarket receipt items
            val scannedItems = listOf(
                Pair("Pollo Fresco", Triple(1.0, "unidades", Pair(2, 6.50))), // Expires in 2 days, cost: 6.50
                Pair("Leche Desnatada", Triple(2.0, "Litros", Pair(1, 1.80))), // Expires in 1 day, cost: 1.80
                Pair("Espinacas Bolsa", Triple(1.0, "unidades", Pair(3, 1.40))), // Expires in 3 days, cost: 1.40
                Pair("Aguacate", Triple(3.0, "unidades", Pair(4, 3.20))), // Expires in 4 days, cost: 3.20
                Pair("Yogurt Griego", Triple(4.0, "unidades", Pair(7, 2.50))) // Expires in 7 days, cost: 2.50
            )

            scannedItems.forEach { (name, details) ->
                val (qty, unit, exprAndPrice) = details
                val (daysToExpiry, price) = exprAndPrice
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, daysToExpiry)

                repository.insertIngredient(
                    Ingredient(
                        name = name,
                        quantity = qty,
                        unit = unit,
                        category = when (name) {
                            "Pollo Fresco" -> "Carnes"
                            "Leche Desnatada", "Yogurt Griego" -> "Lácteos"
                            "Espinacas Bolsa" -> "Verduras"
                            else -> "Frutas"
                        },
                        expiryTimestamp = calendar.timeInMillis,
                        purchasePrice = price
                    )
                )
            }

            _isOcrScanning.value = false
            triggerInAppNotification("📸 ¡Ticket escaneado! Agregados 5 ingredientes automáticamente.")
        }
    }

    // Expiry verification triggers in-app notification alerts
    private fun checkExpiryAndTriggerAlert(ingredient: Ingredient) {
        val daysLeft = getDaysUntilExpiry(ingredient.expiryTimestamp)
        if (daysLeft <= 2) {
            triggerInAppNotification("⏳ Alerta FridgeBoss: ¡El ingrediente '${ingredient.name}' caduca en $daysLeft días! Úsalo pronto.")
        }
    }

    // Trigger local push-like notification bar state
    fun triggerInAppNotification(message: String) {
        _inAppNotification.value = message
    }

    fun clearNotification() {
        _inAppNotification.value = null
    }

    // AI Generar Resetas Engine
    fun generateAiRecipeForExpiringFoods() {
        viewModelScope.launch {
            _aiRecipeState.value = AiRecipeState.Loading

            // Extract ingredients expiring soon (3 days or less)
            val currentFridgeItems = ingredientsInFridge.value
            if (currentFridgeItems.isEmpty()) {
                _aiRecipeState.value = AiRecipeState.Error("No tienes ingredientes en tu inventario para cocinar. ¡Agrega o escanea un ticket!")
                return@launch
            }

            // Group expiring soon items
            val expiringSoon = currentFridgeItems.filter { getDaysUntilExpiry(it.expiryTimestamp) <= 3 }
            val itemsToUse = if (expiringSoon.isNotEmpty()) expiringSoon else currentFridgeItems
            _activeRecipeIngredients.value = itemsToUse

            val ingredientsText = itemsToUse.joinToString(separator = "\n") { "- ${it.name} (${it.quantity} ${it.unit}) - Caduca en ${getDaysUntilExpiry(it.expiryTimestamp)} días" }

            val result = GeminiRetrofitClient.generateRecipe(ingredientsText)

            if (result == "ERROR_API_KEY_MISSING") {
                // Return descriptive error with missing key state so UI displays tutorial card
                _aiRecipeState.value = AiRecipeState.Error(
                    message = "Por favor ingresa tu API Key de Gemini en el panel de Secrets de AI Studio para activar recetas por Inteligencia Artificial.",
                    isKeyMissing = true
                )
            } else if (result.startsWith("Error:")) {
                _aiRecipeState.value = AiRecipeState.Error("Error de Red al conectar con la Inteligencia Artificial. Mostrando receta segura.")
            } else {
                _aiRecipeState.value = AiRecipeState.Success(result)
            }
        }
    }

    // Mock Offline Recipe Generator for beautiful fallback sandbox demo with Colombian flavor
    fun generateOfflineRecipeFallbackInstance() {
        viewModelScope.launch {
            _aiRecipeState.value = AiRecipeState.Loading
            kotlinx.coroutines.delay(1000)

            val currentFridgeItems = ingredientsInFridge.value
            if (currentFridgeItems.isEmpty()) {
                _aiRecipeState.value = AiRecipeState.Error("No tienes ingredientes en tu inventario para cocinar. ¡Agrega o escanea un ticket!")
                return@launch
            }

            val expiringSoon = currentFridgeItems.filter { getDaysUntilExpiry(it.expiryTimestamp) <= 3 }
            val itemsToUse = if (expiringSoon.isNotEmpty()) expiringSoon else currentFridgeItems
            _activeRecipeIngredients.value = itemsToUse

            val mainIngredient = itemsToUse.firstOrNull()?.name ?: "Ingredientes Variados"

            val mockRecipeText = """
                **Título de la Receta**
                Calentao Colombiano "Salva-Nevera" con $mainIngredient (Estilo Casero)

                **Tiempo de Preparación y Dificultad**
                Tiempo: 12 min | Dificultad: fácil

                **Ingredientes Utilizados de la Nevera**
                ${itemsToUse.joinToString(separator = "\n") { "- ${it.name} (${it.quantity} ${it.unit})" }}

                **Ingredientes Básicos de Despensa Adicionales**
                - 1 taza de Arroz blanco cocido del día anterior
                - 1/2 taza de Frijoles o lentejas cocidas
                - Un chorrito de aceite de girasol, sal y cilantro fresco
                - Huevo frito u arepa (opcional)

                **Instrucciones Paso a Paso**
                1. Corta finamente tus ingredientes recuperados de la nevera (como carne, salmón, salchichas, aguacate o quesos).
                2. En una sartén grande con aceite caliente, saltea un picadillo de ajo u cebolla si posees, luego saltea el ingrediente principal ($mainIngredient) desmenuzado durante 5 minutos.
                3. Agrega la taza de arroz y granos fríos del día anterior para calentarlos juntos, revolviendo con amor y sabor hogareño hasta lograr una mezcla uniforme y tostada.
                4. Sirve caliente adornado con rodajas de aguacate maduro o cilantro fresco. ¡Cero desperdicio colombiano!

                **Tip Antibasura (Zero Waste Tip)**
                El calentao es el rey indiscutible de la cocina de desperdicio cero colombiana. Al saltear arroz o guisos que te quedaron de comidas anteriores con carnes o de vegetales próximos a vencer, no solo creas un desayuno/almuerzo súper reconfortante, sino que detienes la oxidación de tus alimentos y generas un ahorro increíble.
            """.trimIndent()

            _aiRecipeState.value = AiRecipeState.Success(mockRecipeText)
        }
    }

    fun consumeActiveRecipeIngredients() {
        viewModelScope.launch {
            val itemsToConsume = _activeRecipeIngredients.value
            if (itemsToConsume.isEmpty()) {
                triggerInAppNotification("⚠️ No de ingredientes activos en la receta actual para consumir.")
                return@launch
            }
            itemsToConsume.forEach { ingredient ->
                val updated = ingredient.copy(status = Ingredient.STATUS_CONSUMED)
                repository.updateIngredient(updated)
                
                val isBeforeExpiry = System.currentTimeMillis() <= ingredient.expiryTimestamp
                if (isBeforeExpiry) {
                    gamificationManager.onIngredientConsumed(ingredient.purchasePrice)
                }
            }
            _gamificationStatsTrigger.value += 1
            
            triggerInAppNotification("🍳 ¡Manos a la obra! Se marcaron ${itemsToConsume.size} alimentos como consumidos de forma segura. ¡Buen provecho!")
            _aiRecipeState.value = AiRecipeState.Idle
            _activeRecipeIngredients.value = emptyList()
        }
    }

    // Save recipe into database
    fun saveGeneratedRecipe(title: String, ingredients: String, instructions: String) {
        viewModelScope.launch {
            repository.insertRecipe(
                Recipe(
                    title = title,
                    ingredientsUsed = ingredients,
                    instructions = instructions
                )
            )
            triggerInAppNotification("⭐ Receta saved: '$title' guardada en favoritos!")
        }
    }

    fun deleteSavedRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.deleteRecipe(recipe)
            triggerInAppNotification("🗑️ Receta eliminada de favoritos.")
        }
    }

    // Helper calculate system duration
    fun getDaysUntilExpiry(timestamp: Long): Int {
        val diff = timestamp - System.currentTimeMillis()
        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
        return if (days < 0 && Math.abs(diff) < (1000 * 60 * 60 * 2)) 0 else days
    }

    fun getExpiryLabelAndColor(timestamp: Long): Pair<String, String> {
        val days = getDaysUntilExpiry(timestamp)
        return when {
            days < 0 -> Pair("Caducado", "RED")
            days == 0 -> Pair("Caduca HOY", "RED")
            days == 1 -> Pair("Caduca Mañana", "ORANGE")
            days <= 3 -> Pair("Caduca en $days días", "ORANGE")
            else -> Pair("Fresco ($days días restantes)", "GREEN")
        }
    }

    fun addSampleIngredients() {
        viewModelScope.launch {
            val products = listOf(
                Pair("Lomo de Salmón", Pair("Carnes", Pair(2, 8.90))),
                Pair("Queso Parmesano", Pair("Lácteos", Pair(1, 3.20))),
                Pair("Tomate Cherry", Pair("Verduras", Pair(14, 1.50))),
                Pair("Yogur de Fresa", Pair("Lácteos", Pair(-1, 0.80))), // Expired yesterday
                Pair("Aguacate Maduro", Pair("Frutas", Pair(0, 2.00)))  // Expires today
            )
            products.forEach { (name, details) ->
                val (category, timingAndPrice) = details
                val (days, price) = timingAndPrice
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, days)
                repository.insertIngredient(
                    Ingredient(
                        name = name,
                        quantity = 1.0,
                        unit = "unidades",
                        category = category,
                        expiryTimestamp = calendar.timeInMillis,
                        purchasePrice = price
                    )
                )
            }
            triggerInAppNotification("🔋 Alimentos de muestra cargados con diferentes fechas de vencimiento.")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            gamificationManager.clearStats()
            _gamificationStatsTrigger.value += 1
            _aiRecipeState.value = AiRecipeState.Idle
            triggerInAppNotification("🧼 Nevera vaciada y estadísticas reiniciadas.")
        }
    }
}

// Stats Holder Class
data class Stats(
    val moneySaved: Double,
    val moneyWasted: Double,
    val rescueRate: Int,
    val carbonSaved: Double,
    val currentStreak: Int
)

// ViewModel Provider Factory
class FridgeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FridgeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FridgeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
