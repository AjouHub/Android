// ui/common/LoadState.kt
package com.sulhoe.aura.ui.common

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(
        val statusCode: Int? = null,
        val message: String? = null,
        val failingUrl: String? = null
    ) : LoadState
}
