package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val ingredientsUsed: String, // Comma separated or bullet point string of ingredients
    val instructions: String, // Step-by-step cooking instructions
    val prepTime: String = "20 min", // e.g. "15 min"
    val difficulty: String = "Fácil", // e.g. "Fácil", "Medio", "Difícil"
    val savedTimestamp: Long = System.currentTimeMillis()
)
