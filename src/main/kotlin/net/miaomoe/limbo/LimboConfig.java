package net.miaomoe.limbo;

import net.miaomoe.blessing.config.AbstractConfig;
import net.miaomoe.blessing.config.annotation.Description;
import net.miaomoe.blessing.config.annotation.Path;
import net.miaomoe.blessing.nbt.dimension.World;
import net.miaomoe.limbo.fallback.ForwardHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class LimboConfig extends AbstractConfig {

    @Path(path = "bind-address")
    @NotNull
    public String address = "0.0.0.0";

    @Path(path = "bind-ports")
    @Description(description = "can support bind multi port.")
    @NotNull
    public List<Integer> port = Collections.singletonList(25565);

    @Path(path = "forward-mode")
    @Description(description = "Forwarding from proxy.")
    @NotNull
    public ForwardHandler.ForwardMode forwardMode = ForwardHandler.ForwardMode.NONE;

    @Path
    public boolean debug = false;

    @SuppressWarnings("SpellCheckingInspection")
    @Path
    @Description(description = "overworld, nether, the_end")
    @NotNull
    public World world = World.OVERWORLD;

    @Path
    @NotNull
    public String brand = "<light_purple>Blessing</light_purple>";

    @Path(path = "player-name")
    @NotNull
    public String playerName = "Blessing";

    @Path(path = "disable-fall")
    public boolean disableFall = true;

    @Path
    public long timeout = 30000;

    @Path
    @Description(description = "keep-alive delay")
    public long delay = 10000;

    @Path
    @NotNull
    public PositionConfig position = new PositionConfig();

    public static class PositionConfig extends AbstractConfig {

        private PositionConfig() {}

        @Path
        public double x = 7.5;
        @Path
        public double y = 100;
        @Path
        public double z = 7.5;
        @Path
        public float yaw = 180;
        @Path
        public float pitch = 0;
    }

    @Path
    @NotNull
    public MotdConfig motd = new MotdConfig();

    public static class MotdConfig extends AbstractConfig {

        private MotdConfig() {}

        @Path
        public String description = "<light_purple>Blessing powered - Limbo <3";
        @Path
        public String brand = "<light_purple>Blessing Powered";
        @Path
        @Description(description = "set player info to null.")
        public boolean unknown = false;
        @Path
        public int max = 0;
        @Path
        public int online = 0;
        @Path(path = "show-brand")
        public boolean showBrand = false;
        @Path
        @Description(description = {
                "empty to set null. available prefix:",
                "[url] : get png file from url. (example: \"[url]https://example.com\")",
                "[file] : get png file from file. (example: \"[file]server-icon.png\" will read the \"server-icon.png\" file in the directory opened by the shell)",
                "[encoded] : directly encode base64 image. (example: \"[encoded]data:image/png;base64,**content**\")"
        })
        public String icon = "";
        @Path
        public List<String> sample = Collections.singletonList("<light_purple>https://github.com/CatMoe/Limbo");
    }

    @Path
    @NotNull
    public JoinMessageConfig message = new JoinMessageConfig();

    public static class JoinMessageConfig extends AbstractConfig {

        private JoinMessageConfig() {}

        @Path
        @NotNull
        @Description(description = {
                "Write the chat message for player that joining.",
                "Set to empty to disable this feature."
        })
        public List<String> chat = Collections.emptyList();

        @Path
        @NotNull
        @Description(description = {
                "Write the actionbar message for player that joining.",
                "This feature only works on 1.8+",
                "",
                "Set to empty to disable this feature."
        })
        public String actionBar = "";

        @Path
        @NotNull
        @Description(description = {
                "Write the title message for player that joining.",
                "This feature only works on 1.8+",
                "",
                "setting the fadeIn, stay, fadeOut to 0 to disable."
        })
        public TitleConfig title = new TitleConfig();

        public static class TitleConfig extends AbstractConfig {

            private TitleConfig() {}

            @Path
            @NotNull
            public String title = "";
            @Path(path = "sub-title")
            @NotNull
            public String subTitle = "";
            @Path(path = "fade-in")
            public int fadeIn = 0;
            @Path
            public int stay = 0;
            @Path(path = "fade-out")
            public int fadeOut = 0;
        }

        @Path
        @NotNull
        @Description(description = "Configuration about tab header & footer. (Works on 1.8+)")
        public TabConfig tab = new TabConfig();

        public static class TabConfig extends AbstractConfig {

            private TabConfig() {}

            @Path
            @NotNull
            public List<String> header = Collections.emptyList();
            @Path
            @NotNull
            public List<String> footer = Collections.emptyList();
        }

    }

}
