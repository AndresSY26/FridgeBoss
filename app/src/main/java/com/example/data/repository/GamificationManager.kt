package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GamificationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fridgeboss_gamification_prefs", Context.MODE_PRIVATE)

    var rachaActual: Int
        get() = prefs.getInt("key_racha_actual", 0)
        set(value) {
            prefs.edit().putInt("key_racha_actual", value).apply()
            if (value > maxRacha) {
                maxRacha = value
            }
        }

    var maxRacha: Int
        get() = prefs.getInt("key_max_racha", 0)
        set(value) = prefs.edit().putInt("key_max_racha", value).apply()

    var dineroSalvado: Double
        get() = prefs.getString("key_dinero_salvado", "0.0")?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString("key_dinero_salvado", value.toString()).apply()

    /**
     * Call when an ingredient is consumed before its expiry date.
     * Keeps track of days consecutive and sum money saved.
     */
    fun onIngredientConsumed(purchasePrice: Double) {
        // 1. Add to money saved
        dineroSalvado += purchasePrice

        // 2. Check if it is the first consumption of today to increment the streak
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastConsumedDay = prefs.getString("key_last_consumed_day", "") ?: ""

        if (lastConsumedDay != todayStr) {
            rachaActual += 1
            prefs.edit().putString("key_last_consumed_day", todayStr).apply()
        }
    }

    /**
     * Call when a food item gets wasted/deleted after expiry (broken streak).
     */
    fun onStreakBroken() {
        rachaActual = 0
    }

    fun clearStats() {
        prefs.edit().clear().apply()
    }
}
