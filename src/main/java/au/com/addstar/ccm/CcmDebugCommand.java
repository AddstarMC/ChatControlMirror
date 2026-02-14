package au.com.addstar.ccm;

import com.velocitypowered.api.command.SimpleCommand;
import org.slf4j.Logger;

/**
 * Toggle debug mode (in-memory only, not persistent).
 * When enabled, logs received plugin messages and API send calls.
 */
public final class CcmDebugCommand implements SimpleCommand {

    public static final String PERMISSION = "chatcontrolmirror.debug";

    private final Logger logger;

    public CcmDebugCommand(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        boolean now = Debug.toggle();
        logger.info("ccmdebug: debug mode {}", now ? "enabled" : "disabled");
        invocation.source().sendMessage(
                net.kyori.adventure.text.Component.text("ChatControlMirror debug " + (now ? "enabled" : "disabled") + "."));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
