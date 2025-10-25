package com.sulhoe.aura

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.sulhoe.aura.fcm.TopicManager

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannelsIfNeeded()
        TopicManager.subscribe(applicationContext, "system")
        TopicManager.subscribe(applicationContext, "broadcast")
    }

    private fun createNotificationChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun ch(id: String, name: String, desc: String, importance: Int) =
            NotificationChannel(id, name, importance).apply {
                description = desc
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setSound(null, null)
            }

        val notice = ch(
            getString(R.string.ch_notice_id),
            getString(R.string.ch_notice_name),
            getString(R.string.ch_notice_desc),
            NotificationManager.IMPORTANCE_HIGH
        )

        val urgent = ch(
            getString(R.string.ch_urgent_id),
            getString(R.string.ch_urgent_name),
            getString(R.string.ch_urgent_desc),
            NotificationManager.IMPORTANCE_HIGH
        )

        val system = ch(
            getString(R.string.ch_system_id),
            getString(R.string.ch_system_name),
            getString(R.string.ch_system_desc),
            NotificationManager.IMPORTANCE_HIGH
        )

        nm.createNotificationChannels(listOf(notice, urgent, system))
    }
}
