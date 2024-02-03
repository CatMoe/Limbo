package net.miaomoe.limbo.fallback

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.protocol.packet.handshake.PacketHandshake
import net.miaomoe.blessing.protocol.registry.State
import net.miaomoe.blessing.protocol.util.ComponentUtil.toMixedComponent
import java.net.InetSocketAddress


class ForwardHandler(
    private val fallback: FallbackHandler,
    private val mode: ForwardMode,
    private val key: Any? = null
) : ChannelInboundHandlerAdapter() {

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        when (msg) {
            is PacketHandshake -> {
                if (msg.nextState == State.LOGIN) when (mode) {
                    ForwardMode.LEGACY -> handleLegacy(msg)
                    ForwardMode.GUARD -> handleGuard(msg)
                    ForwardMode.NONE -> msg.host.let { host ->
                        if (host.length !in 4..<254 || host.split("\u0000").filter { it.isNotBlank() }.size > 3)
                            fallback.disconnect(invalidLegacyKick)
                    }
                    ForwardMode.MODERN -> {}
                }
            }
        }
        super.channelRead(ctx, msg)
    }

    private fun handleGuard(handshake: PacketHandshake) {
        val split = handshake.host.split("\u0000").filter { it.isNotBlank() }
        if (split.size != 4) {
            fallback.disconnect(guardUnknownKick)
            return
        }
        val jsonArray = try {
            JsonParser.parseString(split[3]).asJsonArray
        } catch (exception: JsonParseException) {
            fallback.disconnect(guardFailedKick)
            return
        }
        var token: String? = null
        for (obj in jsonArray) {
            if (!obj.isJsonObject) continue
            val prop = obj.asJsonObject
            @Suppress("SpellCheckingInspection")
            if (prop["name"].asString == "bungeeguard-token") {
                token = prop["value"].asString
                break
            }
        }
        if (token == null) fallback.disconnect(guardUnknownKick) else {
            val result = when (key) {
                is String -> key == token
                is List<*> -> key.contains(token)
                else -> null
            }
            if (result != true) {
                fallback.disconnect(guardFailedKick)
                return
            }
            fallback.address = InetSocketAddress(split[1], fallback.address.port)
            fallback.destination = InetSocketAddress.createUnresolved(split[0], handshake.port)
            // TODO process uuid?
        }
    }

    private fun handleLegacy(handshake: PacketHandshake) {
        val split = handshake.host.split("\u0000").filter { it.isNotBlank() }
        if (split.size >= 3) {
            fallback.address = InetSocketAddress(split[1], fallback.address.port)
            fallback.destination = InetSocketAddress.createUnresolved(split[0], handshake.port)
            // TODO process uuid?
        } else {
            fallback.disconnect(legacyForwardKick)
        }
    }

    companion object {
        private val legacyForwardKick = "If you wish to use IP forwarding, please enable it in your BungeeCord config as well!".toMixedComponent()
        // Use the original kick message.
        // Even though know it's not possible (Because Limbo doesn't have spigot.yml) :P
        private val invalidLegacyKick = "Unknown data in login hostname, did you forget to enable BungeeCord in spigot.yml?".toMixedComponent()
        private val guardUnknownKick = "<red>Unable to authenticate - no data was forwarded by the proxy.".toMixedComponent()
        private val guardFailedKick = "<red>Unable to authenticate.".toMixedComponent()
    }

    enum class ForwardMode {
        NONE,
        LEGACY,
        GUARD,
        MODERN
    }

}