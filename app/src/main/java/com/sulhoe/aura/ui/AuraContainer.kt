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

    var isLoginRoute by remember { mutableStateOf(false) }
    var isOnboardingRoute by remember { mutableStateOf(false) }

    var reauthInFlight by remember { mutableStateOf(false) }
    var lastReauthAt by remember { mutableStateOf(0L) }
    var lastOAuthLaunchAt by remember { mutableStateOf(0L) }

    var listLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var detailLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }

    var listHandle by remember { mutableStateOf<WebViewHandle?>(null) }
    var detailHandle by remember { mutableStateOf<WebViewHandle?>(null) }

    // 1. hasBackendSessionCookie를 먼저 정의
    fun hasBackendSessionCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("$apiOrigin/") ?: ""
        val hasCookie = cookies.split(";").any { it.trim().startsWith("WEB_SESSION=") }

        android.util.Log.d("AuraContainer", "Session cookie check: $hasCookie (cookies: ${cookies.take(100)}...)")
        return hasCookie
    }

    // onUrlChanged 로직 개선
    fun updateFullScreenState(url: String) {
        android.util.Log.d("AuraContainer", "URL changed: $url")
        val u = runCatching { Uri.parse(url) }.getOrNull()
        val path = u?.path.orEmpty()
        val frag = u?.fragment.orEmpty()
        val query = u?.query.orEmpty()

        android.util.Log.d("AuraContainer", "Parsed - path: $path, fragment: $frag, query: $query")

        // 해시 라우터 우선
        val route = if (frag.isNotBlank()) frag else path

        // TopBar 모드/BottomBar 가시성 판단용 라우트 플래그
        val looksLikeLoginRoute = route.startsWith("/login") || route.contains("login")

        // 온보딩 화면 감지 추가
        val looksLikeOnboarding = route.contains("/select-department")

        val hasSession = hasBackendSessionCookie()
        val isHomeAndUnauthed = ((path.isEmpty() || path == "/") && frag.isBlank()) && !hasSession

        // 실제 앱 내부 라우트 진입 여부
        val insideApp = route.startsWith("/notice") ||
                route.startsWith("/bookmark") ||
                route.startsWith("/settings")

        // 현재 탭 동기화 (BottomBar 하이라이트를 라우트 기반으로)
        val newTab = when {
            route.startsWith("/bookmark") -> "bookmark"
            route.startsWith("/settings") -> "settings"
            else -> "home" // /notice 및 기타 기본은 home 취급
        }
        if (newTab != currentTab) currentTab = newTab

        // TopBar 모드 계산에 쓰려는 라우트 플래그 저장
        isLoginRoute = looksLikeLoginRoute
        isOnboardingRoute = looksLikeOnboarding

        android.util.Log.d("AuraContainer", "Flags - login:$looksLikeLoginRoute, onboarding:$looksLikeOnboarding, session:$hasSession, homeUnauth:$isHomeAndUnauthed")

        // BottomBar 숨김 여부 (로그인/온보딩 혹은 비인증 홈일 때만 숨김)
        val newFullScreen = (looksLikeLoginRoute || looksLikeOnboarding || (isHomeAndUnauthed && !insideApp)) && !hasSession

        if (newFullScreen != isFullScreen) {
            android.util.Log.d("AuraContainer", "FullScreen changed: $isFullScreen -> $newFullScreen")
            isFullScreen = newFullScreen
        }
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
        val cookieManager = CookieManager.getInstance()
        val logoutUrl = "$apiOrigin/api/auth/logout"

        // 1. 백엔드 로그아웃
        WebBridge.listWebView?.postUrl(logoutUrl, ByteArray(0))

        WebBridge.listWebView?.postDelayed({
            // 2. 모든 쿠키 삭제
            cookieManager.removeAllCookies { success ->
                android.util.Log.d("AuraContainer", "All cookies removed: $success")

                cookieManager.removeSessionCookies { sessionSuccess ->
                    android.util.Log.d("AuraContainer", "Session cookies removed: $sessionSuccess")

                    // 3. 디스크 동기화
                    cookieManager.flush()

                    // 4. WebView 데이터 완전 삭제
                    android.webkit.WebStorage.getInstance().deleteAllData()

                    // 5. 캐시 삭제
                    WebBridge.listWebView?.clearCache(true)
                    WebBridge.listWebView?.clearFormData()
                    WebBridge.listWebView?.clearHistory()

                    // 6. 쿠키 삭제 후 다시 한번 flush
                    cookieManager.flush()

                    // 7. 화면 전환
                    WebBridge.listWebView?.postDelayed({
                        // 검증
                        val cookies = cookieManager.getCookie("$apiOrigin/") ?: "EMPTY"
                        android.util.Log.e("AuraContainer", "Final cookies after logout: $cookies")

                        WebBridge.load(frontEntryUrl)
                        isFullScreen = true
                    }, 500) // 더 긴 대기 시간
                }
            }
        }, 800) // 백엔드 요청 완료 대기
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

    // 검색 지원 탭 (home만 검색 허용)
    val searchSupported = (currentTab == "home")

    // TopBar 모드: 로그인/온보딩/검색미지원 탭/로딩 중에는 OTHER(비활성), 상세는 DETAIL, 그 외 LIST
    val topBarMode = when {
        detailVisible -> TopBarMode.DETAIL
        isLoginRoute || isOnboardingRoute || !searchSupported || (listLoadState !is LoadState.Success) -> TopBarMode.OTHER
        else -> TopBarMode.LIST
    }

    val effectiveSearching = isSearching && topBarMode == TopBarMode.LIST
    if (topBarMode != TopBarMode.LIST && isSearching) isSearching = false

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            // TopBar는 항상 렌더링 (로그인 화면 포함)
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
        },
        bottomBar = {
            // BottomBar는 로그인/온보딩/비인증 홈에서만 숨김
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
                onUrlChanged = { url -> updateFullScreenState(url) },
                onOpenNotice = { url -> detailUrl = url },
                onReauthRequest = { reauthFlow() },
                onLogoutRequest = { logoutFlow() },
                onOAuthRequest = { openOAuthInCustomTab() },
                onLoadStateChange = { st -> listLoadState = st },
                onHandleReady = { handle -> listHandle = handle },
                // 온보딩 완료 콜백 추가
                onOnboardingComplete = {
                    if (hasBackendSessionCookie()) {
                        isFullScreen = false
                        // reload 대신 깨끗한 URL로 이동
                        val cleanUrl = "$frontEntryUrl#/notice" // signUp 쿼리 없이
                        listHandle?.currentUrl?.let { getCurrentUrl ->
                            val current = getCurrentUrl()
                            // 현재 URL에 signUp이 있으면 제거
                            if (current?.contains("signUp=true") == true) {
                                WebBridge.listWebView?.post {
                                    WebBridge.listWebView?.loadUrl(cleanUrl)
                                }
                            }
                        }
                    }
                }
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
