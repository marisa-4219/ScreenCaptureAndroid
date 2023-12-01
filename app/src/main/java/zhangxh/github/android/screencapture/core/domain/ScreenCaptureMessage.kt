package zhangxh.github.android.screencapture.core.domain

import zhangxh.github.android.screencapture.utils.insertInt16
import zhangxh.github.android.screencapture.utils.toInt16ByteArray
import java.io.ByteArrayOutputStream

class ScreenCaptureMessage(
    private val type: Int = TYPE_FRAME,
    private val body: ByteArray
) {
    companion object {
        const val TYPE_METADATA: Int = 0
        const val TYPE_FRAME: Int = 1
        const val TYPE_KEYFRAME: Int = 2
    }

    fun toByteArray(): ByteArray {
        val header = ByteArrayOutputStream().run {
            write(ByteArray(2))             // HEADER_LEN
            write(type.toInt16ByteArray())       // TYPE
            toByteArray()
        }

        header.insertInt16(0, header.size);

        return ByteArrayOutputStream().run {
            write(header)
            write(body)
            toByteArray()
        }
    }

    override fun toString(): String {
        return "FrameDataWrapper(frameType=$type, body=${body.contentToString()})"
    }
}
