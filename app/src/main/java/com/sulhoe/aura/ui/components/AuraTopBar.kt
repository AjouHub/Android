package com.sulhoe.aura.ui.components

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

@Composable
fun AuraTopBar(
    isSearching: Boolean,
    query: String,
    onSearchToggle: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    title: String = "AURA",
) {
    val swipeThresholdPx = 40f
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 내부 입력 상태
    var tfv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    // 검색 닫기 + 초기화(요구사항 3)
    fun closeSearchAndClear() {
        keyboardController?.hide()

        // (0) 조합 중이면 커밋해서 잔여 composition 제거 (선택)
        if (tfv.composition != null) {
            tfv = tfv.copy(composition = null, selection = TextRange(tfv.text.length))
        }

        // (1) 먼저 빈 쿼리를 반영
        onQueryChange("")

        // (2) 빈 문자열로 즉시 검색 실행
        onSubmit("")
        // (3) 내부 입력 초기화
        tfv = TextFieldValue("")

        // (4) 마지막에 검색창 닫기
        onSearchToggle(false)
    }

    // 조합 강제 커밋 + 외부 쿼리 동기화 + 검색 실행
    fun commitAndSubmit() {
        // 한글 조합 중이면 커밋
        if (tfv.composition != null) {
            tfv = tfv.copy(composition = null, selection = TextRange(tfv.text.length))
        }
        onQueryChange(tfv.text)  // 외부 쿼리를 먼저 갱신
        onSubmit(tfv.text)       // 그 다음 검색 실행
    }

    // 하드웨어 뒤로가기(요구사항 2)
    BackHandler(enabled = isSearching) {
        closeSearchAndClear()
    }

    Column(
        Modifier
            .background(Color.White) // 요구사항 1: Light 고정
            .statusBarsPadding()
            .pointerInput(isSearching) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (!isSearching && dragAmount < -swipeThresholdPx) onSearchToggle(true)
                    if (isSearching && dragAmount > swipeThresholdPx) closeSearchAndClear()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedContent(
                targetState = isSearching,
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
                    // 기본 탑바
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = title,
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
                        IconButton(
                            onClick = { onSearchToggle(true) },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF202124) // Light 고정
                            )
                        }
                    }
                } else {
                    // 검색 모드
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
                                tint = Color(0xFF202124) // Light 고정
                            )
                        }

                        TextField(
                            value = tfv,
                            onValueChange = { newV ->
                                // 내부 상태는 항상 반영
                                tfv = newV
                                // 조합 중에는 외부 상태를 건드리지 않음(한글 분해 방지)
                                if (newV.composition == null) {
                                    onQueryChange(newV.text)
                                }
                            },
                            placeholder = {
                                Text("검색어를 입력하세요", color = Color(0xFF5F6368)) // Light 고정
                            },
                            singleLine = true,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .weight(1f)
                                .focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    commitAndSubmit() // ← 조합 커밋 + 외부 동기화 + 검색
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
                                commitAndSubmit() // ← 아이콘도 동일 경로
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "검색 실행",
                                tint = Color(0xFF202124) // Light 고정
                            )
                        }
                    }
                }
            }
        }
    }
}
