package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.ui.theme.LocalOsType
import com.baiel.expressivefiles.ui.theme.OsIcons
import com.baiel.expressivefiles.ui.theme.PillShape
import java.io.File

@Composable
fun BreadcrumbBar(
    currentDirectory: File,
    rootDirectory: File,
    onNavigateToDir: (File) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentDirectory.absolutePath == rootDirectory.absolutePath) return

    val scrollState = rememberScrollState()

    // Keep the path tail visible; content slides tell the direction.
    LaunchedEffect(currentDirectory) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    fun depth(path: String) = path.count { it == File.separatorChar }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChunkyIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            onClick = onNavigateBack,
            size = 38.dp,
            contentDescription = stringResource(R.string.crumb_navigate_up)
        )
        Spacer(modifier = Modifier.width(8.dp))

        AnimatedContent(
            targetState = currentDirectory,
            transitionSpec = {
                // Deeper navigation: trail slides in from the end edge;
                // going up mirrors it. Micro-offsets read as directional
                // motion, never as a second stacked page.
                val deeper =
                    depth(targetState.absolutePath) > depth(initialState.absolutePath)
                if (deeper) {
                    (slideInHorizontally(tween(180)) { it / 4 } + fadeIn(tween(140))) togetherWith
                            fadeOut(tween(110))
                } else {
                    (slideInHorizontally(tween(180)) { -it / 4 } + fadeIn(tween(140))) togetherWith
                            fadeOut(tween(110))
                }
            },
            label = "breadcrumb_transition"
        ) { dir ->
            val pathElements = remember(dir, rootDirectory) {
                buildList {
                    var directory: File? = dir
                    while (directory != null) {
                        add(0, directory)
                        if (directory.absolutePath == rootDirectory.absolutePath || directory.parentFile == null) break
                        directory = directory.parentFile
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                pathElements.forEachIndexed { index, folder ->
                    val isLast = index == pathElements.lastIndex
                    val isRoot = folder.absolutePath == rootDirectory.absolutePath
                    val displayName = if (isRoot) stringResource(R.string.crumb_home) else folder.name.ifEmpty { stringResource(R.string.crumb_root) }

                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(
                                if (isLast) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .cookieClickable { onNavigateToDir(folder) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRoot) Icons.Rounded.Home else OsIcons.folder(LocalOsType.current),
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
}
