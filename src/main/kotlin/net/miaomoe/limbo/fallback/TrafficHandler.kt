package net.miaomoe.limbo.fallback

import io.netty.buffer.ByteBuf
import io.netty.channel.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.fallback.handler.FallbackInitializer

@Suppress("MemberVisibilityCanBePrivate")
@ChannelHandler.Sharable
object TrafficHandler : ChannelDuplexHandler() {

    var tx = 0L
        private set
    var rx = 0L
        private set

    private val openConnections = MutableStateFlow(mutableListOf<FallbackHandler>())

    val oc get() = openConnections.value.size

    val connections get() = openConnections.value.toList()

    private fun getHandler(pipeline: ChannelPipeline) = pipeline[FallbackInitializer.HANDLER] as FallbackHandler

    override fun channelActive(ctx: ChannelHandlerContext) {
        openConnections.value.add(getHandler(ctx.channel().pipeline()))
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        openConnections.value.remove(getHandler(ctx.channel().pipeline()))
        super.channelInactive(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is ByteBuf) rx += msg.readableBytes()
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        if (msg is ByteBuf) tx += msg.readableBytes()
        super.write(ctx, msg, promise)
    }

    fun conversionBytesFormat(bytes: Long): String {
        val bytesPerKiB = 1024L
        val bytesPerMiB = bytesPerKiB * 1024L
        val bytesPerGiB = bytesPerMiB * 1024L
        val bytesPerTiB = bytesPerGiB * 1024L

        return when (bytes) {
            in 0L..<bytesPerKiB -> "$bytes B"
            in bytesPerKiB..<bytesPerMiB -> "${bytes / bytesPerKiB} KB"
            in bytesPerMiB..<bytesPerGiB -> "${bytes / bytesPerMiB} MB"
            in bytesPerGiB..<bytesPerTiB -> "${bytes / bytesPerGiB} GB"
            else -> "${bytes / bytesPerTiB} TB"
        }
    }

}