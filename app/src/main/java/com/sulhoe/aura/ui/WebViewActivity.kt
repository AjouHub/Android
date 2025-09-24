// WebViewActivity.kt
package com.sulhoe.aura.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sulhoe.aura.R
import com.sulhoe.aura.ui.theme.AURATheme
import com.sulhoe.aura.ui.web.WebBridge

class WebViewActivity : ComponentActivity() {

    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink.value = intent?.data

        setContent {
            AURATheme {
                AuraScaffold(
                    pendingDeepLink = pendingDeepLink.value,
                    clearPendingDeepLink = { pendingDeepLink.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.data
    }
}

// 아래는 기존 AuraContainer 대체: 딥링크를 받아 bridge를 로드
@Composable
private fun AuraScaffold(
    pendingDeepLink: Uri?,
    clearPendingDeepLink: () -> Unit
) {
    val ctx = LocalContext.current
    val frontOrigin = remember {
        val u = Uri.parse(ctx.getString(R.string.frontend_url))
        "${u.scheme}://${u.authority}"
    }
    val apiOrigin = remember {
        val u = Uri.parse(ctx.getString(R.string.api_base_url))
        "${u.scheme}://${u.authority}"
    }
    val appScheme = ctx.getString(R.string.app_scheme)
    val apiAuth = "$apiOrigin/api/auth"

    fun frontEntryUrl(): String {
        val u = Uri.parse(ctx.getString(R.string.frontend_url))
        return u.buildUpon().appendQueryParameter("embed", "app").toString()
    }

    // OAuth 콜백 처리 (appScheme://oauth?code=...)
    LaunchedEffect(pendingDeepLink) {
        val data = pendingDeepLink ?: return@LaunchedEffect
        clearPendingDeepLink()
        val host = ctx.getString(R.string.app_oauth_host) // ex) "oauth"
        if (data.scheme == appScheme && data.host == host) {
            val code = data.getQueryParameter("code")
            val bridgeUrl = if (!code.isNullOrEmpty())
                "$apiAuth/sso/bridge?code=$code"
            else
                frontEntryUrl()
            WebBridge.load(bridgeUrl)
        }
    }

    // 기존 AuraContainer 내용을 그대로 사용하지만 entryUrl은 함수로
    AuraContainer(
        frontOrigin = frontOrigin,
        apiOrigin = apiOrigin,
        appScheme = appScheme,
        frontEntryUrl = frontEntryUrl()
    )
}