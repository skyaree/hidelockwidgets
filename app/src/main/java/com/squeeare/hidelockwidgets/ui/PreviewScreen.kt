package com.squeeare.hidelockwidgets.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp + padding.calculateTopPadding(), bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Превью",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Экран блокировки: двигай виджеты, фон и передний слой прямо здесь.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        LayerTabs(state.activeLayer, vm::setActiveLayer)

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LockPreviewCanvas(state, vm)
        }

        PreviewBottomPanel(state, vm)
    }
}

@Composable
private fun LayerTabs(active: ActiveLayer, onLayer: (ActiveLayer) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LayerChip("Задний", Icons.Rounded.Image, active == ActiveLayer.BACKGROUND) { onLayer(ActiveLayer.BACKGROUND) }
        LayerChip("Виджеты", Icons.Rounded.Widgets, active == ActiveLayer.WIDGET) { onLayer(ActiveLayer.WIDGET) }
        LayerChip("Передний", Icons.Rounded.Layers, active == ActiveLayer.FOREGROUND) { onLayer(ActiveLayer.FOREGROUND) }
    }
}

@Composable
private fun RowScope.LayerChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(title, maxLines = 1) },
        leadingIcon = { Icon(icon, null) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun LockPreviewCanvas(state: MainUiState, vm: MainViewModel) {
    ElevatedCard(
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier.padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 19.5f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
                    .pointerInput(state.activeLayer, state.selectedWidgetId) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            vm.moveActive(pan.x, pan.y)
                            vm.scaleActive(zoom)
                        }
                    }
            ) {
                PreviewImage(
                    path = state.config.backgroundPath,
                    x = state.config.backgroundX,
                    y = state.config.backgroundY,
                    scale = state.config.backgroundScale / 100f
                )

                if (state.config.backgroundPath == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest))
                }

                StatusFake()

                state.config.widgets.forEach { widget ->
                    if (widget.visible) PreviewWidget(
                        widget = widget,
                        selected = state.selectedWidgetId == widget.id && state.activeLayer == ActiveLayer.WIDGET,
                        onSelect = { vm.selectWidget(widget.id) }
                    )
                }

                PreviewImage(
                    path = state.config.foregroundPath,
                    x = state.config.foregroundX,
                    y = state.config.foregroundY,
                    scale = state.config.foregroundScale / 100f
                )

                ActiveOverlayInfo(state)
            }
        }
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
private fun StatusFake() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("01:29", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
        Text("VoLTE  88%", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
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
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(widget.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("Live widget", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (selected) Text("#${widget.id}", modifier = Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ActiveOverlayInfo(state: MainUiState) {
    val selected = state.config.widgets.firstOrNull { it.id == state.selectedWidgetId }
    val text = when (state.activeLayer) {
        ActiveLayer.BACKGROUND -> "Задний слой\nX: ${state.config.backgroundX}\nY: ${state.config.backgroundY}"
        ActiveLayer.FOREGROUND -> "Передний слой\nX: ${state.config.foregroundX}\nY: ${state.config.foregroundY}"
        ActiveLayer.WIDGET -> if (selected != null) "Виджет #${selected.id}\nX: ${selected.x}\nY: ${selected.y}" else "Выбери виджет"
    }
    Surface(
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Text(text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PreviewBottomPanel(state: MainUiState, vm: MainViewModel) {
    val selected = state.config.widgets.firstOrNull { it.id == state.selectedWidgetId }
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { vm.setPage(AppPage.HOME); vm.toggleWidgetPicker() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Widget")
                }
                Button(onClick = vm::apply, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Done, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Применить")
                }
            }

            if (selected == null) {
                Text("Выбери виджет на холсте или добавь новый на главной.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    text = "${selected.title}: X:${selected.x}, Y:${selected.y}, ${selected.width}×${selected.height}, ${selected.scale}%",
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Виджет: ${selected.provider}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = { vm.nudgeWidget(selected.id, -10, 0) }, label = { Text("←") })
                    AssistChip(onClick = { vm.nudgeWidget(selected.id, 10, 0) }, label = { Text("→") })
                    AssistChip(onClick = { vm.nudgeWidget(selected.id, 0, -10) }, label = { Text("↑") })
                    AssistChip(onClick = { vm.nudgeWidget(selected.id, 0, 10) }, label = { Text("↓") })
                    AssistChip(onClick = vm::deleteSelected, label = { Text("Удалить") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = { vm.resizeWidget(selected.id, -20, 0) }, label = { Text("W-") })
                    AssistChip(onClick = { vm.resizeWidget(selected.id, 20, 0) }, label = { Text("W+") })
                    AssistChip(onClick = { vm.resizeWidget(selected.id, 0, -20) }, label = { Text("H-") })
                    AssistChip(onClick = { vm.resizeWidget(selected.id, 0, 20) }, label = { Text("H+") })
                    AssistChip(onClick = { vm.scaleWidget(selected.id, 10) }, label = { Text("S+") })
                }
            }
        }
    }
}
