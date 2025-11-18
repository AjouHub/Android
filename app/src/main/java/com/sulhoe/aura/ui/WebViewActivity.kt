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

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val p = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(p), 1001)
            }
        }

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
        extractNavUrlFromExtras(intent)
    }

    private fun extractNavUrlFromExtras(intent: Intent?) {
        if (intent == null) return
        val link = intent.getStringExtra("link")
        android.util.Log.d("FCM", "onIntent link=$link action=${intent.action} flags=${intent.flags}")
        if (!link.isNullOrBlank()) pendingNavUrl.value = link
    }
}

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
        val url = u.buildUpon().appendQueryParameter("embed", "app").toString()
        android.util.Log.d("AuraScaffold", "frontEntryUrl: $url")
        return url
    }

    // OAuth 콜백 처리
    LaunchedEffect(pendingDeepLink) {
        val data = pendingDeepLink ?: return@LaunchedEffect
        clearPendingDeepLink()
        val host = ctx.getString(R.string.app_oauth_host)

        if (data.scheme == appScheme && data.host == host) {
            val errorCode = data.getQueryParameter("error")
            val errorMessage = data.getQueryParameter("message")

            if (errorCode != null) {
                // ✅ hash 라우팅 + search 제거 (새로고침 금지)
                val errHash = "/auth/error?code=${Uri.encode(errorCode)}&message=${Uri.encode(errorMessage ?: "로그인에 실패했습니다.")}"
                val js = """
                (function(){
                  var h = '#$errHash';
                  // 쿼리스트링 제거 (embed=app만 남기고 싶다면 필요한 값만 재조합하세요)
                  if (location.search) {
                    history.replaceState({}, '', location.origin + location.pathname + h);
                  } else {
                    history.pushState({}, '', h);
                  }
                  window.dispatchEvent(new Event('popstate'));
                })();
            """.trimIndent()

                if (WebBridge.listWebView != null) {
                    WebBridge.listWebView?.evaluateJavascript(js, null)
                } else {
                    // 초기 구동 폴백만 loadUrl
                    WebBridge.load(frontEntryUrl() + "#$errHash")
                }
                return@LaunchedEffect
            }

            val code = data.getQueryParameter("code")
            val bridgeUrl = if (!code.isNullOrEmpty())
                "$apiAuth/sso/bridge?code=$code"
            else
                frontEntryUrl()

            WebBridge.load(bridgeUrl)
        }
    }

    // FCM 알림 클릭: 상세 오버레이로 (큐잉, 폴백 로드)
    LaunchedEffect(pendingNavUrl) {
        val target = pendingNavUrl ?: return@LaunchedEffect
        android.util.Log.d("AuraScaffold", "FCM navigation to: $target")
        clearPendingNavUrl()
        WebBridge.requestOpenDetail(target)
    }

    AuraContainer(
        apiOrigin = apiOrigin,
        frontEntryUrl = frontEntryUrl()
    )
}