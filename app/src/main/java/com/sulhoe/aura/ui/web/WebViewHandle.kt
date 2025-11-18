// ui/web/WebViewHandle.kt
package com.sulhoe.aura.ui.web

class WebViewHandle(
    val reload: () -> Unit,
    val currentUrl: () -> String?
)
