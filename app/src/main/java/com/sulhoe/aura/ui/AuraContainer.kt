package com.sulhoe.aura.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sulhoe.aura.ui.common.LoadState
import com.sulhoe.aura.ui.components.*
import com.sulhoe.aura.ui.web.*

private const val ABOUT_BLANK = "about:blank"

private fun shareLink(ctx: Context, link: String?, title: String = "AURA 공지 공유") {
    val safe = link?.trim().orEmpty()
    val uri = runCatching { Uri.parse(safe) }.getOrNull()
    if (uri == null || !(uri.scheme.equals("http", true) || uri.scheme.equals("https", true))) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, safe)
    }
    ctx.startActivity(Intent.createChooser(intent, "링크 공유"))
}

@Composable
fun AuraContainer(
    apiOrigin: String,
    frontEntryUrl: String
) {
    val ctx = LocalContext.current
    var isFullScreen by remember { mutableStateOf(false) }

    var currentTab by remember { mutableStateOf("home") }
    var detailUrl by remember { mutableStateOf<String?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var reauthInFlight by remember { mutableStateOf(false) }
    var lastReauthAt by remember { mutableStateOf(0L) }
    var lastOAuthLaunchAt by remember { mutableStateOf(0L) }

    var listLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var detailLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }

    var listHandle by remember { mutableStateOf<WebViewHandle?>(null) }
    var detailHandle by remember { mutableStateOf<WebViewHandle?>(null) }

    fun hasBackendSessionCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("$apiOrigin/") ?: return false
        return cookies.split(";").any { it.trim().startsWith("WEB_SESSION=") }
    }
    fun openOAuthInCustomTab() {
        val now = System.currentTimeMillis()
        if (now - lastOAuthLaunchAt < 6000) return
        lastOAuthLaunchAt = now
        val url = "$apiOrigin/api/auth/google?mode=app"
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, Uri.parse(url))
    }
    fun reauthFlow() {
        val now = System.currentTimeMillis()
        if (reauthInFlight || now - lastReauthAt < 2000) return
        reauthInFlight = true
        lastReauthAt = now
        WebBridge.setAuthState("reauthenticating")
        WebBridge.listWebView?.postUrl("$apiOrigin/api/auth/refresh", ByteArray(0))
        WebBridge.listWebView?.postDelayed({
            try {
                if (hasBackendSessionCookie()) {
                    WebBridge.setAuthState("success")
                    WebBridge.listWebView?.reload()
                } else {
                    WebBridge.setAuthState("failed")
                    openOAuthInCustomTab()
                }
            } finally {
                reauthInFlight = false
                WebBridge.listWebView?.postDelayed({ WebBridge.setAuthState("idle") }, 500)
            }
        }, 1000)
    }
    fun logoutFlow() {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            WebBridge.load(frontEntryUrl)
        }
    }
    fun closeDetailAndEnsureListVisible() {
        detailUrl = null
        detailLoadState = LoadState.Idle
        val current = listHandle?.currentUrl?.invoke()
        if (current.isNullOrBlank() || current == ABOUT_BLANK) {
            listLoadState = LoadState.Loading
            listHandle?.reload?.invoke()
        }
    }

    fun String?.isHttpUrl(): Boolean =
        !this.isNullOrBlank() && (this.startsWith("http://") || this.startsWith("https://"))

    /* 상세 오프너 등록: FCM 큐 소진까지 */
    DisposableEffect(Unit) {
        WebBridge.setDetailOpener { url -> detailUrl = url }
        onDispose { WebBridge.clearDetailOpener() }
    }

    val isDetailVisible = detailUrl != null
    val listVisible   = !isDetailVisible && (listLoadState is LoadState.Success)
    val detailVisible =  isDetailVisible && (detailLoadState is LoadState.Success)

    // 검색 지원 탭
    val searchSupported = currentTab == "home"

    val topBarMode = when {
        detailVisible -> TopBarMode.DETAIL
        listVisible && searchSupported -> TopBarMode.LIST
        else -> TopBarMode.OTHER
    }

    val effectiveSearching = isSearching && topBarMode == TopBarMode.LIST
    if (topBarMode != TopBarMode.LIST && isSearching) isSearching = false

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            if (!isFullScreen) {
                AuraTopBar(
                    mode = topBarMode,
                    isSearching = effectiveSearching,
                    query = query,
                    onSearchToggle = { open ->
                        if (topBarMode == TopBarMode.LIST) isSearching = open
                    },
                    onQueryChange = { q -> query = q },
                    onSubmit = { q -> query = q; WebBridge.searchSubmit(q) },
                    onShareClick = {
                        val current = detailHandle?.currentUrl?.invoke() ?: detailUrl
                        shareLink(ctx, current)
                    },
                    onBackClick = { closeDetailAndEnsureListVisible() }
                )
            }
        },
        bottomBar = {
            if (!isFullScreen) {
            AuraBottomBar(
                current = currentTab,
                onSelect = { tab ->
                    currentTab = tab
                    WebBridge.navigateTo(tab)
                }
            )
        }
        }
    ) { inner ->
        // ★ 풀스크린이면 inner padding 제거
        val contentPadding = if (isFullScreen) androidx.compose.foundation.layout.PaddingValues()
        else inner

        Box(Modifier.padding(contentPadding)) {

            // 목록 (SPA)
            NoticeListWebView(
                entryUrl = frontEntryUrl,
                visible = listVisible, // ★ 성공시에만 보이게
                onUrlChanged = { url ->
                    val u = runCatching { Uri.parse(url) }.getOrNull()
                    val path = u?.path.orEmpty()
                    val frag = u?.fragment.orEmpty()     // 해시 라우팅 대응

                    val looksLikeLoginRoute =
                        path.startsWith("/login") ||
                                frag.startsWith("/login") ||     // e.g. #/login
                                frag.contains("login")           // e.g. #/?view=login 등

                    // 홈('/')인데 아직 로그인 안 된 상태면 로그인 히어로 화면으로 간주 → 풀스크린
                    val isHomeAndUnauthed = (path.isEmpty() || path == "/") && !hasBackendSessionCookie()

                    isFullScreen = looksLikeLoginRoute || isHomeAndUnauthed
                },
                onOpenNotice = { url -> detailUrl = url },
                onReauthRequest = { reauthFlow() },
                onLogoutRequest = { logoutFlow() },
                onOAuthRequest = { openOAuthInCustomTab() },
                onLoadStateChange = { st -> listLoadState = st },
                onHandleReady = { handle -> listHandle = handle },
            )

            // 상세 (오버레이)
            if (detailUrl != null) {
                NoticeDetailWebView(
                    url = detailUrl!!,
                    visible = detailVisible, // ★ 성공시에만 보이게
                    onClose = { closeDetailAndEnsureListVisible() },
                    onLoadStateChange = { st -> detailLoadState = st },
                    onHandleReady = { handle -> detailHandle = handle },
                )
            }

            // 목록 로딩/에러 (상세 아닐 때)
            if (detailUrl == null) {
                when (val st = listLoadState) {
                    is LoadState.Loading, LoadState.Idle ->
                        DelayPage("목록 불러오는 중", "네트워크 상태에 따라 시간이 소요될 수 있습니다.")
                    is LoadState.Error ->
                        ErrorPage(
                            title = "목록을 불러올 수 없습니다",
                            message = "연결을 확인한 후 다시 시도해 주세요.",
                            onRetry = { listHandle?.reload?.invoke() }
                        )
                    else -> Unit
                }
            }

            // 상세 로딩/에러 (상세일 때)
            if (detailUrl != null) {
                when (val st = detailLoadState) {
                    is LoadState.Loading, LoadState.Idle ->
                        DelayPage("공지사항을 불러오는 중", "문서를 준비하고 있습니다…")
                    is LoadState.Error ->
                        ErrorPage(
                            title = "공지사항을 열 수 없습니다",
                            message = "네트워크 또는 원문 페이지 오류일 수 있습니다.",
                            onRetry = { detailHandle?.reload?.invoke() }
                        )
                    else -> Unit
                }
            }
        }
    }
}
