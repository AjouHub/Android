package com.sulhoe.aura.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** 네이티브 → 웹 이벤트 송신용 브리지 */
@SuppressLint("StaticFieldLeak")
object WebBridge {
    var listWebView: WebView? = null

    /** 하단 탭 전환: 이벤트만 송신, 실제 라우팅은 웹이 처리 */
    fun navigateTo(tab: String) {
        listWebView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('aura:navigate',{detail:{tab:'$tab'}}));",
            null
        )
    }

    /** 검색 입력/제출 이벤트 */
    fun searchChange(q: String) {
        listWebView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('aura:search-change',{detail:{q:${json(q)}}}));",
            null
        )
    }
    fun searchSubmit(q: String) {
        listWebView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('aura:search-submit',{detail:{q:${json(q)}}}));",
            null
        )
    }

    /** 인증 상태 변경 전파 (reauthenticating | success | failed | idle) */
    fun setAuthState(state: String) {
        listWebView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('aura:authstate-change',{detail:{state:'$state'}}));",
            null
        )
    }

    /** 목록 WebView에 특정 URL 로드 */
    fun load(url: String) {
        listWebView?.loadUrl(url)
    }

    /** 간단한 JS 문자열 이스케이프 */
    private fun json(s: String): String =
        "\"" + s.replace("\"", "\\\"") + "\""
}

/** 목록 WebView */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeListWebView(
    entryUrl: String,
    frontOrigin: String,
    apiOrigin: String,
    appScheme: String,
    onOpenNotice: (String) -> Unit,
    onProgress: (Float) -> Unit = {},
    onReauthRequest: (() -> Unit)? = null,
    onLogoutRequest: (() -> Unit)? = null,
    onOAuthRequest: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                WebBridge.listWebView = this

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString += " AURA-App"
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(object {
                    @JavascriptInterface fun openNotice(url: String) = onOpenNotice(url)
                    @JavascriptInterface fun reauth() { onReauthRequest?.invoke() }
                    @JavascriptInterface fun logout() { onLogoutRequest?.invoke() }
                }, "AURA")

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress.coerceIn(0, 100) / 100f)
                    }
                }

                webViewClient = object : WebViewClient() {

                    // ★ SPA 하드 리다이렉트 이중 차단
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url ?: return super.onPageStarted(view, url, favicon)
                        if (url.contains("/auth/google") || url.contains("accounts.google.com")) {
                            view?.stopLoading()
                            onOAuthRequest?.invoke()
                            return
                        }
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // OAuth 진입은 네이티브가 핸들
                        if (url.contains("/auth/google") || url.contains("accounts.google.com")) {
                            onOAuthRequest?.invoke()
                            return true
                        }

                        // 앱 딥링크
                        if (url.startsWith("$appScheme://")) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            return true
                        }

                        // 프론트/백엔드 내부 라우팅은 WebView가 처리
                        val isFront = url.startsWith(frontOrigin)
                        val isApi   = url.startsWith(apiOrigin)
                        if (isFront || isApi) return false

                        // 외부 링크는 브라우저
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                }

                loadUrl(entryUrl)
            }
        }
    )
}

/** 상세 WebView(오버레이) */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeDetailWebView(
    url: String,
    frontOrigin: String,
    apiOrigin: String,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val webRef = remember { mutableStateOf<WebView?>(null) }

    // 상세가 열려 있을 때 하드웨어 뒤로가기로 닫히도록
    BackHandler(enabled = true) {
        val wv = webRef.value
        if (wv?.canGoBack() == true) wv.goBack() else onClose()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webRef.value = this

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString += " AURA-App"
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {

                    // ★ 상세에서도 인증으로 튕기려 하면 즉시 닫고 목록에 제어권
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url ?: return super.onPageStarted(view, url, favicon)
                        if (url.contains("/auth/google") || url.contains("accounts.google.com")) {
                            view?.stopLoading()
                            onClose()
                            return
                        }
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val u = request?.url?.toString() ?: return false

                        if (u.contains("/auth/google") || u.contains("accounts.google.com")) {
                            onClose()
                            return true
                        }

                        val isFront = u.startsWith(frontOrigin)
                        val isApi   = u.startsWith(apiOrigin)
                        if (isFront || isApi) return false

                        // 상세 내부에서 외부 도메인은 외부 브라우저로
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {}
                loadUrl(url)
            }
        }
    )
}