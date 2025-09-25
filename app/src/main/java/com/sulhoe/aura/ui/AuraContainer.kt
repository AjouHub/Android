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
import com.sulhoe.aura.ui.components.AuraBottomBar
import com.sulhoe.aura.ui.components.AuraTopBar
import com.sulhoe.aura.ui.web.NoticeDetailWebView
import com.sulhoe.aura.ui.web.NoticeListWebView
import com.sulhoe.aura.ui.web.WebBridge

@Composable
fun AuraContainer(
    frontOrigin: String, apiOrigin: String, appScheme: String, frontEntryUrl: String
) {
    val ctx = LocalContext.current

    var currentTab by remember { mutableStateOf("home") }
    var detailUrl by remember { mutableStateOf<String?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var reauthInFlight by remember { mutableStateOf(false) }
    var lastReauthAt by remember { mutableStateOf(0L) }
    var lastOAuthLaunchAt by remember { mutableStateOf(0L) }

    fun hasBackendSessionCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("$apiOrigin/") ?: return false
        return cookies.split(";").any { it.trim().startsWith("WEB_SESSION=") }
    }

    fun openOAuthInCustomTab() {
        val now = System.currentTimeMillis()
        if (now - lastOAuthLaunchAt < 6000) return
        lastOAuthLaunchAt = now
        val url = "$apiOrigin/api/auth/google?mode=app"
        CustomTabsIntent.Builder().setShowTitle(true).build()
            .launchUrl(ctx, Uri.parse(url))
    }

    // [수정됨] 상태 기반의 재인증 로직
    fun reauthFlow() {
        val now = System.currentTimeMillis()
        if (reauthInFlight || now - lastReauthAt < 2000) return  // 쿨다운 2초로 증가
        reauthInFlight = true
        lastReauthAt = now

        // 1. 웹에게 "재인증 중" 상태 즉시 전파
        WebBridge.setAuthState("reauthenticating")

        // 2. 백그라운드에서 토큰 갱신 시도
        WebBridge.listWebView?.postUrl("$apiOrigin/api/auth/refresh", ByteArray(0))

        // 3. 1초 후 쿠키 상태 확인 (네트워크 지연 고려)
        WebBridge.listWebView?.postDelayed({
            try {
                if (hasBackendSessionCookie()) {
                    // 3a. 성공: 웹에게 알리고 리로드하여 새 세션 적용
                    WebBridge.setAuthState("success")
                    WebBridge.listWebView?.reload()
                } else {
                    // 3b. 실패: 웹에게 알리고 OAuth 열기
                    WebBridge.setAuthState("failed")
                    openOAuthInCustomTab()
                }
            } finally {
                // 4. 모든 과정이 끝나면 플래그 해제하고, 잠시 후 상태를 'idle'로 복귀
                reauthInFlight = false
                WebBridge.listWebView?.postDelayed({ WebBridge.setAuthState("idle") }, 500)
            }
        }, 1000) // 쿠키 정착 및 네트워크 시간 대기를 1초로 조정
    }

    fun logoutFlow() {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            WebBridge.load(frontEntryUrl)
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
                frontOrigin = frontOrigin,
                apiOrigin = apiOrigin,
                appScheme = appScheme,
                onOpenNotice = { url -> detailUrl = url },
                onReauthRequest = { reauthFlow() },
                onLogoutRequest = { logoutFlow() },
                onOAuthRequest = { openOAuthInCustomTab() }
            )
            if (detailUrl != null) {
                NoticeDetailWebView(
                    url = detailUrl!!,
                    frontOrigin = frontOrigin,
                    apiOrigin = apiOrigin,
                    onClose = { detailUrl = null }
                )
            }
        }
    }
}