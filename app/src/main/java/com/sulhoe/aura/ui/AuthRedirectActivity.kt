// AuthRedirectActivity.kt
package com.sulhoe.aura.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class AuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deepLink: Uri? = intent?.data   // ← 이름 변경

        val i = Intent(this, WebViewActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLink                 // ← OK
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(i)
        finish()
    }
}
