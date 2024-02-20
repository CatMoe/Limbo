package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.limbo.Bootstrap

@Suppress("unused")
class FallbackDisconnectEvent(
    val bootstrap: Bootstrap,
    val fallback: FallbackHandler
) : BlessingEvent