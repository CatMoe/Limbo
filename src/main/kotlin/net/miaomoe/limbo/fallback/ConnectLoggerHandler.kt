package net.miaomoe.limbo.fallback

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.packet.common.PacketKeepAlive
import net.miaomoe.blessing.protocol.packet.status.PacketStatusRequest
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger

class ConnectLoggerHandler(
    val fallback: FallbackHandler,
    val logger: Logger? = null
) : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (msg is PacketStatusRequest) log("$fallback has pinged")
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
        if (msg is PacketKeepAlive) log("$fallback has joined")
        super.write(ctx, msg, promise)
    }

    private fun log(message: String) {
        logger?.log(Level.INFO, message)
        fallback.channel.pipeline().remove("limbo-connect-logger")
    }

}