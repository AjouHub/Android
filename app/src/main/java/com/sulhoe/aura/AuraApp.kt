package com.sulhoe.aura

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannelsIfNeeded()
    }

    private fun createNotificationChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun ch(id: String, name: String, desc: String, importance: Int) =
            NotificationChannel(id, name, importance).apply { description = desc }

        val notice = ch(
            getString(R.string.ch_notice_id),
            getString(R.string.ch_notice_name),
            getString(R.string.ch_notice_desc),
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val urgent = ch(
            getString(R.string.ch_urgent_id),
            getString(R.string.ch_urgent_name),
            getString(R.string.ch_urgent_desc),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // 필요 시 별도 사운드:
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(uri, attrs)
        }

        val system = ch(
            getString(R.string.ch_system_id),
            getString(R.string.ch_system_name),
            getString(R.string.ch_system_desc),
            NotificationManager.IMPORTANCE_LOW
        )

        nm.createNotificationChannels(listOf(notice, urgent, system))
    }
}
