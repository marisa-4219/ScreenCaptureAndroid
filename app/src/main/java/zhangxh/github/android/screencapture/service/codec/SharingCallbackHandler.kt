package zhangxh.github.android.screencapture.service.codec

import android.media.MediaCodec
import android.media.MediaFormat
import zhangxh.github.android.screencapture.service.remote.Remote
import zhangxh.github.android.screencapture.utils.logger
import zhangxh.github.android.screencapture.utils.toInt16ByteArray
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class SharingCallbackHandler(
    private val remote: Remote
) : MediaCodec.Callback() {

//    private fun keyframe(codec: MediaCodec): ByteArray {
//        val keyframe = ByteArrayOutputStream().run {
//            // FLV TAG
//            write(0x17) // FrameType
//            write(0x00) // AVCPacketType
//            write(0x00)
//            write(0x00)
//            write(0x00)
//            /* AVC CONFIG */
//            write(ByteArrayOutputStream().run {
//                val sps = codec.outputFormat.getByteBuffer("csd-0")!!.let {
//                    it.position(4)
//                    val bytes = ByteArray(it.remaining())
//                    it.get(bytes)
//                    return@let bytes
//                }
//                val pps = codec.outputFormat.getByteBuffer("csd-1")!!.let {
//                    it.position(4)
//                    val bytes = ByteArray(it.remaining())
//                    it.get(bytes)
//                    return@let bytes
//                }
//
//                // HEAD
//                write(0x01)
//                write(sps, 1, 3)
//                write(0xFF)
//                write(0xE1)
//                // SPS
//                write(sps.size.toInt16ByteArray())
//                write(sps, 0, sps.size)
//
//                write(0x01)
//                // PPS
//                write(pps.size.toInt16ByteArray())
//                write(pps, 0, pps.size)
//
//                // RETURN
//                toByteArray()
//            })
//
//            // RETURN
//            toByteArray()
//        }
//
//        logger().info("Keyframe Generated.")
//        return keyframe
//    }


    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
//        client.sendMessage(keyframe(codec))
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        if (index >= 0) {
            try {
                val buffer: ByteBuffer = codec.getOutputBuffer(index) ?: return
                val bytes = ByteArray(info.size)
                buffer.get(bytes)

                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    logger().info("CODEC_CONFIG")
                    remote.clearCodecConfig()
                    remote.setCodecConfig(bytes)
                    return
                }

                if ((info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                    logger().info("KEY_FRAME")
                    remote.sendKeyframe(bytes)
                    return
                }

                logger().info("DEFAULT_FRAME")
                remote.sendNormalFrame(bytes)

            } finally {
                codec.releaseOutputBuffer(index, false)
            }
        }

    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        logger().error(e.message, e)
    }
}