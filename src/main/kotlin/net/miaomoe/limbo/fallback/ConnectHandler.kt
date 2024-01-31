package net.miaomoe.limbo.fallback

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.message.TitleAction
import net.miaomoe.blessing.protocol.message.TitleTime
import net.miaomoe.blessing.protocol.packet.common.PacketKeepAlive
import net.miaomoe.blessing.protocol.packet.play.PacketTabListHeader
import net.miaomoe.blessing.protocol.packet.status.PacketStatusRequest
import net.miaomoe.blessing.protocol.util.ComponentUtil.toComponent
import net.miaomoe.blessing.protocol.util.ComponentUtil.toMixedComponent
import net.miaomoe.blessing.protocol.version.Version
import net.miaomoe.limbo.LimboConfig
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger

class ConnectHandler(
    val fallback: FallbackHandler,
    val config: LimboConfig,
    private val logger: Logger? = null
) : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (msg is PacketStatusRequest) log("$fallback has pinged")
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
        if (msg is PacketKeepAlive) {
            log("$fallback has joined")
            val message = config.message
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
        super.write(ctx, msg, promise)
    }

    private fun log(message: String) {
        logger?.log(Level.INFO, message)
        fallback.channel.pipeline().remove("limbo-connect-handler")
    }

}