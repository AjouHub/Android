// app/src/main/java/com/sulhoe/aura/fcm/TopicManager.kt
package com.sulhoe.aura.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import java.text.Normalizer
import java.util.Locale

object TopicManager {
    private const val TAG = "TopicManager"
    private const val PREF = "fcm_topics"
    private const val KEY_SET = "desired_topics_csv"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun loadDesired(ctx: Context): MutableSet<String> =
        prefs(ctx).getString(KEY_SET, "")!!
            .split(",").filter { it.isNotBlank() }.toMutableSet()

    private fun saveDesired(ctx: Context, topics: Set<String>) {
        prefs(ctx).edit().putString(KEY_SET, topics.joinToString(",")).apply()
    }

    /** 서버와 동일한 sanitize 규칙 */
    fun sanitizeSegment(t: String?): String {
        if (t.isNullOrBlank()) return "unknown"
        val nfkc = Normalizer.normalize(t.trim(), Normalizer.Form.NFKC)
        val lowered = nfkc.lowercase(Locale.ROOT)
        return lowered
            .replace("\\s+".toRegex(), "-")
            .replace("[^a-z0-9-_.~%]".toRegex(), "-")
            .replace("-{2,}".toRegex(), "-")
    }

    fun ensureUserTopic(ctx: Context, email: String?) {
        if (email.isNullOrBlank()) {
            Log.w(TAG, "ensureUserTopic: email is blank, skip subscribing.")
            return
        }
        subscribe(ctx, "user-${sanitizeSegment(email)}")
    }

    fun applyTypeMode(ctx: Context, type: String, mode: String) {
        val topic = "type-${sanitizeSegment(type)}"
        when (mode.uppercase(Locale.ROOT)) {
            "ALL" -> subscribe(ctx, topic)
            "KEYWORD", "NONE" -> unsubscribe(ctx, topic)
            else -> Log.w(TAG, "Unknown mode=$mode for type=$type")
        }
    }

    fun subscribe(ctx: Context, topic: String) {
        val desired = loadDesired(ctx)
        if (desired.add(topic)) saveDesired(ctx, desired)
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { Log.d(TAG, "subscribe($topic) -> ${it.isSuccessful}") }
    }

    fun unsubscribe(ctx: Context, topic: String) {
        val desired = loadDesired(ctx)
        if (desired.remove(topic)) saveDesired(ctx, desired)
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { Log.d(TAG, "unsubscribe($topic) -> ${it.isSuccessful}") }
    }

    /** 새 토큰 발급 등 시점에 재적용(안전망) */
    fun resync(ctx: Context) {
        val desired = loadDesired(ctx)
        desired.forEach { topic ->
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener { Log.d(TAG, "resub($topic) -> ${it.isSuccessful}") }
        }
    }
}
