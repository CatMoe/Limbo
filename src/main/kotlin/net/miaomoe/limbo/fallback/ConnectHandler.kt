package net.miaomoe.limbo.fallback

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import net.miaomoe.blessing.event.EventManager
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.message.TitleAction
import net.miaomoe.blessing.protocol.message.TitleTime
import net.miaomoe.blessing.protocol.packet.common.PacketDisconnect
import net.miaomoe.blessing.protocol.packet.common.PacketKeepAlive
import net.miaomoe.blessing.protocol.packet.play.PacketTabListHeader
import net.miaomoe.blessing.protocol.packet.status.PacketStatusRequest
import net.miaomoe.blessing.protocol.registry.State
import net.miaomoe.blessing.protocol.util.ComponentUtil.toComponent
import net.miaomoe.blessing.protocol.util.ComponentUtil.toLegacyText
import net.miaomoe.blessing.protocol.util.ComponentUtil.toMixedComponent
import net.miaomoe.blessing.protocol.version.Version
import net.miaomoe.limbo.LimboBootstrap
import net.miaomoe.limbo.event.FallbackConnectEvent
import net.miaomoe.limbo.event.FallbackDisconnectEvent
import org.apache.logging.log4j.Level

class ConnectHandler(
    private val bootstrap: LimboBootstrap,
    private val fallback: FallbackHandler
) : ChannelDuplexHandler() {

    private var joined = false
    private var kicked = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        EventManager.call(FallbackConnectEvent(bootstrap, fallback)) {
            if (it.isCancelled) ctx.close()
        }
        super.channelActive(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        when (msg) {
            is PacketKeepAlive -> {
                if (!joined) {
                    joined=true
                    log("has connected")
                    val message = bootstrap.config.message
                    val legacy = fallback.version.less(Version.V1_16)
                    message.chat.takeUnless { it.isEmpty() }?.let {
                        for (chat in it) fallback.sendMessage(chat.toComponent(legacy), false)
                    }
                    message.actionBar.takeUnless { it.isEmpty() }?.let { fallback.sendActionbar(it.toComponent(legacy), false) }
                    val title = message.title
                    if (title.fadeIn != 0 || title.stay != 0 || title.fadeOut != 0) {
                        val time = TitleTime(title.fadeIn, title.stay, title.fadeOut)
                        fallback.writeTitle(TitleAction.TITLE, title.title.toMixedComponent(legacy))
                        fallback.writeTitle(TitleAction.SUBTITLE, title.subTitle.toMixedComponent(legacy))
                        fallback.writeTitle(TitleAction.TIMES, time)
                    }
                    val tab = message.tab
                    fallback.write(PacketTabListHeader(
                        tab.header.toComponent(legacy),
                        tab.footer.toComponent(legacy)
                    ), true)
                }
            }
            is PacketStatusRequest -> log("has pinged")
            is PacketDisconnect -> {
                if (!kicked) kicked=true else return
                log("has been kicked: ${msg.message.toComponent().toLegacyText()}")
            }
        }
        super.channelRead(ctx, msg)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        EventManager.call(FallbackDisconnectEvent(bootstrap, fallback))
        if (fallback.state.let { (it == State.PLAY || it == State.CONFIGURATION) && !kicked }) log("has disconnected")
        super.channelInactive(ctx)
    }

    private fun log(message: String) {
        val fallback = this.fallback.toString().removePrefix("FallbackHandler")
        bootstrap.log(Level.INFO, "$fallback $message")
    }

}