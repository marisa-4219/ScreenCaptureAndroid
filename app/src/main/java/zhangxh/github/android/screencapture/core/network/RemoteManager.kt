package zhangxh.github.android.screencapture.core.network

import com.google.gson.Gson
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.util.ReferenceCountUtil
import zhangxh.github.android.screencapture.core.domain.ScreenCaptureMessage
import zhangxh.github.android.screencapture.core.domain.ScreenCaptureMetadata
import zhangxh.github.android.screencapture.utils.logger
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 远端
 *
 * @param host 远端地址
 * @param port 远端端口
 * @param metadata codec metadata
 */
class RemoteManager(
    private val host: String, private val port: Int, private val metadata: ScreenCaptureMetadata
) {

    companion object {
        const val UNINITIALIZED = 0;
        const val READY = 1;
        const val ERROR = 2;
        const val RELEASE = 3;
    }

    /**
     * 同步锁
     */
    private val locker = Any()

    /**
     * 状态
     */
    private var status: Int = UNINITIALIZED

    /**
     * codec conf
     */
    private var conf: ByteArray? = null

    /**
     * 释放远端
     */
    fun release() {

    }

    /**
     * 当前状态
     *
     * @return 状态
     */
    fun status(): Int {
        return this.status
    }

    /**
     * 设置CodecConfig
     *
     * @param conf 新的 CODEC_CONFIG 如果为 NULL 则清除原来的 CODEC_CONFIG, Remote的状态将恢复到 UNINITIALIZED
     */
    fun resetCodecConfig(conf: ByteArray?) {
        synchronized(locker) {
            if (conf == null) { // 如果参数为NULL 则清除当前CodecConfig 将状态恢复到 UNINITIALIZED
                this.conf = null

                if (status == UNINITIALIZED) {
                    return
                } else {
                    sc(UNINITIALIZED)
                }
            } else {
                sc(
                    if (conn) {
                        READY
                    } else {
                        ERROR
                    }
                )

                this.conf = conf
            }
        }
    }

    /**
     * 发送 KEYFRAME
     *
     * @param frame KEYFRAME
     */
    fun sendKeyFrame(frame: ByteArray) {
        if (this.status != READY) return
        val body = synchronized(locker) {
            ByteArrayOutputStream().run {
                write(conf)
                write(frame)
                toByteArray()
            }
        }
        val message = ScreenCaptureMessage(ScreenCaptureMessage.TYPE_KEYFRAME, body)
        this.handler.send(message.toByteArray())

    }

    /**
     * 发送 FRAME
     *
     * @param frame FRAME
     */
    fun sendNormalFrame(frame: ByteArray) {
        if (this.status != READY) return
        this.handler.send(ScreenCaptureMessage(ScreenCaptureMessage.TYPE_FRAME, frame).toByteArray())
    }

    /**
     * status change
     */
    private fun sc(status: Int) {
        this.status = status
    }

    /**
     * 发送metadata
     */
    private fun sendMetadata() {
        val metadata = Gson().toJson(metadata)!!.toByteArray()
        val message = ScreenCaptureMessage(ScreenCaptureMessage.TYPE_METADATA, metadata)
        this.handler.send(message.toByteArray())

        logger().info("Send Metadata.")

    }

    // ********************************************************* netty *********************************************************

    /**
     * netty connect 状态
     */
    private var conn: Boolean = false

    /**
     * netty 工作线程组
     */
    private val worker = NioEventLoopGroup()

    /**
     * netty channel 处理器
     */
    private val handler = NettyChannelHandler()

    /**
     * netty client channel
     */
    private var channel: ChannelFuture? = null

    /**
     * netty bootstrap
     */
    private val bootstrap = Bootstrap()
        .group(worker)
        .channel(NioSocketChannel::class.java)
        .handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(channel: SocketChannel) {
                channel.pipeline().addLast(LengthFieldPrepender(4), handler)
            }
        })

    /**
     * 连接远端
     */
    fun connect() {
        synchronized(locker) {
            if (conn) return
            if (status == RELEASE) return

            // 连接, 并设置重连机制
            channel = (bootstrap.connect(host, port) as ChannelFuture).apply {
                addListener {
                    synchronized(locker) {
                        val future = it as ChannelFuture

                        if (status == RELEASE) return@addListener

                        if (future.isSuccess) {
                            conn = true
                        } else {
                            reconnect()
                        }
                    }
                }
            }.sync()
        }
    }

    private fun reconnect() {
        if (status == RELEASE) return
        // 修改连接状态
        conn = false
        // 清理上一个channel
        channel?.channel()?.close()

        worker.schedule({
            this.connect()
        }, 1, TimeUnit.SECONDS)

        logger().info("Netty Client Reconnecting ...")
    }


    @Sharable
    private inner class NettyChannelHandler : ChannelInboundHandlerAdapter() {
        private var ctx: ChannelHandlerContext? = null

        override fun channelActive(ctx: ChannelHandlerContext?) {
            this.ctx = ctx
            logger().info("Netty Client Connected")

            sendMetadata()
        }

        override fun channelInactive(ctx: ChannelHandlerContext?) {
            this.ctx = null
            logger().info("Netty Client Disconnected trying to reconnect")
            synchronized(locker) {
                reconnect()
            }
        }

        override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
            sendMetadata()
            ReferenceCountUtil.release(msg);
        }

        fun send(bytes: ByteArray) {
            if (status != READY) {
                logger().warn("remote status is not a READY current status: $status")
                return
            }

            if (ctx == null) {
                logger().warn("netty channel context is null")
                return
            }

            if (!conn) {
                logger().warn("netty client has been disconnected")
                return
            }

            ctx?.writeAndFlush(Unpooled.copiedBuffer(bytes))
        }
    }

}