package com.squeeare.hidelockwidgets.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.squeeare.hidelockwidgets.data.AvailableWidget
import com.squeeare.hidelockwidgets.data.WidgetConfig

@Composable
fun HomeScreen(state: MainUiState, vm: MainViewModel, padding: PaddingValues) {
    val bgPicker = rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importBackground(uri)
    }
    val fgPicker = rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importForeground(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp + padding.calculateTopPadding(), bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Главная",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Конструктор локскрина: виджеты, глубина, часы и быстрый apply.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            StudioCard {
                BigActionRow(
                    title = "Добавить виджеты",
                    subtitle = "Открыть список доступных AppWidget",
                    icon = Icons.Rounded.Add,
                    trailing = {
                        Icon(
                            imageVector = if (state.widgetPickerOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null
                        )
                    },
                    onClick = { vm.toggleWidgetPicker() }
                )

                AnimatedVisibility(
                    visible = state.widgetPickerOpen,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = vm::refreshProviders,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Обновить")
                            }
                            OutlinedButton(
                                onClick = vm::closeWidgetPicker,
                                modifier = Modifier.weight(1f)
                            ) { Text("Закрыть") }
                        }

                        if (state.availableWidgets.isEmpty()) {
                            EmptyText("Виджеты не найдены. Проверь, что на устройстве есть приложения с AppWidget.")
                        } else {
                            state.availableWidgets.take(24).forEach { widget ->
                                AvailableWidgetRow(widget = widget, onClick = { vm.addWidget(widget) })
                            }
                            if (state.availableWidgets.size > 24) {
                                EmptyText("Показаны первые 24 виджета. Нажми Обновить после установки нужного приложения.")
                            }
                        }
                    }
                }

                DividerSoft()

                BigActionRow(
                    title = "Сбросить все виджеты",
                    subtitle = "Удалить виджеты из превью и конфига",
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
                    subtitle = "Background → Widgets → Foreground",
                    checked = state.config.depthEnabled,
                    onChange = vm::toggleDepth
                )
                Spacer(Modifier.height(14.dp))
                ImagePickRow(
                    title = "Передний план",
                    path = state.config.foregroundPath,
                    onPick = { fgPicker.launch(arrayOf("image/*")) }
                )
                Spacer(Modifier.height(10.dp))
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
                    Text(
                        text = "Виджетов на экране: ${state.config.widgets.size}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { vm.toggleCurrentWidgets() }) {
                        Icon(
                            if (state.currentWidgetsOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = vm::restartSystemUi, modifier = Modifier.weight(1f)) {
                        Text("Restart SystemUI")
                    }
                    FilledTonalButton(onClick = vm::softRestart, modifier = Modifier.weight(1f)) {
                        Text("Soft Restart")
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = vm::resetAll, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить все настройки")
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::apply, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(26.dp)) {
                    Icon(Icons.Rounded.Done, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Применить")
                }
            }
        }
    }
}

@Composable
private fun StudioCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun BigActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(14.dp))
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(widget.title.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(widget.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(widget.provider, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.Add, null)
    }
}

@Composable
private fun CurrentWidgetCard(widget: WidgetConfig, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${widget.title}  #${widget.id}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("X:${widget.x}  Y:${widget.y}", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(3.dp))
        Text("${widget.width}×${widget.height}, ${widget.scale}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(widget.provider, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DividerSoft() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(12.dp)
    )
}
