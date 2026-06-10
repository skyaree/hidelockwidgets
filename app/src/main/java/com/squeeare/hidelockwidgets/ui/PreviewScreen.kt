package com.squeeare.hidelockwidgets.ui

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PreviewScreen(state: MainUiState, vm: MainViewModel, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp + padding.calculateTopPadding(), bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Preview", style = MaterialTheme.typography.headlineLarge)
        Text("Пустой холст локскрина: добавляй виджеты, двигай их и включай depth.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.activeLayer == ActiveLayer.BACKGROUND, { vm.setActiveLayer(ActiveLayer.BACKGROUND) }, label = { Text("Background") }, leadingIcon = { Icon(Icons.Rounded.Image, null) })
            FilterChip(state.activeLayer == ActiveLayer.WIDGET, { vm.setActiveLayer(ActiveLayer.WIDGET) }, label = { Text("Widgets") }, leadingIcon = { Icon(Icons.Rounded.Widgets, null) })
            FilterChip(state.activeLayer == ActiveLayer.FOREGROUND, { vm.setActiveLayer(ActiveLayer.FOREGROUND) }, label = { Text("Foreground") }, leadingIcon = { Icon(Icons.Rounded.Layers, null) })
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ElevatedCard(shape = RoundedCornerShape(42.dp)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(42.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .pointerInput(state.activeLayer, state.selectedWidgetId) {
                            detectTransformGestures { _, pan, zoom, _ -> vm.moveActive(pan.x, pan.y); vm.scaleActive(zoom) }
                        }
                ) {
                    PreviewImage(state.config.backgroundPath, state.config.backgroundOffsetX, state.config.backgroundOffsetY, state.config.backgroundScale)
                    StatusFake()
                    state.config.widgets.forEach { widget ->
                        if (widget.visible) WidgetCard(
                            title = widget.title,
                            selected = state.selectedWidgetId == widget.id,
                            x = widget.xDp,
                            y = widget.yDp,
                            w = widget.widthDp,
                            h = widget.heightDp,
                            scale = widget.scalePercent / 100f,
                            onClick = { vm.selectWidget(widget.id) }
                        )
                    }
                    PreviewImage(state.config.foregroundPath, state.config.foregroundOffsetX, state.config.foregroundOffsetY, state.config.foregroundScale)
                }
            }
        }
        ControlPanel(state, vm)
    }
}

@Composable
private fun PreviewImage(path: String?, x: Float, y: Float, scale: Float) {
    if (path != null && File(path).exists()) AsyncImage(
        model = File(path), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer { translationX = x; translationY = y; scaleX = scale; scaleY = scale }
    )
}

@Composable
private fun StatusFake() {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("00:15", style = MaterialTheme.typography.labelLarge)
        Text("LTE 58%", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WidgetCard(title: String, selected: Boolean, x: Int, y: Int, w: Int, h: Int, scale: Float, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .offset(x.dp, y.dp)
            .width(w.dp)
            .height(h.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f,0f) }
            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("Live widget preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ControlPanel(state: MainUiState, vm: MainViewModel) {
    val selected = state.config.widgets.firstOrNull { it.id == state.selectedWidgetId }
    ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = vm::addYandexRecent, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Widget") }
            Button(onClick = vm::apply, modifier = Modifier.weight(1f)) { Text("Apply") }
        }
        if (selected != null) {
            Text("${selected.title}: x=${selected.xDp}, y=${selected.yDp}, ${selected.widthDp}×${selected.heightDp}, ${selected.scalePercent}%")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { vm.moveActive(-8f, 0f) }, label = { Text("←") })
                AssistChip(onClick = { vm.moveActive(8f, 0f) }, label = { Text("→") })
                AssistChip(onClick = { vm.moveActive(0f, -8f) }, label = { Text("↑") })
                AssistChip(onClick = { vm.moveActive(0f, 8f) }, label = { Text("↓") })
                AssistChip(onClick = vm::deleteSelectedWidget, label = { Text("Delete") })
            }
        } else Text("Нажми на виджет или добавь новый.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
