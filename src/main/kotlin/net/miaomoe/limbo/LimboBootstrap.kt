package net.miaomoe.limbo

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import net.miaomoe.blessing.config.ConfigUtil
import net.miaomoe.blessing.event.EventManager
import net.miaomoe.blessing.event.adapter.ConsumerListenerAdapter
import net.miaomoe.blessing.event.info.ListenerInfo
import net.miaomoe.blessing.fallback.config.FallbackSettings
import net.miaomoe.blessing.fallback.handler.FallbackInitializer
import net.miaomoe.blessing.fallback.handler.exception.ExceptionHandler
import net.miaomoe.limbo.LimboConfig.ListenerConfig
import net.miaomoe.limbo.event.ConfigReloadedEvent
import net.miaomoe.limbo.event.ConsoleInputEvent
import net.miaomoe.limbo.fallback.ConnectHandler
import net.miaomoe.limbo.fallback.TrafficHandler
import net.miaomoe.limbo.motd.MotdHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

@Suppress("MemberVisibilityCanBePrivate")
@Sharable
class LimboBootstrap private constructor(var config: ListenerConfig) : ExceptionHandler {

    val listenerKey = ListenerInfo(this, async = false)

    val serverChannel: Class<out ServerSocketChannel>
    val loopGroup: EventLoopGroup

    val motdHandler = MotdHandler(this)

    val settings: FallbackSettings = FallbackSettings.create()
    val initializer: FallbackInitializer

    fun reloadFallback() {
        settings
            .setAliveScheduler(true)
            .setAliveDelay(config.delay)
            .setWorld(config.world)
            .setBrand(config.brand)
            .setPlayerName(config.playerName)
            .setSpawnPosition(config.position.toPlayerPosition().position)
            .setJoinPosition(config.position.toPlayerPosition())
            .setExceptionHandler(this)
            .setMotdHandler(motdHandler)
            .setDisableFall(config.disableFall)
            .setDebugLogger(if (config.debug) java.util.logging.Logger.getAnonymousLogger() else null)
            .setTimeout(config.timeout)
            .setInitListener { fallback, channel ->
                val pipeline = channel.pipeline()
                pipeline.addLast("limbo-handler", ConnectHandler(this, fallback))
                pipeline.addFirst("limbo-traffic", TrafficHandler)
            }
    }

    init {
        reloadFallback()
        initializer = settings.buildInitializer()
        if (Epoll.isAvailable()) {
            this.serverChannel = EpollServerSocketChannel::class.java
            this.loopGroup = EpollEventLoopGroup()
        } else {
            this.serverChannel = NioServerSocketChannel::class.java
            this.loopGroup = NioEventLoopGroup()
        }
        EventManager.register(
            ConfigReloadedEvent::class,
            this.listenerKey,
            ConsumerListenerAdapter<ConfigReloadedEvent> { event ->
                val original = this.config
                val new = event.config.listeners
                    .firstOrNull { it.name == original.name && it.bootstrap == null }
                if (new == null) {
                    log(message = "This listener appears to have been removed from the config.")
                    this.close()
                    EventManager.unregister(ConfigReloadedEvent::class, this.listenerKey)
                    return@ConsumerListenerAdapter
                }
                new.bootstrap = this
                this.config = new
                reloadFallback()
                motdHandler.reload()
                initializer.refreshCache()
            })
    }

    private var bindFuture: ChannelFuture? = null

    private fun bind() {
        val address = InetSocketAddress(config.address, config.port)
        bindFuture = ServerBootstrap()
            .localAddress(address.hostString, address.port)
            .childOption(ChannelOption.IP_TOS, 0x18)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .channel(serverChannel)
            .group(loopGroup)
            .childHandler(initializer)
            .bind()
            .sync()
    }

    fun close() {
        log(message = "Closing listener")
        this.loopGroup.shutdownGracefully()
        bindFuture?.channel()?.closeFuture()?.sync()
        log(message = "Closed completed.")
    }

    fun log(level: Level = Level.INFO, message: String) {
        logger.log(level, "[${config.name}] $message")
    }

    fun log(message: String, throwable: Throwable) {
        logger.log(Level.WARN, "[${config.name}] $message", throwable)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, exception: Throwable) {
        ctx.close()
        if (!config.debug) {
            CompletableFuture.runAsync {
                val fallback = ctx.channel().pipeline()[FallbackInitializer.HANDLER] as FallbackInitializer
                log("$fallback - exception caught", exception)
            }
        }
    }

    companion object {

        @JvmStatic
        val logger: Logger = LogManager.getLogger(LimboBootstrap::class.java)

        fun reload() {
            logger.log(Level.INFO, "Reloading config..")
            val config = LimboConfig.INSTANCE
            ConfigUtil.saveAndRead(File("config.conf"), config)
            val name = mutableListOf<String>()
            for (listener in config.listeners) {
                require(!name.contains(listener.name)) { "Listener name conflict: ${listener.name}" }
                name.add(listener.name)
            }
            EventManager.call(ConfigReloadedEvent(config)) {
                enableListeners(config.listeners.filter { it.bootstrap == null })
            }
            if (config.listeners.isEmpty()) {
                logger.log(Level.WARN, "Listeners list is null!")
                exitProcess(0)
            } else logger.log(Level.INFO, "Reload completed.")
        }

        private fun enableListeners(listeners: List<ListenerConfig>) {
            for (listener in listeners) {
                val bootstrap = LimboBootstrap(listener)
                listener.bootstrap=bootstrap
                val name = "${listener.address}:${listener.port} for ${listener.name}"
                try {
                    bootstrap.bind()
                    logger.log(Level.INFO, "Successfully bind on $name")
                } catch (exception: Exception) {
                    logger.log(Level.WARN, "Failed bound $name", exception)
                }
            }
        }

        private fun commandListener() {
            val thread = Thread {
                logger.log(Level.INFO, "Bootstrap finished")
                Scanner(System.`in`).let { scanner -> while (true) {
                    try { scanner.nextLine() } catch (_: Exception) { null }?.let { line ->
                        EventManager.call(ConsoleInputEvent(line.lowercase().trim())) { event ->
                            if (event.isCancelled) return@call
                            when (event.input) {
                                "stop", "end" -> {
                                    LimboConfig.INSTANCE.listeners.forEach { it.bootstrap?.close() }
                                    exitProcess(0)
                                }
                                "reload" -> reload()
                                "status" -> {
                                    val oc: Int
                                    val rx: String
                                    val tx: String
                                    TrafficHandler.let { traffic ->
                                        oc=traffic.oc
                                        rx=traffic.conversionBytesFormat(traffic.rx)
                                        tx=traffic.conversionBytesFormat(traffic.tx)
                                    }
                                    logger.log(Level.INFO, "Open Connections: $oc | tx: $tx | rx: $rx")
                                }
                            }
                        }
                    }
                }}
            }
            thread.isDaemon=true
            thread.start()
            thread.join()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
            if (Epoll.isAvailable()) {
                logger.log(Level.INFO, "Using Epoll for linux.")
            } else {
                logger.log(Level.INFO, "Using Java NIO")
            }
            logger.log(Level.INFO, "Loading config...")
            val config = LimboConfig.INSTANCE
            ConfigUtil.saveAndRead(File("config.conf"), config)
            val listeners = config.listeners
            if (listeners.isEmpty()) {
                logger.log(Level.WARN, "Listeners list is empty!")
                exitProcess(0)
            }
            val name = mutableListOf<String>()
            for (listener in listeners) {
                require(!name.contains(listener.name)) { "Listener name conflict: ${listener.name}" }
                name.add(listener.name)
            }
            enableListeners(listeners)
            commandListener()
        }
    }

}