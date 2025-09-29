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
    private val pendingNavUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink.value = intent?.data
        extractNavUrlFromExtras(intent)

        setContent {
            AURATheme {
                AuraScaffold(
                    pendingDeepLink = pendingDeepLink.value,
                    pendingNavUrl = pendingNavUrl.value,
                    clearPendingDeepLink = { pendingDeepLink.value = null },
                    clearPendingNavUrl = { pendingNavUrl.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.data
    }

    private fun extractNavUrlFromExtras(intent: Intent?) {
        if (intent == null) return
        val link = intent.getStringExtra("link")
        if (!link.isNullOrBlank()) pendingNavUrl.value = link
    }
}

// 아래는 기존 AuraContainer 대체: 딥링크를 받아 bridge를 로드
@Composable
private fun AuraScaffold(
    pendingDeepLink: Uri?,
    pendingNavUrl: String?,
    clearPendingDeepLink: () -> Unit,
    clearPendingNavUrl: () -> Unit
) {
    val ctx = LocalContext.current

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

    // 2) 알림 클릭: 상세로 바로 열기
    LaunchedEffect(pendingNavUrl) {
        val target = pendingNavUrl ?: return@LaunchedEffect
        clearPendingNavUrl()
        WebBridge.openDetail?.invoke(target)  // ← 목록이 아니라 상세 오버레이로
    }

    // 기존 AuraContainer 내용을 그대로 사용하지만 entryUrl은 함수로
    AuraContainer(
        apiOrigin = apiOrigin,
        frontEntryUrl = frontEntryUrl()
    )
}