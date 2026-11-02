package com.gigabee.app.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gigabee.app.MainActivity
import com.gigabee.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Evening nudge ("your ads are ready") for users who haven't watched an ad today. */
object Reminder {
    private const val CHANNEL = "daily"
    private const val WORK = "daily-reminder"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gigabee_reminder", Context.MODE_PRIVATE)

    fun markWatchedToday(ctx: Context) = prefs(ctx).edit { putString("last_ad_day", LocalDate.now().toString()) }

    fun watchedToday(ctx: Context) = prefs(ctx).getString("last_ad_day", "") == LocalDate.now().toString()

    /** Runs once a day around 18:00 local time. Safe to call on every launch. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(18, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFFD400.toInt())
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(ctx.getString(R.string.notif_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, notification)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!Reminder.watchedToday(applicationContext)) Reminder.show(applicationContext)
        return Result.success()
    }
}
