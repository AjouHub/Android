// AuraTopBar.kt

package com.sulhoe.aura.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key

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

    Column(
        Modifier
            .background(Color.White)
            .statusBarsPadding()
            .pointerInput(isSearching) {
                detectHorizontalDragGestures { _, dragAmount ->
                    // 좌로 스와이프 → 열기, 우로 스와이프 → 닫기
                    if (!isSearching && dragAmount < -swipeThresholdPx) onSearchToggle(true)
                    if (isSearching && dragAmount > swipeThresholdPx) onSearchToggle(false)
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
                    // 기본 탑바: 가운데 로고, 우측 검색 아이콘
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
                            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color(0xFF6C6C6C))
                        }
                    }
                } else {
                    // 검색 모드 탑바
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        IconButton(onClick = { onSearchToggle(false) }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        var tfv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
                        }
                        // 검색 창
                        TextField(
                            value = tfv,
                            onValueChange = { newV ->
                                tfv = newV
                                onQueryChange(newV.text) // <<< 수정된 부분
                            },
                            placeholder = { Text("검색어를 입력하세요") },
                            singleLine = true,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .weight(1f),                           // ← 원하시는 UI 유지를 위해 weight 사용

                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF4F6F8),
                                unfocusedContainerColor = Color(0xFFF4F6F8),
                                disabledContainerColor = Color(0xFFF4F6F8),
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSubmit(tfv.text) }
                            )
                        )
                        // 우측: 명시적 검색 버튼
                        IconButton(
                            onClick = { onSubmit(tfv.text) },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = "검색 실행")
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0x11000000))
    }
}