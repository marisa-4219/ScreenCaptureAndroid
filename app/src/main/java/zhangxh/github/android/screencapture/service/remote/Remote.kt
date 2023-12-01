package zhangxh.github.android.screencapture.service.remote

import com.google.gson.Gson
import zhangxh.github.android.screencapture.service.entity.CodecMetadata
import java.io.ByteArrayOutputStream

// "10.0.2.2"
class Remote(host: String, port: Int, private val metadata: CodecMetadata) {
    companion object {
        const val UNSET_CODEC_CONFIG = 1;
        const val READY = 2;
        const val ERROR = 3;
    }

    private val locker: Any = Any()
    private var config: ByteArray? = null;
    private var status = UNSET_CODEC_CONFIG;
    private val client: NettyClient = NettyClient(host, port).apply {
        setListener(this@Remote::onClientStatusChange)
        connect()
    }

    private fun onClientStatusChange(clientStatus: Int) {
        synchronized(locker) {
            when (clientStatus) {
                NettyClient.STATUS_CONNECTED -> {
                    if (status == ERROR) {
                        status = READY
                    } else if (status == READY) {
                        sendMetadata()
                    }
                }

                NettyClient.STATUS_CONNECTING -> {
                    if (status == READY) {
                        status = ERROR
                    }
                }

                NettyClient.STATUS_DISCONNECT -> {
                    if (status == READY) {
                        status = ERROR
                    }
                }

                NettyClient.STATUS_RELEASE -> {
                    if (status == READY) {
                        status = ERROR
                    }
                }
            }
        }
    }

    private fun sendMetadata() {
        client.sendMessage(
            Packet(
                Packet.TYPE_KEYFRAME,
                Gson().toJson(metadata)!!.toByteArray()
            ).toByteArray()
        )
    }

    fun setCodecConfig(config: ByteArray) {
        synchronized(locker) {
            this.config = config
            if (this.status == UNSET_CODEC_CONFIG) {
                if (client.getStatus() == NettyClient.STATUS_CONNECTED) {
                    this.status = READY;
                } else {
                    this.status = ERROR
                }
            } else {
                throw IllegalStateException();
            }
        }
    }

    fun clearCodecConfig() {
        synchronized(locker) {
            this.config = null
            this.status = UNSET_CODEC_CONFIG
        }
    }

    fun sendKeyframe(keyframe: ByteArray) {
        if (this.status != READY) return

        val os = ByteArrayOutputStream()
        os.write(config)
        os.write(keyframe)

        client.sendMessage(Packet(Packet.TYPE_KEYFRAME, os.toByteArray()).toByteArray())
    }

    fun sendNormalFrame(frame: ByteArray) {
        if (this.status != READY) return

        client.sendMessage(Packet(Packet.TYPE_FRAME, frame).toByteArray())
    }


    fun release() {
        client.release()
    }

}