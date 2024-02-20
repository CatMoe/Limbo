package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.limbo.Bootstrap
import net.miaomoe.limbo.LimboConfig.ListenerConfig

class ListenerRemoveEvent(val config: ListenerConfig, val bootstrap: Bootstrap) : BlessingEvent