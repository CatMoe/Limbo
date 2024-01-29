package net.miaomoe.limbo.fallback

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.util.concurrent.ScheduledFuture
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.packet.common.PacketKeepAlive
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class KeepAliveScheduler(
    private val delay: Long,
    private val fallback: FallbackHandler
) : ChannelOutboundHandlerAdapter() {

    private var startSchedule = false
    private var task: ScheduledFuture<*>? = null

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        if (msg is PacketKeepAlive && !startSchedule) schedule(ctx.channel())
        super.write(ctx, msg, promise)
    }

    fun cancel() {
        task?.cancel(true)
        startSchedule=false
    }

    private fun schedule(channel: Channel) {
        val task = channel.eventLoop().scheduleAtFixedRate({
            if (!fallback.markDisconnect) channel.writeAndFlush(PacketKeepAlive(Random.nextInt()))
            else task?.cancel(true)
        }, delay, delay, TimeUnit.MILLISECONDS)
        this.task = task
    }

}