package au.com.addstar.ccm;

import au.com.addstar.ccm.api.ChatControlMirrorAPI;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "chatcontrolmirror",
    name = "ChatControlMirror",
    version = BuildConstants.VERSION,
    description = "Velocity API and plugin message channel for sending messages on ChatControl channels",
    dependencies = {@com.velocitypowered.api.plugin.Dependency(id = "velocitycontrol", optional = false)}
)
public class ChatControlMirror {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ChatControlMirrorAPI api;

    @Inject
    public ChatControlMirror(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            ChannelSenderImpl sender = new ChannelSenderImpl(logger);
            this.api = sender;

            proxy.getChannelRegistrar().register(PluginMessageListener.CHANNEL);
            proxy.getEventManager().register(this, new PluginMessageListener(sender, logger));

            CommandMeta sendMeta = proxy.getCommandManager().metaBuilder("ccmsend")
                    .plugin(this)
                    .build();
            proxy.getCommandManager().register(sendMeta, new CcmSendCommand(sender, logger));

            CommandMeta debugMeta = proxy.getCommandManager().metaBuilder("ccmdebug")
                    .plugin(this)
                    .build();
            proxy.getCommandManager().register(debugMeta, new CcmDebugCommand(logger));

            logger.info("ChatControlMirror enabled. API and plugin message channel chatcontrolmirror:send are available.");
        } catch (Throwable t) {
            logger.error("ChatControlMirror failed to initialize. Ensure VelocityControl is installed.", t);
            this.api = null;
        }
    }

    /**
     * Returns the API for sending messages on ChatControl channels.
     * Other plugins can obtain this via the plugin container, e.g.:
     * <pre>
     * proxy.getPluginManager().getPlugin("chatcontrolmirror")
     *     .flatMap(container -> container.getInstance())
     *     .filter(ChatControlMirror.class::isInstance)
     *     .map(ChatControlMirror.class::cast)
     *     .map(ChatControlMirror::getApi)
     * </pre>
     *
     * @return the API instance, or null if the plugin failed to initialize (e.g. VelocityControl not present)
     */
    public ChatControlMirrorAPI getApi() {
        return api;
    }
}
