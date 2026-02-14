package au.com.addstar.ccm;

/**
 * In-memory debug toggle (not persistent across restarts).
 * When enabled, the plugin logs received plugin messages and API send calls.
 */
public final class Debug {

    private static volatile boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        Debug.enabled = enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    private Debug() {}
}
