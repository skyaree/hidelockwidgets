package com.squeeare.hidelockwidgets.data

data class WidgetConfig(
    val id: Int = 1,
    val title: String = "Widget",
    val provider: String = "",
    val x: Int = 0,
    val y: Int = 120,
    val width: Int = 320,
    val height: Int = 160,
    val scale: Int = 100,
    val live: Boolean = true,
    val visible: Boolean = true,
    val snapshot: String = "/data/local/tmp/hidelockwidgets/widget_snapshot.png"
)

data class AvailableWidget(
    val title: String,
    val packageName: String,
    val provider: String,
    val minWidth: Int = 180,
    val minHeight: Int = 80
)

data class DepthConfig(
    val hideClock: Boolean = true,
    val widgetsEnabled: Boolean = true,
    val depthEnabled: Boolean = false,

    val backgroundPath: String? = null,
    val foregroundPath: String? = null,

    val backgroundX: Int = 0,
    val backgroundY: Int = 0,
    val backgroundScale: Int = 100,

    val foregroundX: Int = 0,
    val foregroundY: Int = 0,
    val foregroundScale: Int = 100,

    val widgets: List<WidgetConfig> = emptyList()
)
