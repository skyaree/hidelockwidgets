package com.squeeare.hidelockwidgets.data

data class DepthConfig(
    val depthEnabled: Boolean = true,

    val backgroundPath: String? = null,
    val foregroundPath: String? = null,

    val backgroundOffsetX: Float = 0f,
    val backgroundOffsetY: Float = 0f,
    val backgroundScale: Float = 1f,
    val backgroundAlpha: Float = 1f,

    val foregroundOffsetX: Float = 0f,
    val foregroundOffsetY: Float = 0f,
    val foregroundScale: Float = 1f,
    val foregroundAlpha: Float = 1f,

    val previewWidgetCount: Int = 1
)
