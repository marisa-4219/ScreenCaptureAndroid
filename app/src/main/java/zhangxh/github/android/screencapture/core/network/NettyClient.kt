package zhangxh.github.android.screencapture.core.network

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
import zhangxh.github.android.screencapture.core.utils.logger
import java.util.concurrent.TimeUnit

@ChannelHandler.Sharable
class NettyClient(var host: String = "10.0.2.2", var port: Int = 8888) : ChannelInboundHandlerAdapter() {

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
                status = STATUS_CONNECTING
            }

            try {
                main?.apply {
                    channel().close().sync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            logger().info("-------------------------------------------- CLIENT CONNECTING -----------------------------------------------")
            main = bootstrap.connect(host, port)
            main!!.addListener(ClientDisconnectListener(this))
        }
    }

    fun release() {
        synchronized(locker) {
            status = STATUS_RELEASE
            main!!.channel().close().sync()
            worker.shutdownGracefully()
        }
    }

    fun sendMessage(msg: ByteArray) {
        if (status == STATUS_CONNECTED) {
            ctx?.writeAndFlush(Unpooled.copiedBuffer(msg))
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        this.ctx = ctx
        logger().info("-------------------------------------------- CLIENT CONNECTED -----------------------------------------------")
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        logger().info("-------------------------------------------- MESSAGE RECEIVE -----------------------------------------------")
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger().info("-------------------------------------------- CLIENT DISCONNECT -----------------------------------------------")
        synchronized(locker) {
            if (status == STATUS_RELEASE) return
            status = STATUS_DISCONNECT
            ctx.channel().eventLoop().schedule({ connect() }, 1, TimeUnit.SECONDS)
        }
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