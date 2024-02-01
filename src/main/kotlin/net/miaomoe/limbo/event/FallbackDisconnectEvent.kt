package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.limbo.LimboBootstrap

class FallbackDisconnectEvent(
    val bootstrap: LimboBootstrap,
    val fallback: FallbackHandler
) : BlessingEvent