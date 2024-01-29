package net.miaomoe.limbo

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlinx.coroutines.flow.MutableStateFlow
import net.miaomoe.blessing.config.ConfigUtil
import net.miaomoe.blessing.event.EventManager
import net.miaomoe.blessing.fallback.config.FallbackSettings
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.fallback.handler.FallbackInitializer
import net.miaomoe.blessing.fallback.handler.exception.ExceptionHandler
import net.miaomoe.blessing.fallback.util.ComponentUtil.toComponent
import net.miaomoe.blessing.nbt.chat.MixedComponent
import net.miaomoe.blessing.protocol.registry.State
import net.miaomoe.blessing.protocol.util.PlayerPosition
import net.miaomoe.blessing.protocol.util.Position
import net.miaomoe.limbo.event.ConsoleInputEvent
import net.miaomoe.limbo.event.FallbackConnectEvent
import net.miaomoe.limbo.event.FallbackDisconnectEvent
import net.miaomoe.limbo.fallback.ConnectLoggerHandler
import net.miaomoe.limbo.fallback.DisconnectHandler
import net.miaomoe.limbo.fallback.KeepAliveScheduler
import net.miaomoe.limbo.fallback.TrafficHandler
import net.miaomoe.limbo.motd.MotdHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

@Suppress("MemberVisibilityCanBePrivate")
class LimboBootstrap private constructor() : ExceptionHandler {

    private val connections = MutableStateFlow(mutableListOf<FallbackHandler>())

    val serverChannel: Class<out ServerSocketChannel>
    val loopGroup: EventLoopGroup

    init {
        if (Epoll.isAvailable()) {
            logger.log(Level.INFO, "Using Epoll for linux.")
            this.serverChannel = EpollServerSocketChannel::class.java
            this.loopGroup = EpollEventLoopGroup()
        } else {
            logger.log(Level.INFO, "Using Java NIO")
            this.serverChannel = NioServerSocketChannel::class.java
            this.loopGroup = NioEventLoopGroup()
        }
    }

    val config = LimboConfig().let {
        ConfigUtil.saveAndRead(File("config.conf"), it)
        it
    }
    val motdHandler = MotdHandler(config.motd)
    val initializer = FallbackSettings
        .create()
        .setWorld(config.world)
        .setMotdHandler(motdHandler)
        .setBrand(config.brand)
        .setPlayerName(config.playerName)
        .setDisableFall(config.disableFall)
        .setTimeout(config.timeout)
        .setJoinPosition(config.position.let { PlayerPosition(Position(it.x, it.y, it.z), it.yaw, it.pitch, false) })
        .setDebugLogger(if (config.debug) java.util.logging.Logger.getAnonymousLogger() else null)
        .setInitListener { fallback, channel ->
            val logger = if (config.debug) null else logger
            val pipeline = channel.pipeline()
            val keepAlive = KeepAliveScheduler(config.delay, fallback)
            pipeline
                .addBefore(FallbackInitializer.HANDLER, "limbo-connect-logger", ConnectLoggerHandler(fallback, logger))
                .addAfter(FallbackInitializer.HANDLER, "limbo-keep-alive", keepAlive)
                .addAfter(FallbackInitializer.HANDLER, "limbo-disconnect-handler", DisconnectHandler(logger, fallback) {
                    connections.value.remove(it)
                    keepAlive.cancel()
                    pipeline.remove(KeepAliveScheduler::class.java)
                    EventManager.call(FallbackDisconnectEvent(fallback))
                })
                .addFirst("limbo-traffic-counter", TrafficHandler)
            EventManager.call(FallbackConnectEvent(fallback)) {
                if (it.isCancelled) channel.close()
            }
            connections.value.add(fallback)
        }
        .buildInitializer()

    fun reload() {
        try {
            ConfigUtil.saveAndRead(File("config.conf"), config)
        } catch (exception: Exception) {
            logger.log(Level.WARN, "Failed to reload.", exception)
        }
        motdHandler.reload()
        initializer.settings
            .setWorld(config.world)
            .setBrand(config.brand)
            .setPlayerName(config.playerName)
            .setDisableFall(config.disableFall)
            .setTimeout(config.timeout)
            .setJoinPosition(config.position.let { PlayerPosition(Position(it.x, it.y, it.z), it.yaw, it.pitch, false) })
            .setDebugLogger(if (config.debug) java.util.logging.Logger.getAnonymousLogger() else null)
        initializer.settings.cacheMap.clear()
        initializer.refreshCache()
        logger.log(Level.INFO, "Reloaded.")
    }

    fun newBootstrap(address: InetSocketAddress, group: EventLoopGroup = loopGroup): ChannelFuture {
        return ServerBootstrap()
            .localAddress(address.hostString, address.port)
            .channel(this.serverChannel)
            .group(group)
            .childOption(ChannelOption.IP_TOS, 0x18)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(initializer)
            .bind()
    }

    private fun commandListener() {
        val thread = Thread {
            logger.log(Level.INFO, "Bootstrap finished")
            val scanner = Scanner(System.`in`)
            while (true) {
                val line = try { scanner.nextLine() } catch (_: Exception) { null }
                line?.let { EventManager.call(ConsoleInputEvent(it)) { event ->
                    if (event.isCancelled) return@call
                    when (event.input.lowercase().trim()) {
                        "stop", "end" -> {
                            this.close()
                            exitProcess(0)
                        }
                        "status" -> {
                            CompletableFuture.runAsync {
                                val list = this.connections.value.toList()
                                val rx: String
                                val tx: String
                                TrafficHandler.let { traffic ->
                                    rx = traffic.conversionBytesFormat(traffic.rx)
                                    tx = traffic.conversionBytesFormat(traffic.tx)
                                }
                                logger.log(Level.INFO, "OC (Open Connections): ${list.size} | rx $rx | tx $tx")
                                val players = list.filter { handler -> handler.state == State.PLAY }.joinToString { handler -> handler.name ?: "" }.trim()
                                players.takeIf { string -> string.isNotEmpty() }?.let { string -> logger.log(Level.INFO, "Players: $string") }
                            }
                        }
                        "reload" -> reload()
                        else -> logger.log(Level.INFO, "Unknown command. available commands: stop, status, reload")
                    }
                }} ?: exitProcess(0)
            }
        }
        thread.isDaemon=true
        thread.start()
        thread.join()
    }

    val alreadyClosed = AtomicBoolean(false)

    fun close() {
        if (alreadyClosed.get()) return
        val reason = MixedComponent("Server Closing".toComponent())
        for (handler in connections.value) {
            try { handler.disconnect(reason) } catch (_: Exception) { continue }
        }
        loopGroup.shutdownGracefully()
        logger.log(Level.INFO, "Server closed.")
        alreadyClosed.set(true)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, exception: Throwable) {
        ctx.close()
        if (!config.debug) {
            CompletableFuture.runAsync {
                val fallback = ctx.channel().pipeline()[FallbackInitializer.HANDLER] as FallbackInitializer
                logger.log(Level.WARN, "$fallback - exception caught", exception)
            }
        }
    }

    companion object {

        @JvmStatic
        val logger: Logger = LogManager.getLogger(LimboBootstrap::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
            val bootstrap = LimboBootstrap()
            val config = bootstrap.config
            try {
                for (port in config.port) {
                    val address = InetSocketAddress(config.address, port)
                    bootstrap.newBootstrap(address)
                    logger.log(Level.INFO, "Listen on ${address.hostString}:${address.port}")
                }
            } catch (exception: Exception) {
                logger.log(Level.ERROR, "Failed to bind port!", exception)
                exitProcess(-1)
            }
            Runtime.getRuntime().addShutdownHook(Thread(bootstrap::close))
            bootstrap.commandListener()
        }
    }

}