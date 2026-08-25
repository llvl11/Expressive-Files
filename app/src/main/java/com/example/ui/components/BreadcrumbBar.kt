package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.PillShape
import java.io.File

@Composable
fun BreadcrumbBar(
    currentDirectory: File,
    rootDirectory: File,
    onNavigateToDir: (File) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentDirectory.absolutePath == rootDirectory.absolutePath) {
        return
    }

    val scrollState = rememberScrollState()

    // Build path elements
    val pathElements = mutableListOf<File>()
    var curr: File? = currentDirectory
    while (curr != null) {
        pathElements.add(0, curr)
        if (curr.absolutePath == rootDirectory.absolutePath || curr.parentFile == null) break
        curr = curr.parentFile
    }

    LaunchedEffect(currentDirectory) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button if can go up
        val canGoBack = currentDirectory.absolutePath != rootDirectory.absolutePath && currentDirectory.parentFile != null
        if (canGoBack) {
            ChunkyIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onNavigateBack,
                size = 38.dp,
                shape = ChunkyIconShape,
                contentDescription = "Navigate Up"
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Horizontal Breadcrumb Scroll
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pathElements.forEachIndexed { index, folder ->
                val isLast = index == pathElements.size - 1
                val isRoot = folder.absolutePath == rootDirectory.absolutePath

                val displayName = when {
                    isRoot -> "Home"
                    folder.name.isEmpty() -> "Root"
                    else -> folder.name
                }

                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(
                            if (isLast) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .cookieClickable {
                            onNavigateToDir(folder)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isRoot) Icons.Rounded.Home else Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = displayName,
                            fontWeight = if (isLast) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                if (!isLast) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
