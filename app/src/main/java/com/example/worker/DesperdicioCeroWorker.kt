package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.repository.FridgeRepository

class DesperdicioCeroWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "alertas_desperdicio_fridgeboss"
        const val NOTIFICATION_ID = 4210
    }

    override suspend fun doWork(): Result {
        Log.d("DesperdicioCeroWorker", "Iniciando verificación programada de ingredientes...")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = FridgeRepository(db.ingredientDao(), db.recipeDao())

            // "a 2 días o menos de caducar (dias_vida_estimados <= 2)"
            // 2 days in milliseconds: 2 * 24 * 60 * 60 * 1000 = 172,800,000
            val maxTimestamp = System.currentTimeMillis() + (2L * 24 * 60 * 60 * 1000)
            
            val expiringIngredients = repository.getExpiringIngredients(maxTimestamp)
            Log.d("DesperdicioCeroWorker", "Encontrados ${expiringIngredients.size} ingredientes con fecha de vencimiento crítica.")

            if (expiringIngredients.isNotEmpty()) {
                sendNotification(expiringIngredients)
            }
        } catch (e: Exception) {
            Log.e("DesperdicioCeroWorker", "Error al verificar alimentos por caducar", e)
            return Result.retry()
        }

        return Result.success()
    }

    private fun sendNotification(items: List<com.example.data.model.Ingredient>) {
        val context = applicationContext
        
        // Ensure channel is registered
        createNotificationChannel(context)

        val firstItemName = items.first().name
        val description = if (items.size > 1) {
            "Tienes $firstItemName y otros ${items.size - 1} productos a punto de vencer. ¡Abre el Chef IA para salvarlos!"
        } else {
            "Tienes $firstItemName a punto de vencer. ¡Abre el Chef IA para salvarlos!"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("¡Alerta Desperdicio Cero! 🚨")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.d("DesperdicioCeroWorker", "Notificación del sistema mostrada correctamente.")
        } catch (e: SecurityException) {
            Log.e("DesperdicioCeroWorker", "Sin permisos de notificación", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas Desperdicio Cero"
            val descriptionText = "Notificaciones preventivas antes de que caduquen tus alimentos."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
