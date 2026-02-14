package au.com.addstar.ccm.api;

/**
 * API for sending a message on a ChatControl channel.
 * Obtain the implementation via the ChatControlMirror plugin container.
 */
public interface ChatControlMirrorAPI {

    /**
     * Send a message on the given ChatControl channel.
     * The sender is fixed (e.g. "Server") and bypass flags are false.
     *
     * @param channelName ChatControl channel name (e.g. staff)
     * @param message     Message text (format depends on {@code format})
     * @param format      LEGACY (e.g. &a) or MINIMESSAGE (e.g. <green>...</green>)
     */
    void sendChannelMessage(String channelName, String message, MessageFormat format);
}
