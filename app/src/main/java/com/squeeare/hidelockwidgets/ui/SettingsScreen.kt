package com.squeeare.hidelockwidgets.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: MainUiState,
    onDepthEnabledChanged: (Boolean) -> Unit,
    onPickBackground: (Uri) -> Unit,
    onPickForeground: (Uri) -> Unit,
    onWidgetCountChanged: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val backgroundPicker = rememberLauncherForActivityResult(
        contract = OpenDocument()
    ) { uri ->
        if (uri != null) onPickBackground(uri)
    }

    val foregroundPicker = rememberLauncherForActivityResult(
        contract = OpenDocument()
    ) { uri ->
        if (uri != null) onPickForeground(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Минималистичная настройка depth wallpaper и preview для lockscreen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Depth wallpaper",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(14.dp))

                ListItem(
                    headlineContent = { Text("Включить depth wallpaper") },
                    supportingContent = {
                        Text("Слои будут использоваться как задний и передний план локскрина")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.config.depthEnabled,
                            onCheckedChange = onDepthEnabledChanged
                        )
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Изображения",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = { backgroundPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать задний план")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.config.backgroundPath ?: "Задний план не выбран",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                FilledTonalButton(
                    onClick = { foregroundPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать передний план (PNG)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.config.foregroundPath ?: "Передний план не выбран",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Preview виджетов",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Количество виджетов: ${state.config.previewWidgetCount}",
                    style = MaterialTheme.typography.bodyLarge
                )

                Slider(
                    value = state.config.previewWidgetCount.toFloat(),
                    onValueChange = { onWidgetCountChanged(it.toInt().coerceIn(1, 4)) },
                    valueRange = 1f..4f,
                    steps = 2
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Путь хранения",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.storagePath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onOpenPreview,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Открыть Preview")
                    }

                    TextButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
