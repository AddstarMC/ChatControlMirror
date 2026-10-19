package au.com.addstar.ccm;

import au.com.addstar.ccm.api.MessageFormat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.TimeUnit;

/**
 * Subscribes to a Redis pub/sub channel and forwards each message to a ChatControl channel.
 * Payload is a JSON object: {"channel": "...", "type": "LEGACY|MINIMESSAGE", "message": "..."}.
 * <p>
 * Jedis has no automatic reconnect for pub/sub, so a dedicated thread holds the subscription
 * and reconnects with backoff whenever it drops. A periodic PING detects half-open connections.
 */
public final class RedisSubscriber {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final long MIN_BACKOFF_MILLIS = 1_000;
    private static final long MAX_BACKOFF_MILLIS = 30_000;
    private static final long PING_INTERVAL_SECONDS = 30;
    private static final long PONG_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(PING_INTERVAL_SECONDS * 3);

    private final Object plugin;
    private final ProxyServer proxy;
    private final ChannelSenderImpl sender;
    private final Logger logger;
    private final RedisConfig config;

    private volatile boolean running;
    private volatile boolean subscribed;
    private volatile Jedis jedis;
    private volatile long lastActivityNanos;
    private Thread thread;
    private ScheduledTask pingTask;

    private final JedisPubSub pubSub = new JedisPubSub() {
        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            subscribed = true;
            lastActivityNanos = System.nanoTime();
            logger.info("Redis: subscribed to '{}' on {}:{}", channel, config.host(), config.port());
        }

        @Override
        public void onMessage(String channel, String message) {
            lastActivityNanos = System.nanoTime();
            try {
                handleMessage(message);
            } catch (Throwable t) {
                // Never let an exception escape: it would tear down the subscription.
                logger.warn("Redis: failed to handle message '{}'", message, t);
            }
        }

        @Override
        public void onPong(String pattern) {
            lastActivityNanos = System.nanoTime();
        }
    };

    public RedisSubscriber(Object plugin, ProxyServer proxy, ChannelSenderImpl sender, Logger logger, RedisConfig config) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.sender = sender;
        this.logger = logger;
        this.config = config;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::run, "ChatControlMirror-Redis");
        thread.setDaemon(true);
        thread.start();
        pingTask = proxy.getScheduler().buildTask(plugin, this::ping)
                .delay(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .repeat(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .schedule();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (pingTask != null) {
            pingTask.cancel();
            pingTask = null;
        }
        try {
            if (pubSub.isSubscribed()) {
                pubSub.unsubscribe();
            }
        } catch (Exception ignored) {
            // Connection already gone; closing below unblocks the thread either way.
        }
        closeConnection();
        thread.interrupt();
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        thread = null;
        logger.info("Redis: subscriber stopped");
    }

    private void run() {
        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .timeoutMillis(CONNECT_TIMEOUT_MILLIS)
                .clientName("ChatControlMirror")
                // The plain Jedis client only speaks RESP2; skip the HELLO negotiation it would warn about.
                .autoNegotiateProtocol(false);
        if (config.hasPassword()) {
            clientConfig.password(config.password());
        }
        HostAndPort address = new HostAndPort(config.host(), config.port());

        long backoff = MIN_BACKOFF_MILLIS;
        boolean warned = false;
        while (running) {
            subscribed = false;
            String error = null;
            try (Jedis connection = new Jedis(address, clientConfig.build())) {
                jedis = connection;
                // Blocks until unsubscribed or the connection drops.
                connection.subscribe(pubSub, config.channel());
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                jedis = null;
            }
            if (!running) {
                break;
            }

            if (subscribed) {
                // Was connected, so this is a fresh outage: warn again and retry quickly.
                logger.warn("Redis: connection to {}:{} lost ({}), reconnecting", config.host(), config.port(), error);
                backoff = MIN_BACKOFF_MILLIS;
                warned = true;
            } else if (!warned) {
                logger.warn("Redis: cannot connect to {}:{} ({}). Retrying in the background; "
                        + "the plugin message channel and /ccmsend are unaffected.", config.host(), config.port(), error);
                warned = true;
            } else if (Debug.isEnabled()) {
                logger.info("[debug] Redis: reconnect failed ({}), next attempt in {} ms", error, backoff);
            }

            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MILLIS);
        }
    }

    private void ping() {
        if (!running || !pubSub.isSubscribed()) {
            return;
        }
        if (System.nanoTime() - lastActivityNanos > PONG_TIMEOUT_NANOS) {
            logger.warn("Redis: no response from {}:{} in {} seconds, reconnecting",
                    config.host(), config.port(), TimeUnit.NANOSECONDS.toSeconds(PONG_TIMEOUT_NANOS));
            closeConnection();
            return;
        }
        try {
            pubSub.ping();
        } catch (Exception e) {
            closeConnection();
        }
    }

    private void closeConnection() {
        Jedis connection = jedis;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // Best effort; the subscriber thread handles the resulting error.
            }
        }
    }

    private void handleMessage(String payload) {
        JsonObject json;
        try {
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                logger.warn("Redis: payload is not a JSON object, ignoring: {}", payload);
                return;
            }
            json = element.getAsJsonObject();
        } catch (JsonParseException e) {
            logger.warn("Redis: malformed JSON, ignoring: {}", payload);
            return;
        }

        String channel = getString(json, "channel");
        String typeStr = getString(json, "type");
        String message = getString(json, "message");

        if (channel == null || channel.isEmpty()) {
            logger.warn("Redis: 'channel' is missing or empty, ignoring: {}", payload);
            return;
        }
        if (message == null) {
            logger.warn("Redis: 'message' is missing, ignoring: {}", payload);
            return;
        }

        MessageFormat format;
        try {
            format = MessageFormat.valueOf(typeStr == null ? "" : typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            format = MessageFormat.LEGACY;
        }

        if (Debug.isEnabled()) {
            logger.info("[debug] redis message received: channel='{}' type='{}' message='{}'", channel, typeStr, message);
        }

        // Hand off to the Velocity scheduler so sending never runs on (or blocks) the Redis thread.
        MessageFormat finalFormat = format;
        proxy.getScheduler().buildTask(plugin, () -> sender.sendChannelMessage(channel, message, finalFormat)).schedule();
    }

    private static String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }
}
