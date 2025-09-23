// AuraBottomBar.kt
package com.sulhoe.aura.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

// 각 네비게이션 아이템 정보를 담는 데이터 클래스
private data class BottomNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun AuraBottomBar(
    current: String,
    onSelect: (String) -> Unit
) {
    // 네비게이션 아이템 리스트 생성
    val navItems = listOf(
        BottomNavItem("home", "홈", Icons.Outlined.Home),
        BottomNavItem("bookmark", "북마크", Icons.Outlined.Star),
        BottomNavItem("settings", "설정", Icons.Outlined.Settings)
    )

    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = current == item.key,
                onClick = { onSelect(item.key) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
