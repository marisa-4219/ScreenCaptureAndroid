package zhangxh.github.android.screencapture.core.network.entity

import zhangxh.github.android.screencapture.core.utils.insertInt16
import zhangxh.github.android.screencapture.core.utils.toInt16ByteArray
import java.io.ByteArrayOutputStream

class NettyMessage(
    private val type: Int,
    private val body: ByteArray
) {

    companion object {
        val MESSAGE_TYPE_FRAME: Int = 0
        val MESSAGE_TYPE_KEYFRAME: Int = 0
        val MESSAGE_TYPE_CODEC_CONFIG: Int = 0
        val MESSAGE_TYPE_CODEC_CONFIG: Int = 0
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
