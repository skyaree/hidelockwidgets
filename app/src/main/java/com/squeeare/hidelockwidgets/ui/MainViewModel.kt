package com.squeeare.hidelockwidgets.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.squeeare.hidelockwidgets.data.ConfigRepository
import com.squeeare.hidelockwidgets.data.DepthConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class AppPage {
    SETTINGS,
    PREVIEW
}

enum class EditableLayer {
    BACKGROUND,
    FOREGROUND
}

data class MainUiState(
    val currentPage: AppPage = AppPage.SETTINGS,
    val activeLayer: EditableLayer = EditableLayer.FOREGROUND,
    val config: DepthConfig = DepthConfig(),
    val storagePath: String = "",
    val infoMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConfigRepository(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            config = repository.load(),
            storagePath = repository.getRootPath()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    fun setPage(page: AppPage) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun setActiveLayer(layer: EditableLayer) {
        _uiState.update { it.copy(activeLayer = layer) }
    }

    fun setDepthEnabled(enabled: Boolean) {
        updateConfig { copy(depthEnabled = enabled) }
    }

    fun setPreviewWidgetCount(count: Int) {
        updateConfig { copy(previewWidgetCount = count.coerceIn(1, 4)) }
    }

    fun importBackground(uri: Uri) {
        val path = repository.importBackground(uri)
        if (path != null) {
            updateConfig {
                copy(
                    backgroundPath = path,
                    backgroundOffsetX = 0f,
                    backgroundOffsetY = 0f,
                    backgroundScale = 1f
                )
            }
            showMessage("Задний план импортирован")
        } else {
            showMessage("Не удалось импортировать задний план")
        }
    }

    fun importForeground(uri: Uri) {
        val path = repository.importForeground(uri)
        if (path != null) {
            updateConfig {
                copy(
                    foregroundPath = path,
                    foregroundOffsetX = 0f,
                    foregroundOffsetY = 0f,
                    foregroundScale = 1f
                )
            }
            showMessage("Передний план импортирован")
        } else {
            showMessage("Не удалось импортировать передний план")
        }
    }

    fun resetActiveLayer() {
        val layer = _uiState.value.activeLayer
        when (layer) {
            EditableLayer.BACKGROUND -> updateConfig {
                copy(
                    backgroundOffsetX = 0f,
                    backgroundOffsetY = 0f,
                    backgroundScale = 1f
                )
            }

            EditableLayer.FOREGROUND -> updateConfig {
                copy(
                    foregroundOffsetX = 0f,
                    foregroundOffsetY = 0f,
                    foregroundScale = 1f
                )
            }
        }
    }

    fun nudgeActiveLayer(dx: Float, dy: Float) {
        val layer = _uiState.value.activeLayer
        when (layer) {
            EditableLayer.BACKGROUND -> updateConfig {
                copy(
                    backgroundOffsetX = backgroundOffsetX + dx,
                    backgroundOffsetY = backgroundOffsetY + dy
                )
            }

            EditableLayer.FOREGROUND -> updateConfig {
                copy(
                    foregroundOffsetX = foregroundOffsetX + dx,
                    foregroundOffsetY = foregroundOffsetY + dy
                )
            }
        }
    }

    fun scaleActiveLayer(zoomFactor: Float) {
        val layer = _uiState.value.activeLayer
        when (layer) {
            EditableLayer.BACKGROUND -> updateConfig {
                copy(
                    backgroundScale = (backgroundScale * zoomFactor).coerceIn(0.3f, 4f)
                )
            }

            EditableLayer.FOREGROUND -> updateConfig {
                copy(
                    foregroundScale = (foregroundScale * zoomFactor).coerceIn(0.3f, 4f)
                )
            }
        }
    }

    fun saveConfig() {
        repository.save(_uiState.value.config)
        showMessage("Конфиг сохранён")
    }

    fun clearMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(infoMessage = message) }
    }

    private fun updateConfig(block: DepthConfig.() -> DepthConfig) {
        _uiState.update { state ->
            val updated = state.config.block()
            repository.save(updated)
            state.copy(config = updated)
        }
    }
}
