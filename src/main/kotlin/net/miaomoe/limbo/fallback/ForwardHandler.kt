package net.miaomoe.limbo.fallback

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.haproxy.HAProxyMessage
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.fallback.util.ComponentUtil.toComponent
import net.miaomoe.blessing.protocol.packet.handshake.PacketHandshake


class ForwardHandler(
    private val fallback: FallbackHandler,
    private val mode: ForwardMode
) : ChannelInboundHandlerAdapter() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        run {
            when (mode) {
                ForwardMode.BUNGEE -> {
                    if (msg !is PacketHandshake) return@run
                    val split = msg.host.split("\u0000")
                    if (split.size == 3 || split.size == 4) {
                        // Wait to support to set fallback address.
                        fallback.channelRead(ctx, HAProxyMessage(null, null, null, split[1], null, msg.port, 0))
                    } else {
                        fallback.disconnect("You've enabled player info forwarding. Please make sure your proxy's ip-forward is enabled!".toComponent())
                    }
                }
                else -> {}
            }
        }
        super.channelRead(ctx, msg)
    }


    enum class ForwardMode {
        NONE,
        BUNGEE
    }

}