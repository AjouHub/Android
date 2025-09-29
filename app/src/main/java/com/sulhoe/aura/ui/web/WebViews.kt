package com.sulhoe.aura.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.sulhoe.aura.fcm.TopicManager
import com.sulhoe.aura.ui.common.LoadState

@SuppressLint("StaticFieldLeak")
object WebBridge {
    var listWebView: WebView? = null

    // 네이티브가 상세 오픈을 요청할 때 호출할 훅 (AuraContainer가 등록)
    var openDetail: ((String) -> Unit)? = null

    fun navigateTo(tab: String) {
        listWebView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('aura:navigate',{detail:{tab:'$tab'}}));",
            null
        )
    }
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
    fun setAuthState(state: String) {
        listWebView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('aura:authstate-change',{detail:{state:'$state'}}));",
            null
        )
    }
    fun load(url: String) { listWebView?.loadUrl(url) }
    private fun json(s: String) = "\"" + s.replace("\"", "\\\"") + "\""
}

private const val ABOUT_BLANK = "about:blank"
private fun Uri?.isHttpOrHttps(): Boolean =
    this != null && (scheme.equals("http", true) || scheme.equals("https", true))

/* ----------------------- 목록 ----------------------- */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeListWebView(
    entryUrl: String,
    onOpenNotice: (String) -> Unit,
    onProgress: (Float) -> Unit = {},
    onReauthRequest: (() -> Unit)? = null,
    onLogoutRequest: (() -> Unit)? = null,
    onOAuthRequest: (() -> Unit)? = null,
    onLoadStateChange: (LoadState) -> Unit,
    onHandleReady: (WebViewHandle) -> Unit,
) {
    val ctx = LocalContext.current

    var hadMainFrameError by remember { mutableStateOf(false) }
    var isClearingToBlank by remember { mutableStateOf(false) }
    var lastUrl by remember { mutableStateOf(entryUrl) }

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

                    // settings 라우트에서 호출
                    @JavascriptInterface fun ensureUserTopic(userId: Long) {
                        TopicManager.ensureUserTopic(context, userId)
                    }
                    @JavascriptInterface fun applyTypeMode(type: String, mode: String) {
                        TopicManager.applyTypeMode(context, type, mode)
                    }
                }, "AURA")

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress.coerceIn(0, 100) / 100f)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url ?: return
                        if (url.contains("/auth/google") || url.contains("accounts.google.com")) {
                            view?.stopLoading(); onOAuthRequest?.invoke(); return
                        }
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        hadMainFrameError = false
                        lastUrl = url
                        onLoadStateChange(LoadState.Loading)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        if (!hadMainFrameError) onLoadStateChange(LoadState.Success)
                    }
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        if (!hadMainFrameError) onLoadStateChange(LoadState.Success)
                    }
                    override fun onReceivedHttpError(
                        view: WebView?, request: WebResourceRequest?, resp: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true) {
                            hadMainFrameError = true
                            isClearingToBlank = true
                            view?.stopLoading()
                            view?.loadUrl(ABOUT_BLANK)
                            onLoadStateChange(
                                LoadState.Error(
                                    statusCode = resp?.statusCode,
                                    message = resp?.reasonPhrase ?: "HTTP 오류",
                                    failingUrl = request.url?.toString()
                                )
                            )
                        }
                    }
                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?, err: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            hadMainFrameError = true
                            isClearingToBlank = true
                            view?.stopLoading()
                            view?.loadUrl(ABOUT_BLANK)
                            onLoadStateChange(
                                LoadState.Error(
                                    message = err?.description?.toString() ?: "페이지 로드 오류",
                                    failingUrl = request.url?.toString()
                                )
                            )
                        }
                    }
                    @Deprecated("for < M")
                    override fun onReceivedError(
                        view: WebView?, code: Int, desc: String?, failingUrl: String?
                    ) {
                        hadMainFrameError = true
                        isClearingToBlank = true
                        view?.stopLoading()
                        view?.loadUrl(ABOUT_BLANK)
                        onLoadStateChange(
                            LoadState.Error(
                                statusCode = code,
                                message = desc ?: "페이지 로드 오류",
                                failingUrl = failingUrl
                            )
                        )
                    }
                    override fun onReceivedSslError(
                        view: WebView?, handler: SslErrorHandler?, error: SslError?
                    ) {
                        hadMainFrameError = true
                        isClearingToBlank = true
                        handler?.cancel()
                        view?.stopLoading()
                        view?.loadUrl(ABOUT_BLANK)
                        onLoadStateChange(
                            LoadState.Error(message = "SSL 오류", failingUrl = error?.url)
                        )
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url ?: return false
                        lastUrl = u.toString()
                        if (!u.isHttpOrHttps()) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, u)); return true
                        }
                        return false
                    }
                }

                val self = this
                onHandleReady(
                    WebViewHandle(
                        reload = {
                            self.post {
                                isClearingToBlank = false
                                val target = lastUrl
                                if (target.isNotEmpty() && target != ABOUT_BLANK) self.loadUrl(target)
                                else self.reload()
                            }
                        },
                        currentUrl = { self.url }
                    )
                )

                onLoadStateChange(LoadState.Loading)
                loadUrl(entryUrl)
            }
        }
    )
}

/* ----------------------- 상세 ----------------------- */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeDetailWebView(
    url: String,
    onClose: () -> Unit,
    onLoadStateChange: (LoadState) -> Unit,
    onHandleReady: (WebViewHandle) -> Unit,
) {
    val ctx = LocalContext.current
    val webRef = remember { mutableStateOf<WebView?>(null) }

    var hadMainFrameError by remember { mutableStateOf(false) }
    var isClearingToBlank by remember { mutableStateOf(false) }
    var lastUrl by remember { mutableStateOf(url) }

    // 상세는 "뒤로가기 = 항상 닫기"
    BackHandler(enabled = true) { onClose() }

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
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url ?: return
                        if (url.contains("/auth/google") || url.contains("accounts.google.com")) {
                            view?.stopLoading(); onClose(); return
                        }
                        if (isClearingToBlank && url == ABOUT_BLANK) return

                        hadMainFrameError = false
                        lastUrl = url
                        onLoadStateChange(LoadState.Loading)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        if (!hadMainFrameError) onLoadStateChange(LoadState.Success)
                    }
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        if (!hadMainFrameError) onLoadStateChange(LoadState.Success)
                    }

                    private fun showError(view: WebView?, status: Int?, msg: String?, failing: String?) {
                        hadMainFrameError = true
                        isClearingToBlank = true
                        view?.stopLoading()
                        view?.loadUrl(ABOUT_BLANK)
                        // 🔒 흰페이지가 '뒤로가기 대상'이 안 되도록 즉시 역사 제거
                        view?.clearHistory()
                        onLoadStateChange(
                            LoadState.Error(
                                statusCode = status,
                                message = msg ?: "페이지 로드 오류",
                                failingUrl = failing
                            )
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView?, request: WebResourceRequest?, resp: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true) {
                            showError(view, resp?.statusCode, resp?.reasonPhrase ?: "HTTP 오류", request.url?.toString())
                        }
                    }
                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?, err: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            showError(view, null, err?.description?.toString(), request.url?.toString())
                        }
                    }
                    @Deprecated("for < M")
                    override fun onReceivedError(
                        view: WebView?, code: Int, desc: String?, failingUrl: String?
                    ) {
                        showError(view, code, desc, failingUrl)
                    }
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.cancel()
                        showError(view, null, "SSL 오류", error?.url)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url ?: return false
                        lastUrl = u.toString()
                        if (!u.isHttpOrHttps()) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, u)); return true
                        }
                        return false
                    }
                }

                webChromeClient = object : WebChromeClient() {}

                val self = this
                onHandleReady(
                    WebViewHandle(
                        reload = {
                            self.post {
                                isClearingToBlank = false
                                val target = lastUrl
                                if (target.isNotEmpty() && target != ABOUT_BLANK) self.loadUrl(target)
                                else self.reload()
                            }
                        },
                        currentUrl = { self.url }
                    )
                )

                onLoadStateChange(LoadState.Loading)
                loadUrl(url)
            }
        }
    )
}
