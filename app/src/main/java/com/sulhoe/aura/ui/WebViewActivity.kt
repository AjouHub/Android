// com/sulhoe/aura/ui/WebViewActivity.kt
package com.sulhoe.aura.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sulhoe.aura.R
import com.sulhoe.aura.ui.components.AuraTopBar
import com.sulhoe.aura.ui.theme.AURATheme
import kotlinx.coroutines.launch

class WebViewActivity : ComponentActivity() {

    // Compose에서 딥링크 처리를 위해 상태로 전달
    private val deepLinkState = mutableStateOf<Uri?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 최초 1회 인텐트의 data 반영
        deepLinkState.value = intent?.data

        setContent {
            AURATheme {
                WebViewScreen(
                    pendingDeepLink = deepLinkState.value,
                    clearPendingDeepLink = { deepLinkState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkState.value = intent.data
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewScreen(
    pendingDeepLink: Uri?,
    clearPendingDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // ------- URL/도메인 구성 ------
    val frontOrigin by remember {
        mutableStateOf(
            run {
                val u = Uri.parse(context.getString(R.string.frontend_url))
                "${u.scheme}://${u.authority}"
            }
        )
    }
    val apiOrigin by remember {
        mutableStateOf(
            run {
                val u = Uri.parse(context.getString(R.string.api_base_url))
                "${u.scheme}://${u.authority}"
            }
        )
    }
    val apiAuth by remember { mutableStateOf("$apiOrigin/api/auth") }
    fun frontEntryUrl(): String {
        val u = Uri.parse(context.getString(R.string.frontend_url))
        return u.buildUpon().appendQueryParameter("embed", "app").build().toString()
    }

    // ------- WebView 참조 & 상태 -------
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var reauthInFlight by remember { mutableStateOf(false) }
    var lastReauthAt by remember { mutableStateOf(0L) }

    // ------- 유틸 -------
    fun openCustomTab(url: String) {
        CustomTabsIntent.Builder().setShowTitle(true).build()
            .launchUrl(context, Uri.parse(url))
    }

    fun hasBackendSessionCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("$apiOrigin/") ?: return false
        return cookies.split(";").any { it.trim().startsWith("WEB_SESSION=") }
    }

    fun clearWebCookies(after: () -> Unit) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            after()
        }
    }

    fun reauthFlow() {
        val now = System.currentTimeMillis()
        if (reauthInFlight || now - lastReauthAt < 1200) return
        reauthInFlight = true
        lastReauthAt = now

        scope.launch {
            try {
                webView?.postUrl("$apiAuth/refresh", ByteArray(0))
            } catch (_: Throwable) { /* no-op */ }

            webView?.postDelayed({
                try {
                    if (hasBackendSessionCookie()) {
                        webView?.reload()
                    } else {
                        openCustomTab("$apiAuth/google?mode=app")
                    }
                } finally {
                    reauthInFlight = false
                }
            }, 600)
        }
    }

    fun logoutFlow() {
        clearWebCookies { webView?.loadUrl(frontEntryUrl()) }
    }

    // ------- 딥링크 처리 -------
    LaunchedEffect(pendingDeepLink) {
        val data = pendingDeepLink ?: return@LaunchedEffect
        clearPendingDeepLink()
        val scheme = context.getString(R.string.app_scheme)
        val host = context.getString(R.string.app_oauth_host)
        if (data.scheme == scheme && data.host == host) {
            val code = data.getQueryParameter("code")
            if (code.isNullOrEmpty()) {
                webView?.loadUrl(frontEntryUrl())
            } else {
                val bridgeUrl = "$apiAuth/sso/bridge?code=$code"
                webView?.loadUrl(bridgeUrl)
            }
        }
    }

    // ------- 뒤로가기 처리 -------
    BackHandler(enabled = canGoBack) {
        if (webView?.canGoBack() == true) webView?.goBack()
    }

    Scaffold(
        topBar = {
            AuraTopBar(
                onSearchClick = {
                    // (1) WebView에 검색 열기 신호 전송 (프론트가 처리하도록)
                    webView?.evaluateJavascript(
                        "window.postMessage('open-search')",
                        null
                    )
                    // 또는 특정 URL로 이동:
                    // webView?.loadUrl("${frontOrigin}/search?embed=app")
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {

            // 프로그레스 바 (페이지 로딩 시 상단 얇게 표시)
            if (progress in 0f..0.99f) {
                LinearProgressIndicator(modifier = Modifier)
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = "$userAgentString AURA-App"
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = when {
                                    newProgress <= 0 -> 0f
                                    newProgress >= 100 -> 1f
                                    else -> newProgress / 100f
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                req: WebResourceRequest?
                            ): Boolean {
                                val url = req?.url?.toString() ?: return false

                                // 1) 로그인 진입은 CustomTabs로: /api/auth/google?mode=app
                                if (url.contains("/auth/google")) {
                                    openCustomTab("$apiAuth/google?mode=app")
                                    return true
                                }
                                // 2) 구글 계정 페이지는 CustomTabs
                                if (url.contains("accounts.google.com")) {
                                    openCustomTab(url)
                                    return true
                                }
                                // 3) 앱 딥링크 → 앱 복귀
                                val appScheme = context.getString(R.string.app_scheme) + "://"
                                if (url.startsWith(appScheme)) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    return true
                                }
                                // 4) 외부 도메인은 외부 브라우저로
                                val isFront = url.startsWith(frontOrigin)
                                val isApi = url.startsWith(apiOrigin)
                                if (!isFront && !isApi) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    return true
                                }
                                return false
                            }
                        }

                        addJavascriptInterface(object {
                            @JavascriptInterface fun reauth() = reauthFlow()
                            @JavascriptInterface fun logout() = logoutFlow()
                            @JavascriptInterface
                            fun postMessage(message: String) {
                                val m = message.lowercase()
                                if (m.contains("reauth")) reauthFlow()
                                else if (m.contains("logout")) logoutFlow()
                            }
                        }, "AURA")

                        // 초기 진입
                        loadUrl(frontEntryUrl())
                    }
                },
                update = { wv ->
                    canGoBack = wv.canGoBack()
                }
            )
        }
    }
}
