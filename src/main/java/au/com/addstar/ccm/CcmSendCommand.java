package au.com.addstar.ccm;

import au.com.addstar.ccm.api.ChatControlMirrorAPI;
import au.com.addstar.ccm.api.MessageFormat;
import com.velocitypowered.api.command.SimpleCommand;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Test command: /ccmsend &lt;channel&gt; &lt;type&gt; &lt;message...&gt;
 * Sends a message on the given ChatControl channel. Message is everything after type (allows spaces).
 */
public final class CcmSendCommand implements SimpleCommand {

    public static final String PERMISSION = "chatcontrolmirror.send";

    private final ChatControlMirrorAPI api;
    private final Logger logger;

    public CcmSendCommand(ChatControlMirrorAPI api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (api == null) {
            logger.warn("ccmsend: API not available (VelocityControl may not be loaded)");
            invocation.source().sendMessage(net.kyori.adventure.text.Component.text("ChatControlMirror API is not available."));
            return;
        }
        if (args.length < 3) {
            invocation.source().sendMessage(net.kyori.adventure.text.Component.text("Usage: /ccmsend <channel> <LEGACY|MINIMESSAGE> <message...>"));
            return;
        }
        String channel = args[0];
        String typeStr = args[1];
        String message = Arrays.stream(args, 2, args.length).collect(Collectors.joining(" "));

        MessageFormat format;
        try {
            format = MessageFormat.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            invocation.source().sendMessage(net.kyori.adventure.text.Component.text("Type must be LEGACY or MINIMESSAGE."));
            return;
        }

        logger.info("ccmsend: sending to channel '{}' type {} message='{}'", channel, format, message);
        api.sendChannelMessage(channel, message, format);
        //logger.info("ccmsend: sent successfully");
        //invocation.source().sendMessage(net.kyori.adventure.text.Component.text("Sent to channel " + channel + "."));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
