package com.squeeare.hidelockwidgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PreviewScreen(
    state: MainUiState,
    onSetActiveLayer: (EditableLayer) -> Unit,
    onMove: (Float, Float) -> Unit,
    onScale: (Float) -> Unit,
    onResetActiveLayer: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Preview",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Выбери слой и перетаскивай прямо в окне. Масштаб — двумя пальцами.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.activeLayer == EditableLayer.BACKGROUND,
                onClick = { onSetActiveLayer(EditableLayer.BACKGROUND) },
                label = { Text("Задний план") },
                leadingIcon = {
                    Icon(Icons.Rounded.Photo, contentDescription = null)
                }
            )

            FilterChip(
                selected = state.activeLayer == EditableLayer.FOREGROUND,
                onClick = { onSetActiveLayer(EditableLayer.FOREGROUND) },
                label = { Text("Передний план") },
                leadingIcon = {
                    Icon(Icons.Rounded.Layers, contentDescription = null)
                }
            )
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(36.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(36.dp)
                        )
                        .pointerInput(state.activeLayer) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                onMove(pan.x, pan.y)
                                onScale(zoom)
                            }
                        }
                ) {
                    PreviewLayerImage(
                        path = state.config.backgroundPath,
                        offsetX = state.config.backgroundOffsetX,
                        offsetY = state.config.backgroundOffsetY,
                        scale = state.config.backgroundScale,
                        alpha = state.config.backgroundAlpha,
                        label = "Background"
                    )

                    WidgetPreviewStack(
                        widgetCount = state.config.previewWidgetCount,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 96.dp)
                    )

                    PreviewLayerImage(
                        path = state.config.foregroundPath,
                        offsetX = state.config.foregroundOffsetX,
                        offsetY = state.config.foregroundOffsetY,
                        scale = state.config.foregroundScale,
                        alpha = state.config.foregroundAlpha,
                        label = "Foreground"
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(100))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (state.activeLayer == EditableLayer.BACKGROUND)
                                "Активный слой: задний план"
                            else
                                "Активный слой: передний план",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Управление",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(
                        onClick = { onMove(0f, -18f) },
                        label = { Text("↑") }
                    )
                    AssistChip(
                        onClick = { onMove(-18f, 0f) },
                        label = { Text("←") }
                    )
                    AssistChip(
                        onClick = { onMove(18f, 0f) },
                        label = { Text("→") }
                    )
                    AssistChip(
                        onClick = { onMove(0f, 18f) },
                        label = { Text("↓") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(
                        onClick = { onScale(0.95f) },
                        label = { Text("− Масштаб") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Tune, contentDescription = null)
                        }
                    )
                    AssistChip(
                        onClick = { onScale(1.05f) },
                        label = { Text("+ Масштаб") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Tune, contentDescription = null)
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onResetActiveLayer, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Сбросить слой")
                    }

                    FilledTonalButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewLayerImage(
    path: String?,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    alpha: Float,
    label: String
) {
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        )
    }
}

@Composable
private fun WidgetPreviewStack(
    widgetCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(widgetCount) { index ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (index) {
                            0 -> "Яндекс Музыка"
                            1 -> "Погода"
                            2 -> "Календарь"
                            else -> "Виджет ${index + 1}"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (index) {
                            0 -> "Сейчас играет: пример трека"
                            1 -> "21°C · Cloudy"
                            2 -> "Сегодня: 2 события"
                            else -> "Preview content"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
