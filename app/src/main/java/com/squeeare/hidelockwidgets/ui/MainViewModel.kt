package com.squeeare.hidelockwidgets.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.squeeare.hidelockwidgets.data.AvailableWidget
import com.squeeare.hidelockwidgets.data.ConfigRepository
import com.squeeare.hidelockwidgets.data.DepthConfig
import com.squeeare.hidelockwidgets.data.WidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class AppPage { HOME, PREVIEW }
enum class ActiveLayer { BACKGROUND, WIDGET, FOREGROUND }

data class MainUiState(
    val page: AppPage = AppPage.HOME,
    val activeLayer: ActiveLayer = ActiveLayer.WIDGET,
    val selectedWidgetId: Int? = null,
    val config: DepthConfig = DepthConfig(),
    val availableWidgets: List<AvailableWidget> = emptyList(),
    val widgetPickerOpen: Boolean = false,
    val currentWidgetsOpen: Boolean = true,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConfigRepository(app)
    private val _state = MutableStateFlow(
        MainUiState(
            config = repo.load(),
            availableWidgets = repo.getInstalledWidgets()
        )
    )
    val state: StateFlow<MainUiState> = _state

    fun setPage(page: AppPage) = _state.update { it.copy(page = page) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun refreshProviders() {
        _state.update { it.copy(availableWidgets = repo.getInstalledWidgets(), message = "Список виджетов обновлён") }
    }

    fun toggleWidgetPicker() = _state.update { it.copy(widgetPickerOpen = !it.widgetPickerOpen) }
    fun closeWidgetPicker() = _state.update { it.copy(widgetPickerOpen = false) }
    fun toggleCurrentWidgets() = _state.update { it.copy(currentWidgetsOpen = !it.currentWidgetsOpen) }

    fun setActiveLayer(layer: ActiveLayer) = _state.update { it.copy(activeLayer = layer) }
    fun selectWidget(id: Int) = _state.update { it.copy(activeLayer = ActiveLayer.WIDGET, selectedWidgetId = id, page = AppPage.PREVIEW) }

    fun toggleHideClock(value: Boolean) = updateConfig { copy(hideClock = value) }
    fun toggleDepth(value: Boolean) = updateConfig { copy(depthEnabled = value) }
    fun toggleWidgets(value: Boolean) = updateConfig { copy(widgetsEnabled = value) }

    fun importBackground(uri: Uri) {
        val path = repo.importImage(uri, "depth_background")
        if (path != null) updateConfig("Задний план выбран") { copy(backgroundPath = path) }
        else show("Не удалось выбрать задний план")
    }

    fun importForeground(uri: Uri) {
        val path = repo.importImage(uri, "depth_foreground")
        if (path != null) updateConfig("Передний план выбран") { copy(foregroundPath = path) }
        else show("Не удалось выбрать передний план")
    }

    fun addWidget(widget: AvailableWidget) {
        val nextId = ((_state.value.config.widgets.maxOfOrNull { it.id } ?: 0) + 1)
        val startY = 120 + (_state.value.config.widgets.size * 34)
        val newWidget = WidgetConfig(
            id = nextId,
            title = widget.title,
            provider = widget.provider,
            x = 0,
            y = startY,
            width = widget.minWidth.coerceIn(180, 360),
            height = widget.minHeight.coerceIn(80, 220),
            scale = 100,
            live = true,
            visible = true
        )
        _state.update { state ->
            val updated = state.config.copy(widgets = state.config.widgets + newWidget)
            repo.save(updated)
            state.copy(
                config = updated,
                selectedWidgetId = nextId,
                activeLayer = ActiveLayer.WIDGET,
                widgetPickerOpen = false,
                page = AppPage.PREVIEW,
                message = "Виджет добавлен на превью"
            )
        }
    }

    fun deleteSelected() {
        val id = _state.value.selectedWidgetId ?: return
        _state.update { state ->
            val updatedWidgets = state.config.widgets.filterNot { it.id == id }
            val updated = state.config.copy(widgets = updatedWidgets)
            repo.save(updated)
            state.copy(config = updated, selectedWidgetId = updatedWidgets.firstOrNull()?.id, message = "Удалено")
        }
    }

    fun resetWidgets() {
        updateConfig("Все виджеты сброшены") { copy(widgets = emptyList()) }
        _state.update { it.copy(selectedWidgetId = null) }
    }

    fun resetAll() {
        val empty = DepthConfig()
        repo.save(empty)
        _state.update { it.copy(config = empty, selectedWidgetId = null, activeLayer = ActiveLayer.WIDGET, message = "Настройки сброшены") }
    }

    fun moveActive(dxPx: Float, dyPx: Float) {
        val dx = dxPx.toInt()
        val dy = dyPx.toInt()
        if (dx == 0 && dy == 0) return
        val s = _state.value
        when (s.activeLayer) {
            ActiveLayer.BACKGROUND -> updateConfig { copy(backgroundX = backgroundX + dx, backgroundY = backgroundY + dy) }
            ActiveLayer.FOREGROUND -> updateConfig { copy(foregroundX = foregroundX + dx, foregroundY = foregroundY + dy) }
            ActiveLayer.WIDGET -> {
                val id = s.selectedWidgetId ?: s.config.widgets.firstOrNull()?.id ?: return
                updateWidget(id) { copy(x = x + dx, y = y + dy) }
            }
        }
    }

    fun scaleActive(factor: Float) {
        if (factor == 1f) return
        val percentDelta = ((factor - 1f) * 100f).toInt()
        if (percentDelta == 0) return
        val s = _state.value
        when (s.activeLayer) {
            ActiveLayer.BACKGROUND -> updateConfig { copy(backgroundScale = (backgroundScale + percentDelta).coerceIn(30, 400)) }
            ActiveLayer.FOREGROUND -> updateConfig { copy(foregroundScale = (foregroundScale + percentDelta).coerceIn(30, 400)) }
            ActiveLayer.WIDGET -> {
                val id = s.selectedWidgetId ?: s.config.widgets.firstOrNull()?.id ?: return
                updateWidget(id) { copy(scale = (scale + percentDelta).coerceIn(40, 250)) }
            }
        }
    }

    fun nudgeWidget(id: Int, dx: Int, dy: Int) = updateWidget(id) { copy(x = x + dx, y = y + dy) }
    fun resizeWidget(id: Int, dw: Int, dh: Int) = updateWidget(id) { copy(width = (width + dw).coerceIn(80, 600), height = (height + dh).coerceIn(50, 500)) }
    fun scaleWidget(id: Int, delta: Int) = updateWidget(id) { copy(scale = (scale + delta).coerceIn(40, 250)) }

    fun apply() {
        val ok = repo.applyWithRoot(_state.value.config)
        show(if (ok) "Применено. SystemUI перезапущен" else "Не удалось применить через su")
    }

    fun restartSystemUi() {
        show(if (repo.restartSystemUi()) "SystemUI перезапущен" else "Не удалось перезапустить SystemUI")
    }

    fun softRestart() {
        show(if (repo.softRestart()) "Soft restart выполнен" else "Soft restart не выполнен")
    }

    private fun updateWidget(id: Int, block: WidgetConfig.() -> WidgetConfig) {
        _state.update { state ->
            val updated = state.config.copy(widgets = state.config.widgets.map { if (it.id == id) it.block() else it })
            repo.save(updated)
            state.copy(config = updated, selectedWidgetId = id)
        }
    }

    private fun updateConfig(message: String? = null, block: DepthConfig.() -> DepthConfig) {
        _state.update { state ->
            val updated = state.config.block()
            repo.save(updated)
            state.copy(config = updated, message = message ?: state.message)
        }
    }

    private fun show(message: String) = _state.update { it.copy(message = message) }
}
