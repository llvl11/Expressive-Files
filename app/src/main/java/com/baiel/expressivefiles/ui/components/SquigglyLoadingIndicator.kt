package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.model.ArchiveProgress
import com.baiel.expressivefiles.ui.components.formatFileSize
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import kotlin.math.sin

@Composable
fun SquigglyLoadingIndicator(
    progress: ArchiveProgress,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Column(
        modifier = modifier.padding(bottom = 12.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Progress Popup
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 4.dp,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = progress.operation,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (progress.currentFileName.isNotBlank()) {
                    Text(
                        text = progress.currentFileName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val progressText = if (progress.isIndeterminate) {
                        "Processing..."
                    } else {
                        "${(progress.progressFloat * 100).toInt()}%"
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (progress.totalBytes > 0) {
                        Text(
                            text = "${formatFileSize(progress.bytesProcessed)} / ${formatFileSize(progress.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Squiggly Line
        val strokeColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .width(64.dp)
                .height(16.dp)
        ) {
            val width = size.width
            val height = size.height
            val points = 50
            val path = Path()
            
            for (i in 0..points) {
                val x = (i.toFloat() / points) * width
                val y = height / 2 + sin(x * 0.1f + phase) * 6f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
