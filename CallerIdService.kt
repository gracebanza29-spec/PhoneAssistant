package com.phoneassistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phoneassistant.R
import com.phoneassistant.data.repository.BlockedRepo
import com.phoneassistant.data.repository.CallerIdRepository
import com.phoneassistant.data.repository.ContactsRepository
import kotlinx.coroutines.*

class CallerIdService : Service() {

    companion object {
        const val EXTRA_NUMBER = "phone_number"
        const val CHANNEL_ID   = "caller_id"
        const val NOTIF_ID     = 2001
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildLoading())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(EXTRA_NUMBER) ?: run { stopSelf(); return START_NOT_STICKY }

        scope.launch {
            // 1. Bloqué ?
            if (BlockedRepo(applicationContext).isBlocked(number)) {
                notify("🚫 Appel bloqué", number)
                stopSelf(); return@launch
            }

            // 2. Dans les contacts ?
            val contact = ContactsRepository(applicationContext).findByNumber(number)
            if (contact != null) { stopSelf(); return@launch }

            // 3. API Caller ID
            val info = CallerIdRepository(applicationContext).identify(number)
            val detail = info?.let { i ->
                buildString {
                    i.carrier?.let  { append("📡 $it") }
                    i.lineType?.let { if (isNotEmpty()) append("  •  "); append(it) }
                    i.location?.let { if (isNotEmpty()) append("\n📍 $it") }
                    if (i.isSpam) append("\n⚠️ SPAM PROBABLE")
                }
            } ?: "Numéro non identifié"

            notify("📞 Appel entrant inconnu", number, detail)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun notify(title: String, number: String, detail: String? = null) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(number)
            .apply { detail?.let { setStyle(NotificationCompat.BigTextStyle().bigText("$number\n$it")) } }
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        mgr.notify(NOTIF_ID + 1, n)
    }

    private fun buildLoading() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Identification en cours…")
        .setSmallIcon(android.R.drawable.ic_menu_search)
        .build()

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Identification appelant", NotificationManager.IMPORTANCE_HIGH)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
