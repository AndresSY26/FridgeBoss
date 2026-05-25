package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.DesperdicioCeroWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class FridgeBossApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("FridgeBossApplication", "Inicializando FridgeBossApplication...")
        
        // 1. Register notification channel
        createNotificationChannel()

        // 2. Schedule unique periodic background task
        scheduleDailyExpiryCheck()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas Desperdicio Cero"
            val descriptionText = "Notificaciones preventivas antes de que caduquen tus alimentos."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                DesperdicioCeroWorker.CHANNEL_ID,
                name,
                importance
            ).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("FridgeBossApplication", "Canal de notificaciones 'alertas_desperdicio_fridgeboss' registrado.")
        }
    }

    private fun scheduleDailyExpiryCheck() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        
        val initialDelay = dueDate.timeInMillis - currentDate.timeInMillis
        Log.d("FridgeBossApplication", "Calculado delay inicial para las 9:00 AM: ${initialDelay / 1000 / 60} minutos.")

        val workRequest = PeriodicWorkRequestBuilder<DesperdicioCeroWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "desperdicio_cero_periodic_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d("FridgeBossApplication", "Tarea periódica 'desperdicio_cero_periodic_work' registrada exitosamente.")
    }
}
