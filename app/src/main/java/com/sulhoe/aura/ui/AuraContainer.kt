package com.sulhoe.aura.ui

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
import com.sulhoe.aura.ui.components.AuraBottomBar
import com.sulhoe.aura.ui.components.AuraTopBar
import com.sulhoe.aura.ui.components.DelayPage
import com.sulhoe.aura.ui.components.ErrorPage
import com.sulhoe.aura.ui.web.NoticeDetailWebView
import com.sulhoe.aura.ui.web.NoticeListWebView
import com.sulhoe.aura.ui.web.WebBridge
import com.sulhoe.aura.ui.web.WebViewHandle

private const val ABOUT_BLANK = "about:blank"

@Composable
fun AuraContainer(
    apiOrigin: String,
    frontEntryUrl: String
) {
    val ctx = LocalContext.current

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

    Scaffold(
        topBar = {
            AuraTopBar(
                isSearching = isSearching,
                query = query,
                onSearchToggle = { open -> isSearching = open },
                onQueryChange = { q -> query = q },
                onSubmit = { q -> query = q; WebBridge.searchSubmit(q) }
            )
        },
        bottomBar = {
            AuraBottomBar(
                current = currentTab,
                onSelect = { tab -> currentTab = tab; WebBridge.navigateTo(tab) }
            )
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            NoticeListWebView(
                entryUrl = frontEntryUrl,
                onOpenNotice = { url -> detailUrl = url },
                onReauthRequest = { reauthFlow() },
                onLogoutRequest = { logoutFlow() },
                onOAuthRequest = { openOAuthInCustomTab() },
                onLoadStateChange = { st -> listLoadState = st },
                onHandleReady = { handle -> listHandle = handle },
            )
            if (detailUrl != null) {
                NoticeDetailWebView(
                    url = detailUrl!!,
                    onClose = { closeDetailAndEnsureListVisible() },
                    onLoadStateChange = { st -> detailLoadState = st },
                    onHandleReady = { handle -> detailHandle = handle },
                )
            }

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
