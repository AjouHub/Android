// ui/components/Placeholders.kt
package com.sulhoe.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sulhoe.aura.ui.common.LoadState

//private val LoadState.Error.message: String
//private val LoadState.Error.statusCode: Any

@Composable
fun DelayPage(
    title: String = "로딩 중",
    message: String = "콘텐츠를 불러오는 중입니다…",
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6C6C6C))
        }
    }
}

/**
 * 에러 페이지 - LoadState를 기반으로 적절한 메시지 표시
 * @param loadState 에러 상태 정보
 * @param onRetry 재시도 버튼 클릭 시 호출될 함수
 */
@Composable
fun ErrorPage(
    loadState: LoadState.Error,
    onRetry: () -> Unit
) {
    // 에러 타입에 따른 제목 결정
    val title = when {
        // 네트워크 연결 실패 또는 서버 연결 불가
        loadState.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                loadState.message?.contains("failed to connect", ignoreCase = true) == true ||
                loadState.message?.contains("Connection refused", ignoreCase = true) == true ||
                loadState.message?.contains("timeout", ignoreCase = true) == true ||
                loadState.statusCode == null -> "서버에 연결할 수 없습니다"

        // HTTP 5xx 서버 오류
        loadState.statusCode in 500..599 -> "서버 오류가 발생했습니다"

        // HTTP 404 Not Found
        loadState.statusCode == 404 -> "페이지를 찾을 수 없습니다"

        // HTTP 403 Forbidden
        loadState.statusCode == 403 -> "접근이 거부되었습니다"

        // HTTP 401 Unauthorized
        loadState.statusCode == 401 -> "인증이 필요합니다"

        // 기타 오류
        else -> "연결할 수 없습니다"
    }

    // 에러 타입에 따른 상세 메시지 결정
    val message = when {
        loadState.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                loadState.message?.contains("failed to connect", ignoreCase = true) == true ||
                loadState.message?.contains("Connection refused", ignoreCase = true) == true ||
                loadState.message?.contains("timeout", ignoreCase = true) == true ||
                loadState.statusCode == null ->
            "서버가 일시적으로 중단되었거나\n인터넷 연결을 확인해주세요"

        loadState.statusCode in 500..599 ->
            "서버에서 일시적인 문제가 발생했습니다\n잠시 후 다시 시도해주세요"

        loadState.statusCode == 404 ->
            "요청하신 페이지를 찾을 수 없습니다"

        loadState.statusCode == 403 ->
            "해당 페이지에 접근할 권한이 없습니다"

        loadState.statusCode == 401 ->
            "로그인이 필요한 페이지입니다"

        else ->
            loadState.message ?: "네트워크 또는 페이지 오류가 발생했습니다"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // 에러 아이콘
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.displayMedium
            )

            Spacer(Modifier.height(8.dp))

            // 에러 제목
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF2F5BFF),
                textAlign = TextAlign.Center
            )

            // 에러 메시지
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.Gray
            )

            // 상태 코드 표시 (디버깅용, 있을 경우에만)
            loadState.statusCode?.let { code ->
                Text(
                    text = "오류 코드: $code",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            Spacer(Modifier.height(8.dp))

            // 재시도 버튼
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2F5BFF)
                )
            ) {
                Text("다시 시도")
            }
        }
    }
}

/**
 * 기존 ErrorPage 오버로드 - 하위 호환성 유지
 * @param title 제목
 * @param message 메시지
 * @param onRetry 재시도 콜백
 */
@Composable
fun ErrorPage(
    title: String = "연결할 수 없습니다",
    message: String = "네트워크 또는 페이지 오류가 발생했습니다.",
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2F5BFF))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("다시 시도") }
        }
    }
}