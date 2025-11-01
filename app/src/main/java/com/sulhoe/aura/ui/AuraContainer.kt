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

/**
 * 링크를 공유하는 헬퍼 함수
 * @param ctx Context
 * @param link 공유할 링크 URL
 * @param title 공유 제목
 */
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

/**
 * AURA 앱의 메인 컨테이너
 * WebView 기반 SPA를 감싸며, 상단바/하단바/로딩/에러 화면을 관리
 *
 * @param apiOrigin API 서버 주소 (예: https://api.example.com)
 * @param frontEntryUrl 프론트엔드 진입 URL
 */
@Composable
fun AuraContainer(
    apiOrigin: String,
    frontEntryUrl: String
) {
    val ctx = LocalContext.current

    /**
     * 백엔드 세션 쿠키 존재 여부 확인
     * @return 세션 쿠키가 있으면 true, 없으면 false
     */
    // hasBackendSessionCookie 함수를 먼저 선언 (초기화에서 사용하기 위해)
    fun hasBackendSessionCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("$apiOrigin/") ?: ""
        val hasCookie = cookies.split(";").any { it.trim().startsWith("WEB_SESSION=") }
        android.util.Log.d("AuraContainer", "Session cookie check: $hasCookie (cookies: ${cookies.take(100)}...)")
        return hasCookie
    }

    // ========== UI 상태 관리 ==========
    // 초기 상태: 쿠키가 있으면 false(하단바 표시), 없으면 true(전체화면)
    var isFullScreen by remember {
        mutableStateOf(!hasBackendSessionCookie())  // 쿠키 있으면 false, 없으면 true
    }  // 전체화면 모드 (로그인/온보딩 시)
    var currentTab by remember { mutableStateOf("home") }    // 현재 선택된 하단 탭
    var detailUrl by remember { mutableStateOf<String?>(null) } // 상세 페이지 URL (null이면 목록 화면)
    var aboutUrl by remember { mutableStateOf<String?>(null) }  // About 페이지 URL (null이면 닫힌 상태)

    // 초기 쿠키 상태 로깅
    LaunchedEffect(Unit) {
        val hasSession = hasBackendSessionCookie()
        android.util.Log.d("AuraContainer", "Initial state - hasSession: $hasSession, isFullScreen: $isFullScreen")
    }

    // ========== 검색 상태 관리 ==========
    var isSearching by remember { mutableStateOf(false) }   // 검색 모드 활성화 여부
    var query by remember { mutableStateOf("") }            // 검색어

    // ========== 라우트 상태 관리 ==========
    var isLoginRoute by remember { mutableStateOf(false) }        // 로그인 화면 여부
    var isOnboardingRoute by remember { mutableStateOf(false) }   // 온보딩 화면 여부

    // ========== OAuth 및 인증 상태 관리 ==========
    var reauthInFlight by remember { mutableStateOf(false) }      // 재인증 진행 중 플래그
    var lastReauthAt by remember { mutableStateOf(0L) }           // 마지막 재인증 시도 시각
    var lastOAuthLaunchAt by remember { mutableStateOf(0L) }      // 마지막 OAuth 실행 시각

    // ========== WebView 로드 상태 관리 ==========
    var listLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }     // 목록 WebView 로드 상태
    var detailLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }   // 상세 WebView 로드 상태
    var aboutLoadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }    // About WebView 로드 상태

    // ========== WebView 핸들 관리 ==========
    var listHandle by remember { mutableStateOf<WebViewHandle?>(null) }     // 목록 WebView 제어 핸들
    var detailHandle by remember { mutableStateOf<WebViewHandle?>(null) }   // 상세 WebView 제어 핸들
    var aboutHandle by remember { mutableStateOf<WebViewHandle?>(null) }    // About WebView 제어 핸들


    // 목록 로드 성공 시 전체화면 상태 재확인
    LaunchedEffect(listLoadState) {
        if (listLoadState is LoadState.Success && isFullScreen) {
            val hasSession = hasBackendSessionCookie()
            if (hasSession) {
                android.util.Log.d("AuraContainer", "List loaded with session - disabling fullscreen")
                isFullScreen = false
            }
        }
    }

    /**
     * URL 변경 시 호출되어 전체화면 상태 및 현재 탭을 업데이트
     * 라우트 분석을 통해 로그인/온보딩/메인 화면을 구분
     *
     * @param url 변경된 URL
     */
    fun updateFullScreenState(url: String) {
        android.util.Log.d("AuraContainer", "URL changed: $url")
        val u = runCatching { Uri.parse(url) }.getOrNull()
        val path = u?.path.orEmpty()
        val frag = u?.fragment.orEmpty()
        val query = u?.query.orEmpty()

        android.util.Log.d("AuraContainer", "Parsed - path: $path, fragment: $frag, query: $query")

        // 해시 라우터 우선 (SPA는 주로 해시 기반 라우팅 사용)
        val route = if (frag.isNotBlank()) frag else path

        // 라우트 플래그 계산
        val looksLikeLoginRoute = route.startsWith("/login") || route.contains("login")
        val looksLikeOnboarding = route.contains("/select-department")
        val hasSession = hasBackendSessionCookie()
        val isHomeAndUnauthed = ((path.isEmpty() || path == "/") && frag.isBlank()) && !hasSession

        // 실제 앱 내부 라우트 진입 여부
        val insideApp = route.startsWith("/notice") ||
                route.startsWith("/bookmark") ||
                route.startsWith("/settings")

        // 현재 탭 동기화 (하단바 하이라이트용)
        val newTab = when {
            route.startsWith("/bookmark") -> "bookmark"
            route.startsWith("/settings") -> "settings"
            else -> "home" // /notice 및 기타는 home으로 취급
        }
        if (newTab != currentTab) currentTab = newTab

        // TopBar 모드 계산용 플래그 저장
        isLoginRoute = looksLikeLoginRoute
        isOnboardingRoute = looksLikeOnboarding

        android.util.Log.d("AuraContainer",
            "Flags - login:$looksLikeLoginRoute, onboarding:$looksLikeOnboarding, " +
                    "session:$hasSession, homeUnauth:$isHomeAndUnauthed")

        // 전체화면 모드 판단: 로그인/온보딩 또는 비인증 홈일 때만 하단바 숨김
        val newFullScreen = (looksLikeLoginRoute || looksLikeOnboarding ||
                (isHomeAndUnauthed && !insideApp)) && !hasSession

        if (newFullScreen != isFullScreen) {
            android.util.Log.d("AuraContainer", "FullScreen changed: $isFullScreen -> $newFullScreen")
            isFullScreen = newFullScreen
        }
    }

    /**
     * Google OAuth를 Custom Tab에서 실행
     * 중복 실행 방지를 위해 6초 간격 제한
     */
    fun openOAuthInCustomTab() {
        val now = System.currentTimeMillis()
        if (now - lastOAuthLaunchAt < 6000) return  // 6초 이내 중복 실행 방지
        lastOAuthLaunchAt = now
        val url = "$apiOrigin/api/auth/google?mode=app"
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, Uri.parse(url))
    }

    /**
     * 재인증 플로우 실행
     * 1. 백엔드 /api/auth/refresh 호출
     * 2. 세션 쿠키 확인
     * 3. 성공 시 페이지 리로드, 실패 시 OAuth 실행
     */
    fun reauthFlow() {
        val now = System.currentTimeMillis()
        if (reauthInFlight || now - lastReauthAt < 2000) return  // 2초 이내 중복 방지
        reauthInFlight = true
        lastReauthAt = now

        // 프론트엔드에 재인증 상태 알림
        WebBridge.setAuthState("reauthenticating")

        // 백엔드 refresh 엔드포인트 호출
        WebBridge.listWebView?.postUrl("$apiOrigin/api/auth/refresh", ByteArray(0))

        // 1초 후 세션 확인
        WebBridge.listWebView?.postDelayed({
            try {
                if (hasBackendSessionCookie()) {
                    // 재인증 성공
                    WebBridge.setAuthState("success")
                    WebBridge.listWebView?.reload()
                } else {
                    // 재인증 실패 → OAuth로 이동
                    WebBridge.setAuthState("failed")
                    openOAuthInCustomTab()
                }
            } finally {
                reauthInFlight = false
                // 0.5초 후 상태를 idle로 복구
                WebBridge.listWebView?.postDelayed({ WebBridge.setAuthState("idle") }, 500)
            }
        }, 1000)
    }

    /**
     * 로그아웃 플로우 실행
     * 1. 백엔드 로그아웃 API 호출
     * 2. 모든 쿠키 삭제
     * 3. WebView 데이터 초기화
     * 4. 로그인 화면으로 이동
     */
    fun logoutFlow() {
        val cookieManager = CookieManager.getInstance()
        val logoutUrl = "$apiOrigin/api/auth/logout"

        // 1. 백엔드 로그아웃 API 호출
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

                    // 5. 캐시 및 기록 삭제
                    WebBridge.listWebView?.clearCache(true)
                    WebBridge.listWebView?.clearFormData()
                    WebBridge.listWebView?.clearHistory()

                    // 6. 쿠키 삭제 후 다시 한번 flush
                    cookieManager.flush()

                    // 7. 로그인 화면으로 이동
                    WebBridge.listWebView?.postDelayed({
                        // 최종 쿠키 확인 (디버깅용)
                        val cookies = cookieManager.getCookie("$apiOrigin/") ?: "EMPTY"
                        android.util.Log.e("AuraContainer", "Final cookies after logout: $cookies")

                        WebBridge.load(frontEntryUrl)
                        isFullScreen = true
                    }, 500)
                }
            }
        }, 800) // 백엔드 요청 완료 대기
    }

    /**
     * 상세 페이지를 닫고 목록으로 돌아감
     * 목록이 비어있으면 리로드
     */
    fun closeDetailAndEnsureListVisible() {
        detailUrl = null
        detailLoadState = LoadState.Idle
        val current = listHandle?.currentUrl?.invoke()

        // 목록 WebView가 비어있으면 리로드
        if (current.isNullOrBlank() || current == ABOUT_BLANK) {
            listLoadState = LoadState.Loading
            listHandle?.reload?.invoke()
        }
    }

    /**
     * URL이 HTTP/HTTPS인지 확인하는 확장 함수
     */
    fun String?.isHttpUrl(): Boolean =
        !this.isNullOrBlank() && (this.startsWith("http://") || this.startsWith("https://"))

    // FCM 알림 클릭 시 상세 페이지 열기 핸들러 등록
    DisposableEffect(Unit) {
        WebBridge.setDetailOpener { url -> detailUrl = url }
        onDispose { WebBridge.clearDetailOpener() }
    }

    // ========== UI 가시성 제어 ==========
    val isDetailVisible = detailUrl != null  // 상세 화면이 열려있는지
    val isAboutVisible = aboutUrl != null    // About 화면이 열려있는지
    val listVisible = !isDetailVisible && !isAboutVisible && (listLoadState is LoadState.Success)      // 목록 WebView 표시 여부
    val detailVisible = isDetailVisible && !isAboutVisible && (detailLoadState is LoadState.Success)   // 상세 WebView 표시 여부
    val aboutVisible = isAboutVisible && (aboutLoadState is LoadState.Success)      // About WebView 표시 여부

    // ========== TopBar 모드 계산 ==========
    val searchSupported = (currentTab == "home")  // 검색은 home 탭에서만 지원

    // TopBar 모드 결정
    val topBarMode = when {
        detailVisible || aboutVisible -> TopBarMode.DETAIL  // 상세 화면
        isLoginRoute || isOnboardingRoute || !searchSupported ||
                (listLoadState !is LoadState.Success) -> TopBarMode.OTHER  // 비활성 모드
        else -> TopBarMode.LIST  // 목록 화면
    }

    // 검색 모드는 LIST 모드에서만 활성화
    val effectiveSearching = isSearching && topBarMode == TopBarMode.LIST
    if (topBarMode != TopBarMode.LIST && isSearching) isSearching = false

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            // TopBar는 항상 표시 (로그인 화면 포함)
            AuraTopBar(
                mode = topBarMode,
                isSearching = effectiveSearching,
                query = query,
                onSearchToggle = { open ->
                    if (topBarMode == TopBarMode.LIST) isSearching = open
                },
                onQueryChange = { q -> query = q },
                onSubmit = { q ->
                    query = q
                    WebBridge.searchSubmit(q)
                },
                onShareClick = {
                    // About 페이지일 때는 aboutUrl, 상세일 때는 detailUrl 공유
                    val current = when {
                        aboutVisible -> aboutUrl
                        detailVisible -> detailHandle?.currentUrl?.invoke() ?: detailUrl
                        else -> null
                    }
//                    val current = detailHandle?.currentUrl?.invoke() ?: detailUrl
                    shareLink(ctx, current)
                },
                onBackClick = {
                    // About 페이지가 열려있으면 닫기, 아니면 상세 닫기
                    if (aboutVisible) {
                        aboutUrl = null
                        aboutLoadState = LoadState.Idle
                    } else {
                        closeDetailAndEnsureListVisible()
                    }
                }
            )
        },
        bottomBar = {
            // BottomBar는 전체화면 모드(로그인/온보딩)에서만 숨김
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
        // 전체화면 모드에서는 padding 제거
        val contentPadding = if (isFullScreen)
            androidx.compose.foundation.layout.PaddingValues()
        else
            inner

        Box(Modifier.padding(contentPadding)) {

            // ========== 목록 WebView (SPA) ==========
            NoticeListWebView(
                entryUrl = frontEntryUrl,
                visible = listVisible,  // 성공 상태에서만 표시
                onUrlChanged = { url -> updateFullScreenState(url) },
                onOpenNotice = { url -> detailUrl = url },
                onOpenAbout = { url -> aboutUrl = url },
                onReauthRequest = { reauthFlow() },
                onLogoutRequest = { logoutFlow() },
                onOAuthRequest = { openOAuthInCustomTab() },
                onLoadStateChange = { st -> listLoadState = st },
                onHandleReady = { handle -> listHandle = handle },
                onOnboardingComplete = {
                    // 온보딩 완료 시 하단바 표시 및 깨끗한 URL로 이동
                    if (hasBackendSessionCookie()) {
                        isFullScreen = false
                        val cleanUrl = "$frontEntryUrl#/notice"  // signUp 쿼리 제거
                        listHandle?.currentUrl?.let { getCurrentUrl ->
                            val current = getCurrentUrl()
                            if (current?.contains("signUp=true") == true) {
                                WebBridge.listWebView?.post {
                                    WebBridge.listWebView?.loadUrl(cleanUrl)
                                }
                            }
                        }
                    }
                }
            )

            // ========== 상세 WebView (오버레이) ==========
            if (detailUrl != null) {
                NoticeDetailWebView(
                    url = detailUrl!!,
                    visible = detailVisible,  // 성공 상태에서만 표시
                    onClose = { closeDetailAndEnsureListVisible() },
                    onLoadStateChange = { st -> detailLoadState = st },
                    onHandleReady = { handle -> detailHandle = handle },
                )
            }

            // ========== About WebView (오버레이) ==========
            if (aboutUrl != null) {
                AboutWebView(
                    url = aboutUrl!!,
                    visible = aboutVisible,  // 성공 상태에서만 표시
                    onClose = {
                        aboutUrl = null
                        aboutLoadState = LoadState.Idle
                    },
                    onLoadStateChange = { st -> aboutLoadState = st },
                    onHandleReady = { handle -> aboutHandle = handle },
                )
            }

            // ========== 목록 로딩/에러 화면 (상세가 아닐 때) ==========
            if (detailUrl == null && aboutUrl == null) {
                when (val st = listLoadState) {
                    is LoadState.Loading, LoadState.Idle -> {
                        // 로딩 중 화면
                        DelayPage(
                            "목록 불러오는 중",
                            "네트워크 상태에 따라 시간이 소요될 수 있습니다."
                        )
                    }
                    is LoadState.Error -> {
                        // 에러 화면 (서버 연결 실패, HTTP 오류 등 모든 에러 처리)
                        ErrorPage(
                            loadState = st,
                            onRetry = {
                                listLoadState = LoadState.Loading
                                listHandle?.reload?.invoke()
                            }
                        )
                    }
                    else -> Unit
                }
            }

            // ========== 상세 로딩/에러 화면 (상세일 때) ==========
            if (detailUrl != null && aboutUrl == null) {
                when (val st = detailLoadState) {
                    is LoadState.Loading, LoadState.Idle -> {
                        // 로딩 중 화면
                        DelayPage(
                            "공지사항을 불러오는 중",
                            "문서를 준비하고 있습니다…"
                        )
                    }
                    is LoadState.Error -> {
                        // 에러 화면 (서버 연결 실패, HTTP 오류 등 모든 에러 처리)
                        ErrorPage(
                            loadState = st,
                            onRetry = {
                                detailLoadState = LoadState.Loading
                                detailHandle?.reload?.invoke()
                            }
                        )
                    }
                    else -> Unit
                }
            }

            // ========== About 로딩/에러 화면 (About 페이지일 때) ==========
            if (aboutUrl != null) {
                when (val st = aboutLoadState) {
                    is LoadState.Loading, LoadState.Idle -> {
                        // 로딩 중 화면
                        DelayPage(
                            "어바웃 페이지를 불러오는 중",
                            "잠시만 기다려주세요..."
                        )
                    }
                    is LoadState.Error -> {
                        // 에러 화면
                        ErrorPage(
                            loadState = st,
                            onRetry = {
                                aboutLoadState = LoadState.Loading
                                aboutHandle?.reload?.invoke()
                            }
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}