package com.squeeare.hidelockwidgets.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.squeeare.hidelockwidgets.data.ConfigRepository
import com.squeeare.hidelockwidgets.data.DepthConfig
import com.squeeare.hidelockwidgets.data.WidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class AppPage { SETTINGS, PREVIEW }
enum class ActiveLayer { BACKGROUND, WIDGET, FOREGROUND }

data class MainUiState(
    val page: AppPage = AppPage.PREVIEW,
    val activeLayer: ActiveLayer = ActiveLayer.WIDGET,
    val selectedWidgetId: Int? = null,
    val config: DepthConfig = DepthConfig(),
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConfigRepository(app)
    private val _state = MutableStateFlow(MainUiState(config = seed(repo.load())))
    val state: StateFlow<MainUiState> = _state

    private fun seed(c: DepthConfig): DepthConfig = if (c.widgets.isNotEmpty()) c else c.copy(widgets = listOf(
        WidgetConfig(id = 1, title = "Яндекс Музыка", yDp = 120)
    ))

    fun setPage(page: AppPage) = _state.update { it.copy(page = page) }
    fun setActiveLayer(layer: ActiveLayer) = _state.update { it.copy(activeLayer = layer) }
    fun selectWidget(id: Int) = _state.update { it.copy(activeLayer = ActiveLayer.WIDGET, selectedWidgetId = id) }

    fun toggleWidgets(v: Boolean) = update { copy(widgetsEnabled = v) }
    fun toggleDepth(v: Boolean) = update { copy(depthEnabled = v) }
    fun toggleHideClock(v: Boolean) = update { copy(hideClock = v) }

    fun addYandexRecent() = addWidget("Яндекс Recently", "ru.yandex.music/ru.yandex.music.ui.widget.WidgetRecentlyRectangleReceiver")
    fun addYandexRectangle() = addWidget("Яндекс Rectangle", "ru.yandex.music/ru.yandex.music.ui.widget.WidgetRectangleReceiver")
    fun addCustomWidget() = addWidget("Custom widget", "")

    private fun addWidget(title: String, provider: String) {
        val nextId = ((_state.value.config.widgets.maxOfOrNull { it.id } ?: 0) + 1)
        val widget = WidgetConfig(id = nextId, title = title, provider = provider, yDp = 92 + (nextId - 1) * 96)
        update { copy(widgets = widgets + widget) }
        _state.update { it.copy(activeLayer = ActiveLayer.WIDGET, selectedWidgetId = nextId, message = "Виджет добавлен") }
    }

    fun deleteSelectedWidget() {
        val id = _state.value.selectedWidgetId ?: return
        update { copy(widgets = widgets.filterNot { it.id == id }) }
        _state.update { it.copy(selectedWidgetId = null, message = "Виджет удалён") }
    }

    fun updateSelectedProvider(provider: String) = updateWidget { copy(provider = provider) }
    fun updateSelectedSize(w: Int, h: Int) = updateWidget { copy(widthDp = w.coerceIn(80, 480), heightDp = h.coerceIn(40, 500)) }
    fun updateSelectedScale(scale: Int) = updateWidget { copy(scalePercent = scale.coerceIn(30, 200)) }

    fun moveActive(dx: Float, dy: Float) {
        when (_state.value.activeLayer) {
            ActiveLayer.BACKGROUND -> update { copy(backgroundOffsetX = backgroundOffsetX + dx, backgroundOffsetY = backgroundOffsetY + dy) }
            ActiveLayer.FOREGROUND -> update { copy(foregroundOffsetX = foregroundOffsetX + dx, foregroundOffsetY = foregroundOffsetY + dy) }
            ActiveLayer.WIDGET -> updateWidget { copy(xDp = xDp + dx.toInt(), yDp = yDp + dy.toInt()) }
        }
    }

    fun scaleActive(factor: Float) {
        when (_state.value.activeLayer) {
            ActiveLayer.BACKGROUND -> update { copy(backgroundScale = (backgroundScale * factor).coerceIn(0.35f, 4f)) }
            ActiveLayer.FOREGROUND -> update { copy(foregroundScale = (foregroundScale * factor).coerceIn(0.35f, 4f)) }
            ActiveLayer.WIDGET -> updateWidget { copy(scalePercent = (scalePercent * factor).toInt().coerceIn(30, 200)) }
        }
    }

    fun importBackground(uri: Uri) {
        repo.importImage(uri, "depth_background")?.let { path -> update { copy(backgroundPath = path) } }
    }
    fun importForeground(uri: Uri) {
        repo.importImage(uri, "depth_foreground")?.let { path -> update { copy(foregroundPath = path) } }
    }

    fun save() { repo.save(_state.value.config); _state.update { it.copy(message = "Сохранено") } }
    fun apply() {
        repo.save(_state.value.config)
        val ok = repo.applyLegacyConfigWithRoot(_state.value.config)
        _state.update { it.copy(message = if (ok) "Применено. SystemUI перезапущен" else "Root apply не сработал") }
    }
    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun update(block: DepthConfig.() -> DepthConfig) {
        _state.update { s -> s.copy(config = s.config.block()) }
        repo.save(_state.value.config)
    }

    private fun updateWidget(block: WidgetConfig.() -> WidgetConfig) {
        val id = _state.value.selectedWidgetId ?: _state.value.config.widgets.firstOrNull()?.id ?: return
        update { copy(widgets = widgets.map { if (it.id == id) it.block() else it }) }
    }
}
