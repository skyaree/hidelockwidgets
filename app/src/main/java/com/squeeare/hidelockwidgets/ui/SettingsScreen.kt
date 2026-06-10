package com.squeeare.hidelockwidgets.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(state: MainUiState, vm: MainViewModel, padding: PaddingValues) {
    val bgPicker = rememberLauncherForActivityResult(OpenDocument()) { it?.let(vm::importBackground) }
    val fgPicker = rememberLauncherForActivityResult(OpenDocument()) { it?.let(vm::importForeground) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp + padding.calculateTopPadding(), bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("HideLockWidgets", style = MaterialTheme.typography.headlineLarge)
            Text("Виджеты, depth wallpaper и живой preview для локскрина.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Быстрые переключатели", style = MaterialTheme.typography.titleLarge)
                SwitchRow("Виджеты на локскрине", state.config.widgetsEnabled, vm::toggleWidgets)
                SwitchRow("Depth wallpaper", state.config.depthEnabled, vm::toggleDepth)
                SwitchRow("Скрывать стоковые часы", state.config.hideClock, vm::toggleHideClock)
            } }
        }
        item {
            ElevatedCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Виджеты", style = MaterialTheme.typography.titleLarge)
                    AssistChip(onClick = vm::addCustomWidget, label = { Text("Custom") }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = vm::addYandexRecent, modifier = Modifier.weight(1f)) { Text("Yandex Recent") }
                    FilledTonalButton(onClick = vm::addYandexRectangle, modifier = Modifier.weight(1f)) { Text("Rectangle") }
                }
                Text("Выбирай виджет в Preview и двигай его прямо пальцем.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        items(state.config.widgets, key = { it.id }) { w ->
            ElevatedCard(onClick = { vm.selectWidget(w.id); vm.setPage(AppPage.PREVIEW) }) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(w.title, style = MaterialTheme.typography.titleMedium)
                        Text("#${w.id}", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(w.provider.ifBlank { "provider не указан" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("x=${w.xDp}, y=${w.yDp}, ${w.widthDp}×${w.heightDp}dp, scale=${w.scalePercent}%")
                }
            }
        }
        item {
            ElevatedCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Depth images", style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = { bgPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать задний план") }
                Text(state.config.backgroundPath ?: "Не выбран", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { fgPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать передний план") }
                Text(state.config.foregroundPath ?: "Не выбран", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::apply, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Done, null); Spacer(Modifier.width(8.dp)); Text("Apply") }
                OutlinedButton(onClick = vm::save, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
