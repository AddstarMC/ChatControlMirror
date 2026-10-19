# ChatControlMirror

Velocity plugin that provides an API and a plugin message channel for sending messages on ChatControl channels. Requires [VelocityControl](https://builtbybit.com/resources/43226/) to be installed.

## Features

- **Programmatic API**: Other Velocity plugins can send a message on any ChatControl channel.
- **Plugin message channel**: Backend servers (e.g. Bukkit) can send a payload on the `chatcontrolmirror:send` channel to forward a message to a channel.
- **Redis**: Anything that can publish to Redis can send a JSON payload to a channel. Works when the sending backend has no players online.

## Programmatic API

Obtain the API from the plugin container:

```java
proxy.getPluginManager().getPlugin("chatcontrolmirror")
    .flatMap(container -> container.getInstance())
    .filter(ChatControlMirror.class::isInstance)
    .map(ChatControlMirror.class::cast)
    .map(ChatControlMirror::getApi)
    .ifPresent(api -> api.sendChannelMessage("staff", "Hello &aworld", MessageFormat.LEGACY));
```

- **channelName**: Must match a ChatControl channel (e.g. `staff`).
- **message**: The message text. Format depends on the third parameter.
- **format**: `MessageFormat.LEGACY` (e.g. `&a`, `§a`) or `MessageFormat.MINIMESSAGE` (e.g. `<green>text</green>`).

The sender is fixed (e.g. "Server") and bypass flags are false.

## Plugin message (backend servers)

**Channel**: `chatcontrolmirror:send` (Minecraft channel identifier).

**Payload**: A byte array of **three length-prefixed UTF strings** (Java `DataOutputStream.writeUTF` / `DataInputStream.readUTF` style: 2-byte unsigned length in big-endian, then UTF-8 bytes), in order:

1. **channel** – ChatControl channel name  
2. **type** – `"LEGACY"` or `"MINIMESSAGE"`  
3. **message** – Message text (decoded according to `type`)  

Example (Java backend): write the three strings with `DataOutputStream.writeUTF(channel); writeUTF(type); writeUTF(message);` and send the resulting byte array.

## Redis

A plugin message from a backend needs a connected player to travel through, so a backend with no players online can't use it. The Redis input has no such limit: the proxy subscribes to a Redis pub/sub channel and forwards each message it receives.

### Config

`plugins/chatcontrolmirror/config.yml` is created on first start. Redis is off by default.

| Key | Default | Description |
| --- | --- | --- |
| `redis.enabled` | `false` | Turn the Redis input on. |
| `redis.host` | `localhost` | Redis host. |
| `redis.port` | `6379` | Redis port. |
| `redis.password` | `""` | Redis password. Empty means no auth. |
| `redis.channel` | `chatcontrolmirror:send` | Pub/sub channel to subscribe to. |

The config is read at proxy startup. If Redis is unreachable the plugin logs a warning and keeps retrying in the background; the plugin message channel and `/ccmsend` work either way.

### Payload

A JSON object published as a string:

```json
{"channel": "global", "type": "MINIMESSAGE", "message": "<gold>[Comp]</gold> Lobby is open"}
```

- **channel** (required): ChatControl channel name.
- **type**: `LEGACY` or `MINIMESSAGE`. Missing or unknown values are treated as `LEGACY`.
- **message** (required): Message text, decoded according to `type`.

Malformed JSON or a missing field is logged as a warning and the message is dropped. With `/ccmdebug` on, every received message is logged.

### Example

```bash
redis-cli PUBLISH chatcontrolmirror:send '{"channel":"global","type":"MINIMESSAGE","message":"<click:run_command:''/comp''><gold>Join the comp</gold></click>"}'
```

## Commands

- **`/ccmsend <channel> <type> <message...>`** – Test command to send a message on a channel. `type` is `LEGACY` or `MINIMESSAGE`; everything after the second argument is the message (spaces allowed). Permission: `chatcontrolmirror.send`. Logs to the plugin logger.
- **`/ccmdebug`** – Toggle debug mode (in-memory only, resets on restart). When enabled, logs received plugin messages, Redis messages and API send calls. Permission: `chatcontrolmirror.debug`.

## Build

Targets Java 25 and needs a JDK 25 toolchain. Built against VelocityControl 3.1.13 (`vcVersion` in `gradle.properties`). That version isn't in the Maven repo yet, so place `VelocityControl-3.1.13.jar` in the `lib/` directory and run:

```bash
./gradlew build
```

The plugin jar is `build/libs/ChatControlMirror-<version>.jar`. It shades and relocates the Jedis Redis client.

## License

See repository.
