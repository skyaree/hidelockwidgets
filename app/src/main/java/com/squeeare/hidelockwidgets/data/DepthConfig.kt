package com.squeeare.hidelockwidgets.data

data class WidgetConfig(
    val id: Int = 1,
    val title: String = "Яндекс Музыка",
    val provider: String = "ru.yandex.music/ru.yandex.music.ui.widget.WidgetRecentlyRectangleReceiver",
    val xDp: Int = 0,
    val yDp: Int = 120,
    val widthDp: Int = 320,
    val heightDp: Int = 160,
    val scalePercent: Int = 100,
    val liveMode: Boolean = true,
    val snapshot: String = "/data/local/tmp/hidelockwidgets/widget_snapshot.png",
    val visible: Boolean = true
)

data class DepthConfig(
    val widgetsEnabled: Boolean = true,
    val hideClock: Boolean = true,
    val depthEnabled: Boolean = false,
    val backgroundPath: String? = null,
    val foregroundPath: String? = null,
    val backgroundOffsetX: Float = 0f,
    val backgroundOffsetY: Float = 0f,
    val backgroundScale: Float = 1f,
    val foregroundOffsetX: Float = 0f,
    val foregroundOffsetY: Float = 0f,
    val foregroundScale: Float = 1f,
    val widgets: List<WidgetConfig> = emptyList()
)
