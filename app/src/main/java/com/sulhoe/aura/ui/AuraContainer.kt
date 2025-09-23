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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private enum class AuthState(val wireValue: String) {
    Idle("idle"),
    Reauthenticating("reauthenticating"),
    Success("success"),
    Failed("failed")
}

@Composable
fun AuraContainer(
    frontOrigin: String, apiOrigin: String, appScheme: String, frontEntryUrl: String
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("home") }
    var detailUrl by remember { mutableStateOf<String?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var reauthInFlight by remember { mutableStateOf(false) }
    var reauthPendingOAuth by remember { mutableStateOf(false) }
    var reauthState by remember { mutableStateOf(AuthState.Idle) }
    var lastReauthAt by remember { mutableStateOf(0L) }
    var lastOAuthLaunchAt by remember { mutableStateOf(0L) }

    fun updateAuthState(state: AuthState) {
        if (reauthState == state) return
        reauthState = state
        WebBridge.setAuthState(state.wireValue)
    }

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
        if (reauthPendingOAuth && hasBackendSessionCookie()) {
            reauthPendingOAuth = false
        }

        val now = System.currentTimeMillis()
        if (reauthPendingOAuth && !hasBackendSessionCookie()) {
            updateAuthState(AuthState.Failed)
            openOAuthInCustomTab()
            return
        }

        if (reauthInFlight || now - lastReauthAt < 2000) return  // 쿨다운 2초로 증가
        reauthInFlight = true
        lastReauthAt = now

        // 1. 웹에게 "재인증 중" 상태 즉시 전파
        updateAuthState(AuthState.Reauthenticating)

        val sessionCookie = CookieManager.getInstance().getCookie("$apiOrigin/")
        if (sessionCookie.isNullOrBlank()) {
            updateAuthState(AuthState.Failed)
            reauthInFlight = false
            reauthPendingOAuth = true
            openOAuthInCustomTab()
            return
        }

        scope.launch {
            val refreshResult = withContext(Dispatchers.IO) {
                runCatching {
                    val url = URL("$apiOrigin/api/auth/refresh")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setFixedLengthStreamingMode(0)
                        setRequestProperty("Cookie", sessionCookie)
                        setRequestProperty("Accept", "*/*")
                    }

                    connection.outputStream.use { }
                    val responseCode = connection.responseCode
                    val setCookies = connection.headerFields["Set-Cookie"].orEmpty()
                    try {
                        connection.inputStream?.close()
                    } catch (_: Exception) {
                    }
                    connection.errorStream?.close()
                    connection.disconnect()

                    responseCode to setCookies
                }.getOrNull()
            }

            val (responseCode, setCookies) = refreshResult ?: (-1 to emptyList<String>())
            val cookieManager = CookieManager.getInstance()
            setCookies.forEach { cookieManager.setCookie(apiOrigin, it) }
            cookieManager.flush()

            val success = responseCode in 200..299 && hasBackendSessionCookie()
            if (success) {
                reauthPendingOAuth = false
                updateAuthState(AuthState.Success)
                WebBridge.listWebView?.postDelayed({ updateAuthState(AuthState.Idle) }, 500)
            } else {
                reauthPendingOAuth = true
                updateAuthState(AuthState.Failed)
                openOAuthInCustomTab()
            }

            reauthInFlight = false
        }
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
                onQueryChange = { q -> query = q; WebBridge.searchChange(q) },
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