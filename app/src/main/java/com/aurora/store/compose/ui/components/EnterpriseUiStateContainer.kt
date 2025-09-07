package com.aurora.store.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T> EnterpriseUiStateContainer(
    modifier: Modifier = Modifier,
    hasAccess: Boolean,
    isLoading: Boolean,
    error: String?,
    data: T?,
    isEmpty: Boolean = false,
    noAccessContent: @Composable () -> Unit = {
        CenteredMessageView(
            message = "No enterprise access available",
        )
    },
    loadingContent: @Composable () -> Unit = { DefaultLoadingView() },
    errorContent: @Composable (String) -> Unit = { DefaultErrorView(it, modifier) },
    emptyContent: @Composable () -> Unit = { 
        CenteredMessageView(
            message = "No enterprise apps available",
        )
    },
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier) {
        if (!hasAccess) {
            noAccessContent()
        } else {
            UiStateContainer(
                isLoading = isLoading,
                error = error,
                data = data,
                isEmpty = isEmpty,
                loadingContent = loadingContent,
                errorContent = errorContent,
                emptyContent = emptyContent,
                content = content
            )
        }
    }
} 