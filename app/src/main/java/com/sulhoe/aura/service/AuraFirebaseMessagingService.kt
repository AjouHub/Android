// app/src/main/java/com/sulhoe/aura/service/AuraFirebaseMessagingService.kt
package com.sulhoe.aura.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sulhoe.aura.R
import com.sulhoe.aura.ui.WebViewActivity

class AuraFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM"

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: $token")
        getSharedPreferences("app", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
        // TODO: 서버 전송
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from ?: "unknown"}")

        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: getString(R.string.app_name)
            val body  = remoteMessage.data["body"] ?: ""
            showNotification(title, body, remoteMessage.data)
            return
        }

        remoteMessage.notification?.let {
            val title = it.title ?: getString(R.string.app_name)
            val body  = it.body ?: ""
            showNotification(title, body, emptyMap())
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val uniId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val intent = Intent(this, WebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }

        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                (PendingIntent.FLAG_IMMUTABLE)
        val pendingIntent = PendingIntent.getActivity(this, uniId, intent, pendingFlags)

        val channelId = when {
            data["urgent"] == "1" || data["priority"] == "high" -> getString(R.string.ch_urgent_id)
            data["type"] == "system" -> getString(R.string.ch_system_id)
            else -> getString(R.string.ch_notice_id)
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 추후 벡터 아이콘으로 교체 권장
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri) // Pre-O 호환
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
        nm.notify(uniId, builder.build())
    }
}
