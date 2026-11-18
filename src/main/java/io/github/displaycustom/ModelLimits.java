package io.github.displaycustom;

import org.bukkit.configuration.file.FileConfiguration;

/** Runtime quantity limits, loaded from config.yml; -1 disables the limit. */
public final class ModelLimits {
    public static final int UNLIMITED = -1;
    public static final int DEFAULT_MAX_PARTS = 1024;
    public static final int DEFAULT_MAX_ANIMATIONS = 32;
    public static final int DEFAULT_MAX_SOUNDS = 128;
    public static final int DEFAULT_MAX_FRAMES = 128;
    public static final int DEFAULT_MAX_INSTANCES = 32;
    public static final int DEFAULT_MAX_TOTAL_ENTITIES = -1;

    public static volatile int maxParts = DEFAULT_MAX_PARTS;
    public static volatile int maxAnimations = DEFAULT_MAX_ANIMATIONS;
    public static volatile int maxSounds = DEFAULT_MAX_SOUNDS;
    public static volatile int maxFrames = DEFAULT_MAX_FRAMES;
    public static volatile int maxInstances = DEFAULT_MAX_INSTANCES;
    public static volatile int maxTotalEntities = DEFAULT_MAX_TOTAL_ENTITIES;

    private ModelLimits() {}

    /** Reads limits.<key> from config.yml; invalid or missing keys keep defaults. -1 means unlimited. */
    public static void reloadFrom(FileConfiguration config) {
        maxParts = read(config, "limits.max-parts", DEFAULT_MAX_PARTS);
        maxAnimations = read(config, "limits.max-animations", DEFAULT_MAX_ANIMATIONS);
        maxSounds = read(config, "limits.max-sounds-per-animation", DEFAULT_MAX_SOUNDS);
        maxFrames = read(config, "limits.max-frames-per-track", DEFAULT_MAX_FRAMES);
        maxInstances = read(config, "limits.max-instances", DEFAULT_MAX_INSTANCES);
        maxTotalEntities = read(config, "limits.max-total-entities", DEFAULT_MAX_TOTAL_ENTITIES);
    }

    private static int read(FileConfiguration config, String path, int fallback) {
        if (!config.contains(path)) return fallback;
        int value = config.getInt(path, fallback);
        return (value == UNLIMITED || value >= 1) ? value : fallback;
    }

    public static boolean exceeds(int value, int limit) {
        return limit != UNLIMITED && value > limit;
    }

    /** Global keyframe budget: 32 × per-track limit keeps the legacy 4096 default; unlimited stays unlimited. */
    public static int maxTotalFrames() {
        return maxFrames == UNLIMITED || maxAnimations == UNLIMITED ? UNLIMITED : maxAnimations * maxFrames;
    }

    public static String describe(String key, int limit) {
        return limit == UNLIMITED ? "config.yml: " + key + " = -1" : Integer.toString(limit);
    }

    /** Test helper: restores documented defaults without needing Bukkit's FileConfiguration. */
    public static void reset() {
        maxParts = DEFAULT_MAX_PARTS;
        maxAnimations = DEFAULT_MAX_ANIMATIONS;
        maxSounds = DEFAULT_MAX_SOUNDS;
        maxFrames = DEFAULT_MAX_FRAMES;
        maxInstances = DEFAULT_MAX_INSTANCES;
        maxTotalEntities = DEFAULT_MAX_TOTAL_ENTITIES;
    }
}
