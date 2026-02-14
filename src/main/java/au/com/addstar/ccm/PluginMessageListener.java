package au.com.addstar.ccm;

import au.com.addstar.ccm.api.MessageFormat;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Listens for plugin messages on chatcontrolmirror:send.
 * Payload is a byte array with three length-prefixed UTF strings (Java writeUTF/readUTF style):
 * channel, type, message.
 */
public final class PluginMessageListener {

    public static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("chatcontrolmirror", "send");

    private final ChannelSenderImpl sender;
    private final Logger logger;

    public PluginMessageListener(ChannelSenderImpl sender, Logger logger) {
        this.sender = sender;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.getId().equals(event.getIdentifier().getId())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Only accept messages from backend servers, not from players
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        byte[] data = event.getData();
        if (data == null || data.length == 0) {
            logger.warn("chatcontrolmirror:send: empty payload, ignoring");
            return;
        }

        String channel;
        String typeStr;
        String message;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            channel = in.readUTF();
            typeStr = in.readUTF();
            message = in.readUTF();
        } catch (IOException e) {
            logger.warn("chatcontrolmirror:send: failed to read three UTF strings from payload", e);
            return;
        }

        if (channel == null || channel.isEmpty()) {
            logger.warn("chatcontrolmirror:send: channel is null or empty, ignoring");
            return;
        }
        if (message == null) {
            logger.warn("chatcontrolmirror:send: message is null, ignoring");
            return;
        }

        MessageFormat format;
        try {
            format = MessageFormat.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            format = MessageFormat.LEGACY;
        }

        if (Debug.isEnabled()) {
            logger.info("[debug] plugin message received: channel='{}' type='{}' message='{}'", channel, typeStr, message);
        }

        try {
            sender.sendChannelMessage(channel, message, format);
        } catch (Exception e) {
            logger.error("chatcontrolmirror:send: failed to send channel message", e);
        }
    }
}
