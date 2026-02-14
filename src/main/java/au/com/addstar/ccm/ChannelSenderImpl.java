package au.com.addstar.ccm;

import au.com.addstar.ccm.api.ChatControlMirrorAPI;
import au.com.addstar.ccm.api.MessageFormat;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.proxy.ProxyConstants;
import org.mineacademy.chatcontrol.proxy.Redis;
import org.mineacademy.chatcontrol.velocity.lib.fo.CommonCore;
import org.mineacademy.chatcontrol.velocity.lib.fo.model.SimpleComponent;
import org.mineacademy.chatcontrol.velocity.lib.fo.platform.FoundationServer;
import org.mineacademy.chatcontrol.velocity.lib.fo.platform.Platform;
import org.mineacademy.chatcontrol.velocity.lib.fo.proxy.message.OutgoingMessage;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.UUID;

/**
 * Sends a message on a ChatControl channel by building the CHANNEL proxy packet
 * and forwarding it to local servers and via Redis.
 */
public final class ChannelSenderImpl implements ChatControlMirrorAPI {

    private static final String SENDER_NAME = "Server";
    private static final UUID PROXY_ORIGIN_UUID = new UUID(0L, 0L);
    private static final String SERVER_NAME_FOR_PROXY = "proxy";
    private static final int MAX_PAYLOAD_SIZE = 32_000;
    private static final String CONSOLE_FORMAT_TEMPLATE = "&7[&f{channel}&7] &f{message}";

    private final Logger logger;

    public ChannelSenderImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void sendChannelMessage(String channelName, String message, MessageFormat format) {
        if (channelName == null || channelName.isEmpty()) {
            logger.warn("sendChannelMessage: channel name is null or empty, skipping");
            return;
        }
        if (message == null) {
            logger.warn("sendChannelMessage: message is null, skipping");
            return;
        }
        if (Debug.isEnabled()) {
            logger.info("[debug] sendChannelMessage: channel='{}' format={} message='{}'", channelName, format, message);
        }
        try {
            SimpleComponent formattedMessage = toSimpleComponent(message, format);
            String consoleMessage = formattedMessage.toLegacyAmpersand();
            String consoleFormat = CONSOLE_FORMAT_TEMPLATE
                    .replace("{channel}", channelName)
                    .replace("{message}", consoleMessage);

            OutgoingMessage outgoing = new OutgoingMessage(ChatControlProxyMessage.CHANNEL);
            outgoing.writeString(channelName);
            outgoing.writeString(SENDER_NAME);
            outgoing.writeUUID(PROXY_ORIGIN_UUID);
            outgoing.writeSimpleComponent(formattedMessage);
            outgoing.writeString(consoleFormat);
            outgoing.writeBoolean(false);
            outgoing.writeBoolean(false);
            outgoing.writeBoolean(false);

            byte[] data = outgoing.toByteArray(CommonCore.ZERO_UUID, SERVER_NAME_FOR_PROXY);
            if (data.length >= MAX_PAYLOAD_SIZE) {
                logger.warn("sendChannelMessage: payload too large ({} bytes), not sending", data.length);
                return;
            }

            Collection<FoundationServer> servers = Platform.getServers();
            for (FoundationServer server : servers) {
                server.sendData(ProxyConstants.BUNGEECORD_CHANNEL, data);
            }
            if (Redis.isEnabled()) {
                Redis.sendDataToOtherServers(PROXY_ORIGIN_UUID, ProxyConstants.CHATCONTROL_CHANNEL, data);
            }
        } catch (Throwable t) {
            logger.error("sendChannelMessage failed for channel '{}'", channelName, t);
        }
    }

    private static SimpleComponent toSimpleComponent(String message, MessageFormat format) {
        switch (format) {
            case LEGACY:
                return SimpleComponent.fromAmpersand(message);
            case MINIMESSAGE:
                net.kyori.adventure.text.Component adventure = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message);
                return SimpleComponent.fromAdventure(adventure);
            default:
                return SimpleComponent.fromAmpersand(message);
        }
    }
}
