package com.sulhoe.aura.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sulhoe.aura.R
import com.sulhoe.aura.ui.theme.AURATheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // isAnimationPlaying 상태가 false가 될 때까지 시스템 스플래시를 유지
        var keepSplashOn = true
        installSplashScreen().setKeepOnScreenCondition { keepSplashOn }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AURATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SplashScreen(
                        onAnimationFinished = {
                            startActivity(Intent(this, WebViewActivity::class.java))
                            finish()
                        },
                        // 애니메이션 준비가 끝나면 시스템 스플래시를 숨김
                        onReadyToAnimate = { keepSplashOn = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit,
    onReadyToAnimate: () -> Unit
) {
    val letters = "AURA".toCharArray()
    // 0f: 시작 (중앙에 뭉침), 1f: 끝 (각자 위치로 퍼짐)
    val letterAnims = List(letters.size) { remember { Animatable(0f) } }
    // 1f: 시작 (보임), 0f: 끝 (사라짐)
    val logoScale = remember { Animatable(1f) }
    val logoAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 1. 애니메이션을 시작할 준비가 되었음을 시스템에 알림
        //    (이 호출 직후 시스템 스플래시가 사라지기 시작)
        onReadyToAnimate()
        delay(50) // 시스템 스플래시가 사라지는 시간과 맞추기 위한 짧은 딜레이

        coroutineScope {
            // 2. 로고가 작아지면서 사라지는 애니메이션
            launch {
                logoScale.animateTo(0f, animationSpec = tween(durationMillis = 300))
            }
            launch {
                logoAlpha.animateTo(0f, animationSpec = tween(durationMillis = 300))
            }
            // 3. 로고가 사라지는 동시에 글자들이 밖으로 퍼져나가는 애니메이션
            letterAnims.forEachIndexed { index, anim ->
                launch {
                    delay(index * 80L) // 로고가 조금 사라진 후, 순차적으로 시작
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 500)
                    )
                }
            }
        }

        // 5. 다음 화면으로 전환
        onAnimationFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 로고: 처음에는 보였다가 애니메이션에 따라 사라짐
        Image(
            painter = painterResource(id = R.drawable.ic_aura_logo),
            contentDescription = "AURA Logo",
            modifier = Modifier
                .size(120.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
        )

        // AURA 글자: 처음에는 숨겨져 있다가 로고 위치에서부터 각자의 자리로 퍼져나감
        letters.forEachIndexed { index, char ->
            val progress = letterAnims[index].value
            val finalX = (index - 1.5f) * 140.dp.value

            Text(
                text = char.toString(),
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Unspecified,
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF2F5BFF), Color(0xFF8BCBFF))
                    )
                ),
                modifier = Modifier
                    .offset {
                        // progress(0 -> 1)에 따라 중앙(0,0)에서 최종 위치(finalX, 0)로 이동
                        IntOffset(
                            x = (finalX * progress).toInt(),
                            y = 0
                        )
                    }
                    .scale(progress) // 크기도 0에서 1로 커짐
                    .alpha(progress) // 투명도도 0에서 1로 나타남
                    .rotate(360 * (1 - progress)) // 회전하며 나타나는 효과
            )
        }
    }
}