package zhangxh.github.android.screencapture.core

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import zhangxh.github.android.screencapture.core.domain.ScreenCaptureMetadata
import zhangxh.github.android.screencapture.core.network.RemoteManager
import zhangxh.github.android.screencapture.core.service.AbstractScreenCaptureService
import zhangxh.github.android.screencapture.utils.logger
import java.nio.ByteBuffer

class ScreenCaptureManager(
    private val ctx: ComponentActivity,
    private val mime: String = MediaFormat.MIMETYPE_VIDEO_AVC
) {

    companion object {
        const val READY = 0
        const val RUNNING = 1
        const val RELEASE = 2
    }

    private var locker = Any()

    private var manager: MediaProjectionManager? = null
    private var media: MediaProjection? = null
    private var codec: MediaCodec? = null
    private var display: VirtualDisplay? = null
    private var remote: RemoteManager? = null
    private var metadata: ScreenCaptureMetadata? = null

    private var status: Int = READY

    fun initialize(impl: Class<out AbstractScreenCaptureService>) {

        val dpi = ctx.resources.displayMetrics.densityDpi
        val width = ctx.resources.displayMetrics.widthPixels
        val height = ctx.resources.displayMetrics.heightPixels
        metadata = ScreenCaptureMetadata(width, height, dpi, 2 * width * height / 20, 30, 1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                manager = ctx.getSystemService(ComponentActivity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Toast.makeText(ctx, "屏幕录制服务断开", Toast.LENGTH_SHORT).show()
            }
        }

        ctx.bindService(
            Intent(ctx, impl),
            connection,
            ComponentActivity.BIND_AUTO_CREATE
        )

        remote = RemoteManager("10.0.2.2", 8888, metadata!!).apply { connect() }
    }


    fun start() {
        if (status != READY) {
            throw IllegalStateException("ScreenRecorder status not Ready")
        }

        launcher.launch(manager!!.createScreenCaptureIntent())
    }

    fun stop() {
        synchronized(locker) {
            if (status != RUNNING) {
                throw IllegalStateException("ScreenRecorder status not Running")
            }
            Toast.makeText(ctx, "STOP", Toast.LENGTH_SHORT).show()
            media?.stop()
            codec?.stop()
            codec?.release()
            display?.release()
            status = READY
        }
    }

    fun release() {
        synchronized(locker) {
            try {
                media?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                display?.release()
            } catch (_: Exception) {
            }
            try {
                remote?.release()
            } catch (_: Exception) {
            }
            status = RELEASE
        }
    }

    private val launcher: ActivityResultLauncher<Intent> =
        ctx.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            this::onPermissionRequested
        )

    private fun onPermissionRequested(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            synchronized(locker) {
                val metadata = this.metadata!!
                val manager = manager!!

                val media = manager.getMediaProjection(result.resultCode, result.data as Intent)
                val format = MediaFormat.createVideoFormat(mime, metadata.width, metadata.height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, metadata.bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, metadata.frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, metadata.frameInterval)
                }

                val codec = MediaCodec.createEncoderByType(mime).apply {
                    setCallback(MediaCodecCallback())
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }

                val display = media.createVirtualDisplay(
                    "VirtualDisplay",
                    metadata.width,
                    metadata.height,
                    metadata.dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    codec.createInputSurface(),
                    null,
                    null
                )

                codec.start()

                this.media = media
                this.codec = codec
                this.display = display
                this.status = RUNNING
                
                Toast.makeText(ctx, "START", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class MediaCodecCallback() : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            logger().error(e.message, e)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (index < 0) {
                return
            }
            try {
                val buffer: ByteBuffer = codec.getOutputBuffer(index) ?: return
                val bytes = ByteArray(info.size)
                buffer.get(bytes)
                remote?.let {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        it.resetCodecConfig(bytes)
                        return
                    }
                    if ((info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        it.sendKeyFrame(bytes)
                        return
                    }
                    it.sendNormalFrame(bytes)
                }
            } finally {
                codec.releaseOutputBuffer(index, false)
            }

        }
    }

}