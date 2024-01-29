package net.miaomoe.limbo.event

import net.miaomoe.blessing.event.event.BlessingEvent
import net.miaomoe.blessing.event.event.Cancellable

data class ConsoleInputEvent(
    val input: String,
    override var isCancelled: Boolean = false
) : BlessingEvent, Cancellable