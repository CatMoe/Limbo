package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.limbo.LimboConfig

class ConfigReloadedEvent(val config: LimboConfig) : BlessingEvent