package io.github.displaycustom.api;

import java.util.Set;
import org.bukkit.Location;

/**
 * Public service registered with Bukkit's ServicesManager by DisplayCustom.
 * All methods must be called on the server's primary thread.
 */
public interface DisplayCustomService {
    /** Returns a snapshot of loaded model IDs. */
    Set<String> modelIds();

    /** Returns a snapshot of animation names for a loaded model. */
    Set<String> animationNames(String modelId);

    /** Spawns a temporary placed model and returns its instance ID. */
    default String spawn(String modelId, Location location) {
        return spawn(modelId, location, false);
    }

    /**
     * Spawns at the location's coordinates and yaw. Persistent instances are restored after a restart.
     * Throws IllegalArgumentException for an unknown model or invalid location, or if a budget is exceeded.
     */
    String spawn(String modelId, Location location, boolean persistent);

    /** Plays a named clip on a loaded placed instance at 0.05 to 20 times normal speed. */
    void play(String instanceId, String animationName, double speed);

    /** Stops playback on a loaded placed instance and restores its base pose. */
    void stop(String instanceId);

    /** Removes a placed instance or its saved record. Returns false for an unknown ID. */
    boolean remove(String instanceId);
}
