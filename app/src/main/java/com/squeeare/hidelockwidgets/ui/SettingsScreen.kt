package com.squeeare.hidelockwidgets.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.squeeare.hidelockwidgets.data.AvailableWidget
import com.squeeare.hidelockwidgets.data.WidgetConfig

@Composable
fun HomeScreen(state: MainUiState, vm: MainViewModel, padding: PaddingValues) {
    val bgPicker = rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.importBackground(uri)
            vm.selectBackground()
            vm.setPage(AppPage.PREVIEW)
        }
    }
    val fgPicker = rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.importForeground(uri)
            vm.selectForeground()
            vm.setPage(AppPage.PREVIEW)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 18.dp + padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "Главная",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Управляй виджетами, depth-обоями и локскрином. Нажми на элемент в превью — он станет активным.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            StudioCard {
                MainActionRow(
                    title = "Добавить виджеты",
                    subtitle = "Открыть список доступных виджетов",
                    icon = Icons.Rounded.Add,
                    trailing = {
                        Icon(
                            imageVector = if (state.widgetPickerOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    },
                    onClick = { vm.toggleWidgetPicker() }
                )

                AnimatedVisibility(
                    visible = state.widgetPickerOpen,
                    enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                ) {
                    WidgetPickerPanel(
                        widgets = state.availableWidgets,
                        onRefresh = vm::refreshProviders,
                        onClose = vm::closeWidgetPicker,
                        onAdd = vm::addWidget
                    )
                }

                DividerSoft()

                MainActionRow(
                    title = "Сбросить все виджеты",
                    subtitle = "Очистить список и удалить их из конфига",
                    icon = Icons.Rounded.DeleteSweep,
                    onClick = vm::resetWidgets
                )
            }
        }

        item {
            StudioCard {
                SwitchRow(
                    title = "Скрыть системные часы",
                    subtitle = "Прятать стандартные часы локскрина",
                    checked = state.config.hideClock,
                    onChange = vm::toggleHideClock
                )
                DividerSoft()
                SwitchRow(
                    title = "Виджеты на локскрине",
                    subtitle = "Включить слой live-виджетов",
                    checked = state.config.widgetsEnabled,
                    onChange = vm::toggleWidgets
                )
                DividerSoft()
                SwitchRow(
                    title = "Глубинные обои",
                    subtitle = "Задний план → виджеты → передний план",
                    checked = state.config.depthEnabled,
                    onChange = vm::toggleDepth
                )
                Spacer(Modifier.height(16.dp))
                ImagePickRow(
                    title = "Передний план",
                    path = state.config.foregroundPath,
                    onPick = { fgPicker.launch(arrayOf("image/*")) }
                )
                Spacer(Modifier.height(12.dp))
                ImagePickRow(
                    title = "Задний план",
                    path = state.config.backgroundPath,
                    onPick = { bgPicker.launch(arrayOf("image/*")) }
                )
            }
        }

        item {
            StudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Виджетов на экране: ${state.config.widgets.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Текущие элементы превью",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.toggleCurrentWidgets() }) {
                        Icon(
                            if (state.currentWidgetsOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null
                        )
                    }
                }

                AnimatedVisibility(visible = state.currentWidgetsOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.config.widgets.isEmpty()) {
                            EmptyText("Пока пусто. Нажми “Добавить виджеты”.")
                        } else {
                            state.config.widgets.forEach { widget ->
                                CurrentWidgetCard(widget, onClick = { vm.selectWidget(widget.id) })
                            }
                        }
                    }
                }
            }
        }

        item {
            StudioCard {
                Button(
                    onClick = vm::apply,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Применить")
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = vm::restartSystemUi, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.RestartAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Restart")
                    }
                    FilledTonalButton(onClick = vm::softRestart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Cached, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Soft")
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(onClick = vm::resetAll, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить все настройки")
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerPanel(
    widgets: List<AvailableWidget>,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onAdd: (AvailableWidget) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Обновить")
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Закрыть")
            }
        }

        if (widgets.isEmpty()) {
            EmptyText("Виджеты не найдены. Если их должно быть больше — проверь установленные приложения с AppWidget.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 470.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(widgets, key = { it.provider }) { widget ->
                    AvailableWidgetRow(widget = widget, onClick = { onAdd(widget) })
                }
            }
        }
    }
}

@Composable
private fun StudioCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun MainActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ImagePickRow(title: String, path: String?, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(path ?: "Не выбран", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(onClick = onPick) { Text("Выбрать") }
    }
}

@Composable
private fun AvailableWidgetRow(widget: AvailableWidget, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PackageIcon(packageName = widget.packageName, fallback = widget.title.firstOrNull()?.uppercaseChar()?.toString() ?: "W")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(widget.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(widget.provider, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${widget.minWidth}×${widget.minHeight} dp", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Icon(Icons.Rounded.Add, null)
    }
}

@Composable
private fun PackageIcon(packageName: String, fallback: String) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(38.dp))
        } else {
            Text(fallback, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun CurrentWidgetCard(widget: WidgetConfig, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Widgets, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(widget.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("X: ${widget.x}   Y: ${widget.y}   ${widget.width}×${widget.height}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun DividerSoft() {
    Divider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}
