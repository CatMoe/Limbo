package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.blessing.fallback.handler.FallbackHandler

class FallbackDisconnectEvent(val fallback: FallbackHandler) : BlessingEvent