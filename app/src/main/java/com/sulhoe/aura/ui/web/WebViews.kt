package com.sulhoe.aura.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
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
import java.io.ByteArrayInputStream

/* ===================== WebBridge ===================== */

@SuppressLint("StaticFieldLeak")
object WebBridge {
    var listWebView: WebView? = null

    /** 상세 오픈 훅(컨테이너가 등록) */
    var openDetail: ((String) -> Unit)? = null
        private set

    /** FCM 등에서 먼저 온 상세 요청을 보관하는 큐 */
    private var pendingDetailUrl: String? = null

    /** 상세 열기 요청(준비 전이면 큐에 저장) */
    fun requestOpenDetail(url: String) {
        val opener = openDetail
        if (opener != null) opener(url) else pendingDetailUrl = url
    }

    /** 컨테이너가 상세 오프너를 등록할 때 호출 */
    fun setDetailOpener(opener: (String) -> Unit) {
        openDetail = opener
        pendingDetailUrl?.let {
            opener(it)
            pendingDetailUrl = null
        }
    }

    fun clearDetailOpener() { openDetail = null }

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

/* ===================== 공통 유틸 ===================== */

private const val ABOUT_BLANK = "about:blank"
private fun Uri?.isHttpOrHttps(): Boolean =
    this != null && (scheme.equals("http", true) || scheme.equals("https", true))

/* ===================== 목록 WebView ===================== */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeListWebView(
    entryUrl: String,
    visible: Boolean,
    onUrlChanged: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
    onProgress: (Float) -> Unit = {},
    onReauthRequest: (() -> Unit)? = null,
    onLogoutRequest: (() -> Unit)? = null,
    onOAuthRequest: (() -> Unit)? = null,
    onLoadStateChange: (LoadState) -> Unit,
    onHandleReady: (WebViewHandle) -> Unit,
    onOnboardingComplete: (() -> Unit)? = null,
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

                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                settings.textZoom = 100

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(object {
                    @JavascriptInterface fun openNotice(url: String) = onOpenNotice(url)
                    @JavascriptInterface fun reauth() { onReauthRequest?.invoke() }
                    @JavascriptInterface fun logout() { onLogoutRequest?.invoke() }
                    @JavascriptInterface fun ensureUserTopic(email: String?) {
                        TopicManager.ensureUserTopic(context, email)
                    }
                    @JavascriptInterface fun applyTypeMode(type: String, mode: String) {
                        TopicManager.applyTypeMode(context, type, mode)
                    }
                    @JavascriptInterface fun routeChanged(url: String) { onUrlChanged(url) }
                    @JavascriptInterface fun onboardingComplete() {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onOnboardingComplete?.invoke()
                        }
                    }
                }, "AURA")

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress.coerceIn(0, 100) / 100f)
                    }
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                        msg?.let {
                            val tag = "WebView-Console"
                            val message = "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                            when (it.messageLevel()) {
                                android.webkit.ConsoleMessage.MessageLevel.ERROR ->
                                    android.util.Log.e(tag, message)
                                android.webkit.ConsoleMessage.MessageLevel.WARNING ->
                                    android.util.Log.w(tag, message)
                                else ->
                                    android.util.Log.d(tag, message)
                            }
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        val isMain = request?.isForMainFrame == true
                        if (isMain) return null

                        val headers = request?.requestHeaders ?: emptyMap()
                        val origin  = headers["Origin"] ?: "https://aura-front.code0.ai.kr"
                        // ✅ XHR/Fetch 추정 헤더
                        val isFetchOrXhr =
                            headers["Sec-Fetch-Dest"]?.equals("empty", true) == true ||
                                    headers["X-Requested-With"]?.equals("XMLHttpRequest", true) == true ||
                                    (headers["Accept"]?.contains("application/json", ignoreCase = true) == true)

                        if (isFetchOrXhr && (url.contains("/api/auth/google") || url.contains("accounts.google.com"))) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post { onOAuthRequest?.invoke() }
                            return WebResourceResponse(
                                "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                            ).apply {
                                responseHeaders = mapOf(
                                    "Access-Control-Allow-Origin" to origin,
                                    "Access-Control-Allow-Credentials" to "true",
                                    "Cache-Control" to "no-store",
                                    "Vary" to "Origin, Access-Control-Request-Method, Access-Control-Request-Headers"
                                )
                                setStatusCodeAndReasonPhrase(204, "No Content")
                            }
                        }

                        if (request?.method.equals("OPTIONS", true) && url.contains("/api/auth/google")) {
                            return WebResourceResponse(
                                "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                            ).apply {
                                responseHeaders = mapOf(
                                    "Access-Control-Allow-Origin" to origin,
                                    "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                                    "Access-Control-Allow-Headers" to "Content-Type, Authorization",
                                    "Access-Control-Allow-Credentials" to "true",
                                    "Vary" to "Origin, Access-Control-Request-Method, Access-Control-Request-Headers"
                                )
                                setStatusCodeAndReasonPhrase(204, "No Content")
                            }
                        }

                        return null
                    }

                    // 안전장치: 메인프레임으로 구글 이동 시도 차단
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url ?: return
                        if (url.contains("accounts.google.com") || url.contains("/api/auth/google")) {
                            android.util.Log.w("WebView", "⛔ Blocking in onPageStarted: $url")
                            view?.stopLoading()
                            onOAuthRequest?.invoke()
                            return
                        }

                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        hadMainFrameError = false
                        lastUrl = url
                        onUrlChanged(url)
                        onLoadStateChange(LoadState.Loading)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        if (!hadMainFrameError) onLoadStateChange(LoadState.Success)

                        // SPA 라우팅 감지 훅
                        val hook = """
                            (function(){
                              if (window.__AURA_HOOKED__) return;
                              window.__AURA_HOOKED__ = true;
                              const notify = () => { try { AURA.routeChanged(location.href); } catch(e){} };
                              ['pushState','replaceState'].forEach(function(fn){
                                const orig = history[fn];
                                history[fn] = function(){
                                  const ret = orig.apply(this, arguments);
                                  notify();
                                  return ret;
                                }
                              });
                              window.addEventListener('popstate', notify);
                              window.addEventListener('hashchange', notify); // 해시 라우트 변화도 추적
                              notify();
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(hook, null)
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        if (isClearingToBlank && url == ABOUT_BLANK) return
                        if (!hadMainFrameError) onLoadStateChange(LoadState.Success)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?, request: WebResourceRequest?, resp: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true) {
                            val url = request.url?.toString() ?: ""

                            // 구글 에러 페이지는 바로 차단 후 복구
                            if (url.contains("accounts.google.com")) {
                                android.util.Log.e("WebView", "⛔ Google error page blocked")
                                hadMainFrameError = true
                                isClearingToBlank = true
                                view?.stopLoading()
                                view?.loadUrl(ABOUT_BLANK)
                                view?.postDelayed({
                                    val fallbackUrl = if (!lastUrl.contains("accounts.google.com")) {
                                        lastUrl
                                    } else entryUrl
                                    WebBridge.load(fallbackUrl)
                                }, 500)
                                return
                            }

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
                        val urlStr = u.toString()
                        lastUrl = urlStr

                        if (urlStr.contains("accounts.google.com") || urlStr.contains("/api/auth/google")) {
                            android.util.Log.d("WebView", "⛔ Blocking in shouldOverrideUrlLoading: $urlStr")
                            onOAuthRequest?.invoke()
                            return true
                        }

                        if (!u.isHttpOrHttps()) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, u))
                            return true
                        }
                        return false
                    }
                }

                visibility = if (visible) View.VISIBLE else View.GONE

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
                onUrlChanged(entryUrl)
                loadUrl(entryUrl)
            }
        },
        update = { view ->
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }
    )
}

/* ===================== 상세 WebView ===================== */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeDetailWebView(
    url: String,
    visible: Boolean,
    onClose: () -> Unit,
    onLoadStateChange: (LoadState) -> Unit,
    onHandleReady: (WebViewHandle) -> Unit,
) {
    val ctx = LocalContext.current
    val webRef = remember { mutableStateOf<WebView?>(null) }

    var hadMainFrameError by remember { mutableStateOf(false) }
    var isClearingToBlank by remember { mutableStateOf(false) }
    var lastUrl by remember { mutableStateOf(url) }

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
                            view?.stopLoading()
                            onClose()
                            return
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
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, u))
                            return true
                        }
                        return false
                    }
                }

                webChromeClient = object : WebChromeClient() {}

                visibility = if (visible) View.VISIBLE else View.GONE

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
        },
        update = { view ->
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }
    )
}
