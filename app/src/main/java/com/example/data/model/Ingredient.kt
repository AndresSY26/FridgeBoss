package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredients")
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double,
    val unit: String, // e.g. "g", "L", "unidades"
    val category: String, // e.g., "Lácteos", "Carnes", "Verduras", "Frutas", "Otros"
    val expiryTimestamp: Long, // Epoch millis of expiry date
    val purchasePrice: Double, // Cost of food item in Euros/Dollars for Gamification
    val status: String = STATUS_IN_FRIDGE, // IN_FRIDGE, CONSUMED, WASTED
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_IN_FRIDGE = "IN_FRIDGE"
        const val STATUS_CONSUMED = "CONSUMED"
        const val STATUS_WASTED = "WASTED"
    }
}
