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
import com.sulhoe.aura.fcm.TopicManager
import com.sulhoe.aura.ui.WebViewActivity

class AuraFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: $token")
        getSharedPreferences("app", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
        // 컨테이너 구조: 원하는 토픽 집합 재구독(안전망)
        TopicManager.resync(applicationContext)
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "com.sulhoe.aura.OPEN_LINK.$uniId"
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, uniId, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = when {
            data["urgent"] == "1" || data["priority"] == "high" -> getString(R.string.ch_urgent_id)
            data["type"] == "system" -> getString(R.string.ch_system_id)
            else -> getString(R.string.ch_notice_id)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // 앱 아이콘으로 교체 권장
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)   // 분류 힌트
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 잠금화면 노출

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(uniId, builder.build())
    }
}