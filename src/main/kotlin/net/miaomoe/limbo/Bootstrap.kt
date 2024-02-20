package net.miaomoe.limbo

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import net.miaomoe.blessing.config.type.ConfigType
import net.miaomoe.blessing.config.util.SimpleConfigUtil
import net.miaomoe.blessing.event.EventManager
import net.miaomoe.blessing.event.adapter.ConsumerListenerAdapter
import net.miaomoe.blessing.event.info.ListenerInfo
import net.miaomoe.blessing.fallback.config.FallbackSettings
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.fallback.handler.FallbackInitializer
import net.miaomoe.blessing.fallback.handler.exception.ExceptionHandler
import net.miaomoe.limbo.LimboConfig.ListenerConfig
import net.miaomoe.limbo.event.ConfigReloadedEvent
import net.miaomoe.limbo.event.ConsoleInputEvent
import net.miaomoe.limbo.event.ListenerAddEvent
import net.miaomoe.limbo.event.ListenerRemoveEvent
import net.miaomoe.limbo.fallback.ConnectHandler
import net.miaomoe.limbo.fallback.ForwardHandler
import net.miaomoe.limbo.fallback.TrafficHandler
import net.miaomoe.limbo.motd.MotdHandler
import net.miaomoe.limbo.util.Log4jJulHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.io.IoBuilder
import java.io.File
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess
import java.util.logging.Level as JulLevel
import java.util.logging.Logger as JulLogger

@Suppress("MemberVisibilityCanBePrivate")
class Bootstrap private constructor(var config: ListenerConfig) : ExceptionHandler {

    val listenerKey = ListenerInfo(this, async = false)

    val serverChannel: Class<out ServerSocketChannel>
    val loopGroup: EventLoopGroup

    val motdHandler = MotdHandler(this)

    val settings: FallbackSettings = FallbackSettings.create()
    val initializer: FallbackInitializer

    var forwardKey: Any? = null

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
            .setDebugLogger(if (config.debug) jul.value else null)
            .setTimeout(config.timeout)
            .setInitListener(::initListener)
    }

    private fun initListener(fallback: FallbackHandler, channel: Channel) {
        val pipeline = channel.pipeline()
        pipeline.addLast("limbo-handler", ConnectHandler(this, fallback))
        pipeline.addFirst("limbo-traffic", TrafficHandler)
        pipeline.addAfter(FallbackInitializer.HANDLER, "limbo-forward", ForwardHandler(fallback, config.forwardMode, forwardKey))
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
                    EventManager.call(ListenerRemoveEvent(this.config, this))
                    this.close()
                    EventManager.unregister(ConfigReloadedEvent::class, this.listenerKey)
                    return@ConsumerListenerAdapter
                }
                new.bootstrap = this
                this.config = new
                reloadFallback()
                motdHandler.reload()
                initializer.refreshCache()
                forwardKey = when (config.forwardMode) {
                    ForwardHandler.ForwardMode.GUARD -> {
                        val key = config.forwardKey.split("|")
                        if (key.size == 1) key[0] else key
                    }
                    else -> null
                }
                EventManager.call(ListenerAddEvent(this.config, this))
            })
    }

    private var bindFuture: ChannelFuture? = null

    fun bind() {
        val address = InetSocketAddress(config.bindAddress, config.port)
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
        ctx.channel().close()
        if (!config.debug) {
            CompletableFuture.runAsync {
                val fallback = ctx.channel().pipeline()[FallbackInitializer.HANDLER] as FallbackInitializer
                log("$fallback - exception caught", exception)
            }
        }
    }

    companion object {

        @JvmStatic
        val logger: Logger = LogManager.getLogger(Bootstrap::class.java)

        fun reload() {
            logger.log(Level.INFO, "Reloading config..")
            val config = LimboConfig.INSTANCE
            SimpleConfigUtil.saveAndRead(File("config.conf"), config, ConfigType.HOCON)
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
                val bootstrap = Bootstrap(listener)
                listener.bootstrap=bootstrap
                val name = "${listener.bindAddress}:${listener.port} for ${listener.name}"
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
                            logger.log(Level.INFO, "Command executed: ${event.input}")
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
                                else -> logger.log(Level.INFO, "Unknown command. available commands: [end/stop, reload, status]")
                            }
                        }
                    }
                }}
            }
            thread.name = "CommandThread"
            thread.isDaemon=true
            thread.start()
            thread.join()
        }

        @JvmStatic
        val jul = lazy {
            val redirect = LogManager.getRootLogger()
            System.setOut(IoBuilder.forLogger(redirect).setLevel(Level.INFO).buildPrintStream())
            System.setErr(IoBuilder.forLogger(redirect).setLevel(Level.ERROR).buildPrintStream())
            val root = JulLogger.getLogger("")
            root.useParentHandlers=false
            root.handlers.forEach(root::removeHandler)
            root.level = JulLevel.ALL
            root.addHandler(Log4jJulHandler())
            return@lazy JulLogger.getLogger(Log4jJulHandler::class.qualifiedName)
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
            SimpleConfigUtil.saveAndRead(File("config.conf"), config, ConfigType.HOCON)
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