// AuraTopBar.kt
package com.sulhoe.aura.ui.components
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// 모드: LIST(검색), DETAIL(뒤로+공유), OTHER(타이틀만)
enum class TopBarMode { LIST, DETAIL, OTHER }

@SuppressLint("RememberInComposition")
@Composable
fun AuraTopBar(
    mode: TopBarMode = TopBarMode.LIST,
    isSearching: Boolean,
    query: String,
    onSearchToggle: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onShareClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null, // DETAIL 왼쪽 뒤로가기
) {
    val swipeThresholdPx = 40f
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = FocusRequester()

    var tfv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    fun closeSearchAndClear() {
        keyboardController?.hide()
        if (tfv.composition != null) {
            tfv = tfv.copy(composition = null, selection = TextRange(tfv.text.length))
        }
        onQueryChange("")
        onSubmit("")
        tfv = TextFieldValue("")
        onSearchToggle(false)
    }

    fun commitAndSubmit() {
        if (tfv.composition != null) {
            tfv = tfv.copy(composition = null, selection = TextRange(tfv.text.length))
        }
        onQueryChange(tfv.text)
        onSubmit(tfv.text)
    }

    // 검색 뒤로가기는 LIST에서만 허용
    BackHandler(enabled = (mode == TopBarMode.LIST && isSearching)) {
        closeSearchAndClear()
    }

    val searchEnabled = (mode == TopBarMode.LIST)
    val searchingStateForUI = searchEnabled && isSearching

    Column(
        Modifier
            .background(Color.White)
            .statusBarsPadding()
            // 제스처 열기/닫기도 LIST에서만
            .then(
                if (searchEnabled) Modifier.pointerInput(isSearching) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (!isSearching && dragAmount < -swipeThresholdPx) onSearchToggle(true)
                        if (isSearching && dragAmount > swipeThresholdPx) closeSearchAndClear()
                    }
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedContent(
                targetState = searchingStateForUI, // DETAIL/OTHER에서는 false
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(220)) { it } togetherWith
                                slideOutHorizontally(tween(180)) { -it })
                            .using(SizeTransform(clip = false))
                    } else {
                        (slideInHorizontally(tween(220)) { -it } togetherWith
                                slideOutHorizontally(tween(180)) { it })
                            .using(SizeTransform(clip = false))
                    }
                },
                label = "AuraTopBarSearchAnim"
            ) { searching ->
                if (!searching) {
                    // 기본 탑바 (타이틀은 항상 AURA)
                    Box(Modifier.fillMaxSize()) {
                        // 좌측: DETAIL일 때 뒤로가기 버튼
                        if (mode == TopBarMode.DETAIL) {
                            IconButton(
                                onClick = { onBackClick?.invoke() },
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "뒤로",
                                    tint = Color(0xFF202124)
                                )
                            }
                        }

                        // 타이틀
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "AURA",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Unspecified,
                                style = LocalTextStyle.current.copy(
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF2F5BFF), Color(0xFF8BCBFF))
                                    )
                                )
                            )
                        }

                        // 우측: LIST=검색, DETAIL=공유, OTHER=없음
                        when (mode) {
                            TopBarMode.LIST -> {
                                IconButton(
                                    onClick = { onSearchToggle(true) },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFF202124)
                                    )
                                }
                            }
                            TopBarMode.DETAIL -> {
                                IconButton(
                                    onClick = { onShareClick?.invoke() },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = "공유",
                                        tint = Color(0xFF202124)
                                    )
                                }
                            }
                            TopBarMode.OTHER -> {
                                // 아이콘 없음
                            }
                        }
                    }
                } else {
                    // 검색 모드 (LIST에서만)
                    LaunchedEffect(Unit) {
                        delay(100)
                        focusRequester.requestFocus()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        IconButton(onClick = { closeSearchAndClear() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF202124)
                            )
                        }

                        TextField(
                            value = tfv,
                            onValueChange = { newV ->
                                tfv = newV
                                if (newV.composition == null) {
                                    onQueryChange(newV.text)
                                }
                            },
                            placeholder = { Text("검색어를 입력하세요", color = Color(0xFF5F6368)) },
                            singleLine = true,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .weight(1f)
                                .focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    commitAndSubmit()
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                disabledTextColor = Color.Black,
                                cursorColor = Color.Black,
                                focusedContainerColor = Color(0xFFF4F6F8),
                                unfocusedContainerColor = Color(0xFFF4F6F8),
                                disabledContainerColor = Color(0xFFF4F6F8),
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )

                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                commitAndSubmit()
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "검색 실행",
                                tint = Color(0xFF202124)
                            )
                        }
                    }
                }
            }
        }

        // LIST 모드가 아닐 때만 구분선 표시
        if (mode != TopBarMode.LIST) {
            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFFE0E0E0)
            )
        }
    }
}
