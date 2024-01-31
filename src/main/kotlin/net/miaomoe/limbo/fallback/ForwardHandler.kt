package net.miaomoe.limbo.fallback

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.packet.handshake.PacketHandshake
import net.miaomoe.blessing.protocol.util.ComponentUtil.toComponent
import java.net.InetSocketAddress

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
                        fallback.address = InetSocketAddress.createUnresolved(split[1], msg.port)
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