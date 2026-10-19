package au.com.addstar.ccm;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Redis settings from the "redis" section of config.yml.
 */
public record RedisConfig(boolean enabled, String host, int port, String password, String channel) {

    public static final String DEFAULT_CHANNEL = "chatcontrolmirror:send";

    private static final String CONFIG_FILE = "config.yml";

    /**
     * Load the Redis settings, writing the default config.yml to the data directory first if it doesn't exist.
     */
    public static RedisConfig load(Path dataDirectory) throws IOException {
        Path file = dataDirectory.resolve(CONFIG_FILE);
        if (Files.notExists(file)) {
            Files.createDirectories(dataDirectory);
            try (InputStream in = RedisConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (in == null) {
                    throw new IOException("Default " + CONFIG_FILE + " missing from plugin jar");
                }
                Files.copy(in, file);
            }
        }

        CommentedConfigurationNode root;
        try {
            root = YamlConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException e) {
            throw new IOException("Failed to read " + file, e);
        }

        CommentedConfigurationNode redis = root.node("redis");
        return new RedisConfig(
                redis.node("enabled").getBoolean(false),
                redis.node("host").getString("localhost"),
                redis.node("port").getInt(6379),
                redis.node("password").getString(""),
                redis.node("channel").getString(DEFAULT_CHANNEL));
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
}
