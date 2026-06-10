package com.squeeare.hidelockwidgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.squeeare.hidelockwidgets.data.WidgetConfig
import java.io.File

@Composable
fun PreviewScreen(state: MainUiState, vm: MainViewModel, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 92.dp + padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Превью",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Нажми на фон, слой или виджет — он выберется. Двигай пальцем прямо на экране.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            LockPreviewCanvas(state = state, vm = vm)
        }
    }
}

@Composable
private fun BoxScope.LockPreviewCanvas(state: MainUiState, vm: MainViewModel) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(9f / 19.5f),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black)
                    .clickable { vm.selectBackground() }
                    .pointerInput(state.activeLayer, state.selectedWidgetId, state.config.widgets) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            vm.moveActive(pan.x, pan.y)
                            vm.scaleActive(zoom)
                        }
                    }
            ) {
                if (state.config.backgroundPath != null) {
                    SelectableImageLayer(
                        path = state.config.backgroundPath,
                        x = state.config.backgroundX,
                        y = state.config.backgroundY,
                        scale = state.config.backgroundScale / 100f,
                        selected = state.activeLayer == ActiveLayer.BACKGROUND,
                        onSelect = vm::selectBackground
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest))
                }

                StatusFake()

                if (state.config.widgets.isEmpty()) {
                    EmptyHint()
                }

                state.config.widgets.forEach { widget ->
                    if (widget.visible) {
                        PreviewWidget(
                            widget = widget,
                            selected = state.selectedWidgetId == widget.id && state.activeLayer == ActiveLayer.WIDGET,
                            onSelect = { vm.selectWidget(widget.id) }
                        )
                    }
                }

                if (state.config.foregroundPath != null) {
                    // Не перехватываем все тапы прозрачным foreground. Выбор переднего слоя делается
                    // через карточку инфо/выбор картинки на главной, а видимый слой всё равно рисуется поверх.
                    PreviewImage(
                        path = state.config.foregroundPath,
                        x = state.config.foregroundX,
                        y = state.config.foregroundY,
                        scale = state.config.foregroundScale / 100f
                    )
                    if (state.activeLayer == ActiveLayer.FOREGROUND) {
                        SelectionFrame()
                    }
                }

                ActiveInfoPanel(state = state, vm = vm)
            }
        }
    }
}

@Composable
private fun SelectableImageLayer(
    path: String?,
    x: Int,
    y: Int,
    scale: Float,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onSelect)
    ) {
        PreviewImage(path = path, x = x, y = y, scale = scale)
        if (selected) SelectionFrame()
    }
}

@Composable
private fun PreviewImage(path: String?, x: Int, y: Int, scale: Float) {
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = x.toFloat()
                    translationY = y.toFloat()
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

@Composable
private fun SelectionFrame() {
    Box(
        Modifier
            .fillMaxSize()
            .border(2.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(26.dp))
    )
}

@Composable
private fun StatusFake() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("01:29", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
        Text("VoLTE  88%", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BoxScope.EmptyHint() {
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    ) {
        Text(
            text = "Добавь виджет на главной",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PreviewWidget(widget: WidgetConfig, selected: Boolean, onSelect: () -> Unit) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier
            .offset(widget.x.dp, widget.y.dp)
            .width(widget.width.dp)
            .height(widget.height.dp)
            .graphicsLayer {
                scaleX = widget.scale / 100f
                scaleY = widget.scale / 100f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(widget.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("Live widget", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (selected) {
                Text("#${widget.id}", modifier = Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BoxScope.ActiveInfoPanel(state: MainUiState, vm: MainViewModel) {
    val selected = state.config.widgets.firstOrNull { it.id == state.selectedWidgetId }
    val title: String
    val subtitle: String
    val icon = when (state.activeLayer) {
        ActiveLayer.BACKGROUND -> {
            title = "Задний слой"
            subtitle = "X: ${state.config.backgroundX}   Y: ${state.config.backgroundY}   S: ${state.config.backgroundScale}%"
            Icons.Rounded.Image
        }
        ActiveLayer.FOREGROUND -> {
            title = "Передний слой"
            subtitle = "X: ${state.config.foregroundX}   Y: ${state.config.foregroundY}   S: ${state.config.foregroundScale}%"
            Icons.Rounded.Layers
        }
        ActiveLayer.WIDGET -> {
            if (selected != null) {
                title = "Виджет #${selected.id}"
                subtitle = "X: ${selected.x}   Y: ${selected.y}   ${selected.width}×${selected.height}   S: ${selected.scale}%"
            } else {
                title = "Виджет не выбран"
                subtitle = "Нажми на виджет или добавь его на главной"
            }
            Icons.Rounded.Widgets
        }
    }

    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = vm::deleteActive) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AssistChip(onClick = { vm.nudgeActive(-10, 0) }, label = { Text("←") })
                AssistChip(onClick = { vm.nudgeActive(10, 0) }, label = { Text("→") })
                AssistChip(onClick = { vm.nudgeActive(0, -10) }, label = { Text("↑") })
                AssistChip(onClick = { vm.nudgeActive(0, 10) }, label = { Text("↓") })
                AssistChip(onClick = { vm.scaleActiveDelta(-10) }, label = { Text("S-") })
                AssistChip(onClick = { vm.scaleActiveDelta(10) }, label = { Text("S+") })
            }

            if (state.activeLayer == ActiveLayer.WIDGET && selected != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = { vm.resizeActive(-20, 0) }, label = { Text("W-") })
                    AssistChip(onClick = { vm.resizeActive(20, 0) }, label = { Text("W+") })
                    AssistChip(onClick = { vm.resizeActive(0, -20) }, label = { Text("H-") })
                    AssistChip(onClick = { vm.resizeActive(0, 20) }, label = { Text("H+") })
                }
                Text(
                    text = selected.provider,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
