package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.blessing.event.event.Cancellable
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.limbo.LimboBootstrap

data class FallbackConnectEvent(
    val bootstrap: LimboBootstrap,
    val fallback: FallbackHandler,
    override var isCancelled: Boolean = false,
) : BlessingEvent, Cancellable