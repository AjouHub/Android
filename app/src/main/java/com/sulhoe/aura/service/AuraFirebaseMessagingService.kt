package com.sulhoe.aura.service

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
import com.sulhoe.aura.MainActivity
import com.sulhoe.aura.R

class AuraFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM"

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: $token")
        getSharedPreferences("app", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
        // TODO: 서버 전송
        // sendRegistrationToServer(token)
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

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }

        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(this, uniId, intent, pendingFlags)

        // ✅ 데이터에 따라 채널 선택 (fallback: notice)
        val channelId = when {
            data["urgent"] == "1" || data["priority"] == "high" ->
                getString(R.string.ch_urgent_id)
            data["type"] == "system" ->
                getString(R.string.ch_system_id)
            else ->
                getString(R.string.ch_notice_id)
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, channelId)
            // 아이콘: 프로젝트에 벡터 드로어블 추가 시 아래 줄로 교체
            // .setSmallIcon(R.drawable.ic_stat_notification)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 즉시 빌드용 기본 아이콘
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri) // Pre-O 호환
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(uniId, builder.build())
    }
}
