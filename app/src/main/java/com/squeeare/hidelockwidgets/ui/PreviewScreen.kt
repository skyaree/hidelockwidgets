package com.squeeare.hidelockwidgets.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.squeeare.hidelockwidgets.data.WidgetConfig
import java.io.File

private const val DESIGN_WIDTH = 1080f
private const val DESIGN_HEIGHT = 2400f

@Composable
fun PreviewScreen(state: MainUiState, vm: MainViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = padding.calculateBottomPadding() + 10.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Превью",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Холст 1080×2400: нажми на фон, слой или виджет — потом двигай пальцем.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            LockPreviewCanvas(state = state, vm = vm)
        }
    }
}

@Composable
private fun LockPreviewCanvas(state: MainUiState, vm: MainViewModel) {
    val density = LocalDensity.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DESIGN_WIDTH / DESIGN_HEIGHT)
                .padding(10.dp)
        ) {
            val canvasWidthDp = maxWidth
            val designScale = canvasWidthDp.value / DESIGN_WIDTH

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF020D13))
                    .clickable { vm.selectBackground() }
                    .pointerInput(state.activeLayer, state.selectedWidgetId, state.config.widgets, designScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val dxDp = with(density) { pan.x.toDp().value }
                            val dyDp = with(density) { pan.y.toDp().value }
                            if (designScale > 0f) {
                                vm.moveActive(dxDp / designScale, dyDp / designScale)
                            }
                            vm.scaleActive(zoom)
                        }
                    }
            ) {
                if (state.config.backgroundPath != null) {
                    SelectableImageLayer(
                        path = state.config.backgroundPath,
                        x = state.config.backgroundX,
                        y = state.config.backgroundY,
                        scalePercent = state.config.backgroundScale,
                        designScale = designScale,
                        selected = state.activeLayer == ActiveLayer.BACKGROUND,
                        onSelect = vm::selectBackground
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF021018)))
                }

                StatusFake()

                if (state.config.widgets.isEmpty()) {
                    EmptyHint()
                }

                state.config.widgets.forEach { widget ->
                    if (widget.visible) {
                        PreviewWidget(
                            widget = widget,
                            designScale = designScale,
                            selected = state.selectedWidgetId == widget.id && state.activeLayer == ActiveLayer.WIDGET,
                            onSelect = { vm.selectWidget(widget.id) }
                        )
                    }
                }

                if (state.config.foregroundPath != null) {
                    PreviewImage(
                        path = state.config.foregroundPath,
                        x = state.config.foregroundX,
                        y = state.config.foregroundY,
                        scalePercent = state.config.foregroundScale,
                        designScale = designScale
                    )
                    if (state.activeLayer == ActiveLayer.FOREGROUND) SelectionFrame()
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
    scalePercent: Int,
    designScale: Float,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onSelect)
    ) {
        PreviewImage(path = path, x = x, y = y, scalePercent = scalePercent, designScale = designScale)
        if (selected) SelectionFrame()
    }
}

@Composable
private fun PreviewImage(path: String?, x: Int, y: Int, scalePercent: Int, designScale: Float) {
    val density = LocalDensity.current
    if (path != null && File(path).exists()) {
        val tx = with(density) { (x * designScale).dp.toPx() }
        val ty = with(density) { (y * designScale).dp.toPx() }
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = tx
                    translationY = ty
                    scaleX = scalePercent / 100f
                    scaleY = scalePercent / 100f
                }
        )
    }
}

@Composable
private fun SelectionFrame() {
    Box(
        Modifier
            .fillMaxSize()
            .border(2.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
    )
}

@Composable
private fun StatusFake() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("01:29", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("VoLTE  88%", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxScope.EmptyHint() {
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(20.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    ) {
        Text(
            text = "Добавь виджет\nна главной",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PreviewWidget(widget: WidgetConfig, designScale: Float, selected: Boolean, onSelect: () -> Unit) {
    Box(
        modifier = Modifier
            .offset((widget.x * designScale).dp, (widget.y * designScale).dp)
            .width((widget.width * designScale).dp)
            .height((widget.height * designScale).dp)
            .graphicsLayer {
                scaleX = widget.scale / 100f
                scaleY = widget.scale / 100f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                AppWidgetPreviewHost.create(
                    context = context,
                    localId = widget.id,
                    providerString = widget.provider,
                    widthDp = (widget.width / 3).coerceAtLeast(60),
                    heightDp = (widget.height / 3).coerceAtLeast(40),
                    fallbackTitle = widget.title
                )
            }
        )
        if (selected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            ) {
                Text(
                    text = "#${widget.id}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
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
                subtitle = "X: ${selected.x}  Y: ${selected.y}  ${selected.width}×${selected.height}  S: ${selected.scale}%"
            } else {
                title = "Виджет не выбран"
                subtitle = "Добавь на главной или нажми на виджет"
            }
            Icons.Rounded.Widgets
        }
    }

    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = vm::deleteActive) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private object AppWidgetPreviewHost {
    private const val HOST_ID = 8193
    private var host: AppWidgetHost? = null
    private val widgetIds = linkedMapOf<Int, Int>()
    private val providers = linkedMapOf<Int, String>()

    fun create(
        context: Context,
        localId: Int,
        providerString: String,
        widthDp: Int,
        heightDp: Int,
        fallbackTitle: String
    ): View {
        return try {
            val appContext = context.applicationContext
            val component = ComponentName.unflattenFromString(providerString) ?: return fallback(context, fallbackTitle, "Bad provider")
            val manager = AppWidgetManager.getInstance(appContext)
            val h = host ?: AppWidgetHost(appContext, HOST_ID).also {
                host = it
                it.startListening()
            }

            val oldProvider = providers[localId]
            var id = widgetIds[localId] ?: -1
            if (id > 0 && oldProvider != null && oldProvider != providerString) {
                runCatching { h.deleteAppWidgetId(id) }
                id = -1
            }

            if (id <= 0) {
                id = h.allocateAppWidgetId()
                val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, component) }.getOrDefault(false)
                if (!bound) {
                    runCatching { h.deleteAppWidgetId(id) }
                    return fallback(context, fallbackTitle, "Preview bind denied")
                }
                widgetIds[localId] = id
                providers[localId] = providerString
            }

            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            runCatching { manager.updateAppWidgetOptions(id, options) }

            val info = manager.getAppWidgetInfo(id) ?: return fallback(context, fallbackTitle, "No AppWidgetInfo")
            val view: AppWidgetHostView = h.createView(appContext, id, info)
            view.setAppWidget(id, info)
            view.setPadding(0, 0, 0, 0)
            view.clipChildren = false
            view.clipToPadding = false
            runCatching { view.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp) }
            h.startListening()
            view
        } catch (t: Throwable) {
            fallback(context, fallbackTitle, t.javaClass.simpleName ?: "Preview error")
        }
    }

    private fun fallback(context: Context, title: String, reason: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 14, 22, 14)
            setBackgroundColor(0xE61B1B1F.toInt())
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = reason
                textSize = 12f
                setTextColor(0xCCFFFFFF.toInt())
            })
        }
    }
}
