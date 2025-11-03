package com.sleke.library.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sleke.library.R
import com.sleke.library.ui.base.shimmer
import com.sleke.library.ui.SimpleAppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleAppItem(
    modifier: Modifier = Modifier,
    app: SimpleApp,
    onDownload: () -> Unit,
    onInstall: (apkUri: String) -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { 
            if (app.appState !is SimpleAppUiState.Downloading) {
                isExpanded = !isExpanded 
            }
        },
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.Companion.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLogoIcon(
                        modifier = Modifier.Companion.size(56.dp),
                        iconUrl = app.iconUrl.orEmpty(),
                        packageName = app.packageName,
                        appName = app.name
                    )

                    Spacer(modifier = Modifier.Companion.width(12.dp))

                    Column(modifier = Modifier.Companion.weight(1f)) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (app.publisher.isNotEmpty()) {
                            Spacer(modifier = Modifier.Companion.height(2.dp))
                            Text(
                                text = app.publisher,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (app.versionDisplay.isNotEmpty()) {
                            Spacer(modifier = Modifier.Companion.height(2.dp))
                            Text(
                                text = app.versionDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppStateStatusRow(
                        appState = app.appState,
                        onDownload = onDownload,
                        onInstall = { uri, packageName ->
                            onInstall(uri)
                        },
                        onOpen = onOpen,
                        onUninstall = onUninstall,
                    )

                    if (app.appState !is SimpleAppUiState.Downloading) {
                        Spacer(modifier = Modifier.Companion.width(4.dp))
                        IconButton(
                            onClick = { isExpanded = !isExpanded }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeOut()
            ) {
                ExpandedContent(
                    description = app.description,
                    packageName = app.packageName,
                    type = app.type
                )
            }
        }
    }
}

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
            .data(iconUrl.ifEmpty { "https://play-lh.googleusercontent.com/icon?id=${packageName}&s=120" })
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

@Composable
private fun ExpandedContent(
    description: String,
    packageName: String,
    type: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            thickness = 1.dp
        )

                Spacer(modifier = Modifier.Companion.height(12.dp))
        
        if (description.isNotEmpty()) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.Companion.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
            Spacer(modifier = Modifier.Companion.height(12.dp))
        }

        Row(
            modifier = Modifier.Companion.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (packageName.isNotEmpty()) {
                DetailItem(
                    label = "Package",
                    value = packageName,
                    modifier = Modifier.Companion.weight(1f)
                )
            }

            if (type.isNullOrEmpty().not()) {
                DetailItem(
                    label = "Type",
                    value = type.uppercase(),
                    modifier = Modifier.Companion.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.Companion.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppStateStatusRow(
    appState: SimpleAppUiState,
    onDownload: () -> Unit,
    onInstall: (uri: String, packageName: String) -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit
) {
    when (appState) {
        is SimpleAppUiState.NotDownloaded -> {
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        is SimpleAppUiState.Downloading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.Companion
                    .size(40.dp)
                    .padding(end = 4.dp)
            ) {
                CircularProgressIndicator(
                    progress = { appState.progress / 100f },
                    strokeWidth = 2.dp,
                    modifier = Modifier.Companion.size(44.dp)
                )
                Text(
                    "${appState.progress}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        is SimpleAppUiState.Downloaded -> {
            IconButton(onClick = { onInstall(appState.apkUri, appState.packageName) }) {
                Icon(
                    imageVector = Icons.Default.InstallMobile,
                    contentDescription = "Install",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        is SimpleAppUiState.Installed -> {
            Row {
                IconButton(onClick = onUninstall) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Uninstall",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is SimpleAppUiState.Error -> {
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Retry",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SimpleAppItemPreview() {
    Column(
        modifier = Modifier.Companion.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SimpleAppItem(
            app = SimpleApp(
                name = "Airbnb (Web)",
                description = "Find inspiration, plan a trip with your group, book it, and go. You'll always have access to important trip information.",
                publisher = "Airbnb",
                packageName = "com.sleke.airbnbweb",
                versionDisplay = "1.0",
                type = "travel",
                appState = SimpleAppUiState.NotDownloaded
            ),
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
        SimpleAppItem(
            app = SimpleApp(
                name = "Downloading App",
                description = "This app is currently downloading...",
                publisher = "Example Publisher",
                packageName = "com.example.downloading",
                versionDisplay = "2.1",
                type = "utility",
                appState = SimpleAppUiState.Downloading(progress = 42)
            ),
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
        SimpleAppItem(
            app = SimpleApp(
                name = "Installed App",
                description = "This app is already installed on your device and ready to use.",
                publisher = "Example Corp",
                packageName = "com.example.installed",
                versionDisplay = "3.0.1",
                type = "productivity",
                appState = SimpleAppUiState.Installed
            ),
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
    }
}