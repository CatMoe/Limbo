package net.miaomoe.limbo;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.miaomoe.blessing.config.annotation.Description;
import net.miaomoe.blessing.config.annotation.ParseAllField;
import net.miaomoe.blessing.config.parser.AbstractConfig;
import net.miaomoe.blessing.nbt.dimension.World;
import net.miaomoe.blessing.protocol.util.PlayerPosition;
import net.miaomoe.blessing.protocol.util.Position;
import net.miaomoe.limbo.fallback.ForwardHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ParseAllField(ignore = "INSTANCE")
public class LimboConfig extends AbstractConfig {

    public static final LimboConfig INSTANCE = new LimboConfig();
    public List<ListenerConfig> listeners = Collections.singletonList(new ListenerConfig());

    private LimboConfig() {}

    @ParseAllField(ignore = "bootstrap")
    public static class ListenerConfig extends AbstractConfig {

        private ListenerConfig() {}
        @Nullable public LimboBootstrap bootstrap = null;
        @Description(description = "The name must be unique.")
        @NotNull public String name = "main";
        @NotNull public String bindAddress = "0.0.0.0";
        public int port = 25565;
        public boolean debug = false;
        @Description(description = {
                "This option will check the information coming in from the proxy (to synchronize the IP address of the incoming connection).",
                "And help Limbo reject the target connection that is not from the proxy.",
                "",
                "All forwards that are deemed invalid will use their original kick message.",
                "(example when you using LEGACY: \"If you wish to use IP forwarding, please enable it in your BungeeCord config as well!\")",
                "",
                "NONE: No forwarding will be processed. Players are allowed to connect directly to Limbo.",
                "LEGACY: Classic BungeeCord ip-forward.",
                "GUARD: Handle the forward from BungeeGuard.",
                "MODERN: (Unsupported now) Handle the forward from Velocity."
        })
        public ForwardHandler.ForwardMode forwardMode = ForwardHandler.ForwardMode.NONE;
        @Description(description = {
                "The key for the MODERN or GUARD forwarding mode.",
                "",
                "GUARD: A string in the form of a key. (You can use \"|\" to separate multiple lines)",
                "MODERN: The path to the \"forwarding.secret\" file. (It can also be called something else)"
        })
        public String forwardKey = "";
        @NotNull
        @SuppressWarnings("SpellCheckingInspection")
        @Description(description = "overworld, nether, the_end")
        public World world = World.OVERWORLD;
        @NotNull public String brand = "<light_purple>Blessing</light_purple>";
        @NotNull public String playerName = "Blessing";
        public boolean disableFall = true;
        public long timeout = 30000;
        @Description(description = "keep-alive delay")
        public long delay = 10000;
        @NotNull public MotdConfig motd = new MotdConfig();

        @ParseAllField
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class MotdConfig extends AbstractConfig {
            public String description = "<light_purple>Blessing powered - Limbo <3";
            public String brand = "<light_purple>Blessing Powered";
            @Description(description = "set player info to null.")
            public boolean unknown = false;
            public int max = 0;
            public int online = 0;
            public boolean showBrand = false;
            @Description(description = {
                    "empty to set null. available prefix:",
                    "[url] : get png file from url. (example: \"[url]https://example.com\")",
                    "[file] : get png file from file. (example: \"[file]server-icon.png\" will read the \"server-icon.png\" file in the directory opened by the shell)",
                    "[encoded] : directly encode base64 image. (example: \"[encoded]data:image/png;base64,**content**\")"
            })
            public String icon = "";
            public List<String> sample = Collections.singletonList("<light_purple>https://github.com/CatMoe/Limbo");
        }

        @NotNull
        public PositionConfig position = new PositionConfig();

        @ParseAllField
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class PositionConfig extends AbstractConfig {
            public double x = 7.5;
            public double y = 100;
            public double z = 7.5;
            public float yaw = 180;
            public float pitch = 0;

            public @NotNull PlayerPosition toPlayerPosition() {
                return new PlayerPosition(new Position(x, y, z), yaw, pitch, false);
            }
        }

        @NotNull public JoinMessageConfig message = new JoinMessageConfig();

        @ParseAllField
        public static class JoinMessageConfig extends AbstractConfig {

            @Description(description = {
                    "Write the chat message for player that joining.",
                    "Set to empty to disable this feature."
            })
            @NotNull public List<String> chat = Collections.emptyList();

            @Description(description = {
                    "Write the actionbar message for player that joining.",
                    "This feature only works on 1.8+",
                    "",
                    "Set to empty to disable this feature."
            })
            @NotNull public String actionBar = "";

            @Description(description = {
                    "Write the title message for player that joining.",
                    "This feature only works on 1.8+",
                    "",
                    "setting the fadeIn, stay, fadeOut to 0 to disable."
            })
            @NotNull public TitleConfig title = new TitleConfig();

            @ParseAllField
            @NoArgsConstructor(access = AccessLevel.PRIVATE)
            public static class TitleConfig extends AbstractConfig {
                @NotNull public String title = "";
                @NotNull public String subTitle = "";
                public int fadeIn = 0;
                public int stay = 0;
                public int fadeOut = 0;
            }

            @Description(description = "Configuration about tab header & footer. (Works on 1.8+)")
            @NotNull public TabConfig tab = new TabConfig();

            @ParseAllField
            @NoArgsConstructor(access = AccessLevel.PRIVATE)
            public static class TabConfig extends AbstractConfig {
                @NotNull public List<String> header = Collections.emptyList();
                @NotNull public List<String> footer = Collections.emptyList();
            }
        }

    }

}
