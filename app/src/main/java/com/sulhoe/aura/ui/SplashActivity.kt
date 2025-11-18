package com.sulhoe.aura.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulhoe.aura.ui.theme.AURATheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AURATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    SplashScreen(
                        onAnimationFinished = {
                            startActivity(Intent(this, WebViewActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    val letters = "AURA".toCharArray()
    val letterAnims = List(letters.size) { remember { Animatable(0f) } }

    LaunchedEffect(Unit) {
        delay(200)

        letterAnims.forEachIndexed { index, anim ->
            launch {
                delay(index * 100L)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600)
                )
            }
        }

        delay(800)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        letters.forEachIndexed { index, char ->
            val progress = letterAnims[index].value
            val finalX = (index - 1.5f) * 140.dp.value

            Text(
                text = char.toString(),
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF2F5BFF), Color(0xFF8BCBFF))
                    )
                ),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (finalX * progress).toInt(),
                            y = 0
                        )
                    }
                    .scale(0.3f + progress * 0.7f)
                    .alpha(progress)
                    .rotate(360 * (1 - progress))
            )
        }
    }
}