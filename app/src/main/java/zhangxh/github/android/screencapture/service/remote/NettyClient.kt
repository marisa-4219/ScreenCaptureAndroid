package zhangxh.github.android.screencapture.service.remote

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldPrepender
import zhangxh.github.android.screencapture.utils.logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ChannelHandler.Sharable
class NettyClient(private var host: String, private var port: Int) :
    ChannelInboundHandlerAdapter() {
    companion object {
        const val STATUS_DISCONNECT = 0
        const val STATUS_CONNECTING = 1
        const val STATUS_CONNECTED = 2
        const val STATUS_RELEASE = 3
    }

    private var status = STATUS_DISCONNECT;
    private val locker = Any()

    private val worker = NioEventLoopGroup()
    private var main: ChannelFuture? = null
    private var listener: (Int) -> Unit = {}

    private val bootstrap = Bootstrap()
        .group(worker)
        .channel(NioSocketChannel::class.java)
        .handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(channel: SocketChannel) {
                channel.pipeline()
                    .addLast(LengthFieldPrepender(4))
                    .addLast(this@NettyClient)
            }
        })

    private var ctx: ChannelHandlerContext? = null

    fun connect() {
        synchronized(locker) {
            if (status == STATUS_RELEASE) {
                throw IllegalStateException("client is release.")
            } else {
                this.sc(STATUS_CONNECTING)
            }

            try {
                main?.apply {
                    channel().close().sync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            logger().info("Netty Client Connecting....")
            main = bootstrap.connect(host, port)
            main!!.addListener(ClientDisconnectListener(this))
        }
    }

    fun release() {
        synchronized(locker) {
            this.sc(STATUS_RELEASE)

            main!!.channel().close().sync()
            worker.shutdownGracefully()
        }
    }

    fun setListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    fun getStatus(): Int {
        return status
    }

    fun sendMessage(msg: ByteArray) {
        if (status == STATUS_CONNECTED) {
            ctx?.writeAndFlush(Unpooled.copiedBuffer(msg))
        }
    }

    private fun sc(status: Int) {
        this.status = status
        listener(this.status)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        this.ctx = ctx
        logger().info("Netty Client Connected.")
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger().info("Netty Client Disconnect.")
        synchronized(locker) {
            if (status == STATUS_RELEASE) return

            this.sc(STATUS_DISCONNECT)
            ctx.channel().eventLoop().schedule({ connect() }, 1, TimeUnit.SECONDS)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        logger().info("Netty Client Receive Message.")
    }

    inner class ClientDisconnectListener(
        private val client: NettyClient
    ) : ChannelFutureListener {
        override fun operationComplete(future: ChannelFuture) {
            synchronized(locker) {
                if (status == STATUS_RELEASE) return
                if (!future.isSuccess) {
                    status = STATUS_DISCONNECT
                    future.channel().eventLoop().schedule({ client.connect() }, 1, TimeUnit.SECONDS)
                } else {
                    status = STATUS_CONNECTED
                }
            }
        }
    }
}