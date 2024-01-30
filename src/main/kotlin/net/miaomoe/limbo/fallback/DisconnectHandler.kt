package net.miaomoe.limbo.fallback

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.registry.State
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import java.util.function.Consumer

class DisconnectHandler(
    private val logger: Logger?,
    private val fallback: FallbackHandler,
    private val listener: Consumer<FallbackHandler>? = null
) : ChannelDuplexHandler() {

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        if (fallback.state == State.PLAY) logger?.log(Level.INFO, "$fallback has disconnected")
        listener?.accept(fallback)
        super.channelInactive(ctx)
    }

}