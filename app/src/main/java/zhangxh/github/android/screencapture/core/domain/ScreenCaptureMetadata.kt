package zhangxh.github.android.screencapture.core.domain

data class ScreenCaptureMetadata(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val bitRate: Int,
    val frameRate: Int,
    val frameInterval: Int,
)