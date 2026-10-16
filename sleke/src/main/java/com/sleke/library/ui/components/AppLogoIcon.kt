package com.sleke.library.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sleke.library.ui.base.shimmer

@Composable
fun AppLogoIcon(
    iconUrl: String,
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(
                iconUrl.ifEmpty {
                    "https://play-lh.googleusercontent.com/icon?id=$packageName&s=120"
                }
            )
            .crossfade(true)
            .build(),
        contentDescription = "$appName icon",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clip(CircleShape),
        loading = {
            Box(modifier = Modifier.Companion.fillMaxSize().shimmer())
        },
        error = {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = "App icon placeholder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.Companion.size(32.dp)
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun AppLogoIconPreview() {
    MaterialTheme { AppLogoIcon("", "com.example.app", "Example app", Modifier.size(56.dp)) }
}
