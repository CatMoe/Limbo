package net.miaomoe.limbo.motd

import net.kyori.adventure.text.Component
import net.miaomoe.blessing.fallback.handler.FallbackHandler
import net.miaomoe.blessing.fallback.handler.motd.FallbackMotdHandler
import net.miaomoe.blessing.fallback.handler.motd.MotdInfo
import net.miaomoe.blessing.protocol.util.ComponentUtil.toComponent
import net.miaomoe.blessing.protocol.util.ComponentUtil.toLegacyComponent
import net.miaomoe.blessing.protocol.util.ComponentUtil.toLegacyText
import net.miaomoe.blessing.protocol.version.Version
import net.miaomoe.limbo.LimboBootstrap
import org.apache.logging.log4j.Level
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLEncoder
import javax.imageio.ImageIO

class MotdHandler(
    private val bootstrap: LimboBootstrap
) : FallbackMotdHandler {

    private val config get() = bootstrap.config.motd

    private lateinit var modernDescription: Component
    private lateinit var legacyDescription: Component
    private lateinit var brand: String
    private var sample = config.sample
    private var favicon: MotdInfo.Favicon? = null
    private var online = config.online
    private var max = config.max

    init {
        reload()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun reload() {
        this.modernDescription = config.description.toComponent()
        this.legacyDescription = modernDescription.toLegacyComponent()
        this.brand = config.brand.toComponent().toLegacyText()
        this.sample = config.sample
        this.online = config.online
        this.max = config.max
        this.favicon = parseFavicon(config.icon)
    }

    private fun parseFavicon(value: String): MotdInfo.Favicon? {
        when {
            value.isEmpty() -> bootstrap.log(message = "Favicon set to empty.")
            value.startsWith("[url]") -> {
                val url = URLEncoder.encode(value.removePrefix("[url]"), "utf-8")
                try {
                    bootstrap.log(Level.INFO, "Reading image from $url ...")
                    val favicon = MotdInfo.Favicon(ImageIO.read(URL(url)))
                    bootstrap.log(Level.INFO, "Successfully processed favicon.")
                    return favicon
                } catch (exception: Exception) {
                    bootstrap.log("Failed to download and process favicon. set it with empty.", exception)
                }
            }
            value.startsWith("[file]") -> {
                val file = File(value.removePrefix("[file]"))
                try {
                    if (!file.exists() || !file.isFile) throw FileNotFoundException("File is not exists or it is folder!")
                    val favicon = MotdInfo.Favicon(ImageIO.read(file))
                    bootstrap.log(Level.INFO, "Successfully processed favicon.")
                    return favicon
                } catch (exception: Exception) {
                    bootstrap.log("Failed to get favicon from ${file.absolutePath}", exception)
                }
            }
            value.startsWith("[encoded]") -> MotdInfo.Favicon(value.removePrefix("[encoded]"))
            else -> bootstrap.log(Level.WARN, "Invalid input: $value. Set favicon to empty.")
        }
        return null
    }

    override fun handle(handler: FallbackHandler) =
        MotdInfo(
            MotdInfo.VersionInfo(brand, if (config.showBrand) -1 else handler.version.protocolId),
            if (config.unknown) null else MotdInfo.PlayerInfo(max, online, sample.map { MotdInfo.Sample(it.toComponent()) }),
            if (handler.version.moreOrEqual(Version.V1_16)) modernDescription else legacyDescription, favicon
        )

}