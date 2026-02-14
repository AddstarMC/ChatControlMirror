# ChatControlMirror

Velocity plugin that provides an API and a plugin message channel for sending messages on ChatControl channels. Requires [VelocityControl](https://builtbybit.com/resources/43226/) to be installed.

## Features

- **Programmatic API**: Other Velocity plugins can send a message on any ChatControl channel.
- **Plugin message channel**: Backend servers (e.g. Bukkit) can send a JSON payload on the `chatcontrolmirror:send` channel to forward a message to a channel.

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

## Commands

- **`/ccmsend <channel> <type> <message...>`** – Test command to send a message on a channel. `type` is `LEGACY` or `MINIMESSAGE`; everything after the second argument is the message (spaces allowed). Permission: `chatcontrolmirror.send`. Logs to the plugin logger.
- **`/ccmdebug`** – Toggle debug mode (in-memory only, resets on restart). When enabled, logs received plugin messages and API send calls. Permission: `chatcontrolmirror.debug`.

## Build

Requires VelocityControl for compilation. If the Maven dependency fails to resolve (e.g. missing parent POM), place `chatcontrol-velocity-2.4.4.jar` in the `lib/` directory and run:

```bash
./gradlew build
```

## License

See repository.
