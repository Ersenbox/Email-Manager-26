package com.example.platform

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.common.data.EmailDatabase
import com.example.common.data.EmailEntity
import com.example.common.i18n.t
import com.example.common.repository.EmailRepository
import com.example.common.settings.AppSettings
import java.util.concurrent.TimeUnit

/** Uygulama kapalıyken eşitleme ve bildirimler. */
object BackgroundSync {
    private const val WORK_NAME = "mail_background_sync"
    private const val INTERVAL_MINUTES = 30L

    /** Ayara göre arka plan işini başlatır veya durdurur. */
    fun apply(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!AppSettings.backgroundSync()) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<MailSyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class MailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AndroidAppSetup.init(applicationContext)
        // Uygulama açıkken eşitlemeyi ekrandaki kod yapar
        if (!AppSettings.backgroundSync() || AppSettings.appInForeground) return Result.success()

        val database = EmailDatabase.getInstance(applicationContext)
        val repository = EmailRepository(
            database.emailDao(),
            database.accountDao(),
            AndroidPlatformBridge(applicationContext)
        )
        if (AppSettings.notifyImportant()) {
            repository.onNewImportantMail = { MailNotifier.showNewMail(applicationContext, it) }
        }
        for (account in repository.allAccounts()) {
            repository.syncAllAccountData(account, isManual = false)
        }
        sendWeeklyReportIfDue(repository)
        return Result.success()
    }

    private suspend fun sendWeeklyReportIfDue(repository: EmailRepository) {
        if (!AppSettings.weeklyReport()) return
        val now = System.currentTimeMillis()
        val last = AppSettings.lastWeeklyReport()
        if (last == 0L) {
            AppSettings.setLastWeeklyReport(now)
            return
        }
        if (now - last < TimeUnit.DAYS.toMillis(7)) return
        val report = repository.weeklyReport(now)
        MailNotifier.showWeeklyReport(
            applicationContext,
            t("Bu hafta {0} posta geldi: {1} reklam, {2} bülten, {3} sosyal medya. Okunmamış: {4}.",
                report.received, report.ads, report.newsletters, report.social, report.unread)
        )
        AppSettings.setLastWeeklyReport(now)
    }
}

object MailNotifier {
    private const val CHANNEL_MAIL = "important_mail"
    private const val CHANNEL_REPORT = "weekly_report"
    private const val REPORT_ID = 7001

    private fun channel(context: Context, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission")
    fun showNewMail(context: Context, emails: List<EmailEntity>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        channel(context, CHANNEL_MAIL, t("Önemli postalar"), NotificationManager.IMPORTANCE_DEFAULT)
        for (email in emails) {
            val notification = NotificationCompat.Builder(context, CHANNEL_MAIL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(email.senderName)
                .setContentText(email.subject)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build()
            manager.notify(email.messageId?.hashCode() ?: email.subject.hashCode(), notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun showWeeklyReport(context: Context, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        channel(context, CHANNEL_REPORT, t("Haftalık rapor"), NotificationManager.IMPORTANCE_LOW)
        val notification = NotificationCompat.Builder(context, CHANNEL_REPORT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(t("Haftalık posta raporu"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        manager.notify(REPORT_ID, notification)
    }
}
