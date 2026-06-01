package com.cvagrofarmsstore.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * Thin wrapper around {@link Preferences} for persisting the user-chosen
 * database directory across application restarts.
 *
 * <p>Falls back to {@code %APPDATA%/CVGroups/CVAgroFarmsStore/} when the
 * preference node has never been written or the stored path no longer exists.
 */
public final class AppPreferences {

    private static final Logger LOG = LogManager.getLogger(AppPreferences.class);

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(AppPreferences.class);

    private static final String KEY_DB_DIR = "db_directory";

    public static final String DB_FILE_NAME = "CVAgroFarms_DB.xlsx";

    private AppPreferences() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true when a valid DB directory has been saved in preferences. */
    public static boolean isConfigured() {
        String stored = PREFS.get(KEY_DB_DIR, null);
        if (stored == null || stored.isBlank()) return false;
        return Files.isDirectory(Paths.get(stored));
    }

    /**
     * Persists {@code dir} as the active database directory.
     * Creates the directory if it does not yet exist.
     */
    public static void saveDbDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            PREFS.put(KEY_DB_DIR, dir.toAbsolutePath().toString());
            PREFS.flush();
            LOG.info("DB directory saved to preferences: {}", dir.toAbsolutePath());
        } catch (Exception e) {
            LOG.error("Failed to save DB directory preference", e);
        }
    }

    /**
     * Returns the resolved path to {@code CVAgroFarms_DB.xlsx}.
     * Falls back to the AppData sub-folder if no preference is stored.
     */
    public static Path getDbPath() {
        String stored = PREFS.get(KEY_DB_DIR, null);
        if (stored != null && !stored.isBlank()) {
            Path dir = Paths.get(stored);
            if (Files.isDirectory(dir)) {
                return dir.resolve(DB_FILE_NAME);
            }
            LOG.warn("Stored DB directory no longer exists: {} — using fallback", stored);
        }
        return fallbackDir().resolve(DB_FILE_NAME);
    }

    /** Returns the backup directory (sibling {@code backups/} of the DB file). */
    public static Path getBackupDir() {
        return getDbPath().getParent().resolve("backups");
    }

    /** Clears the stored preference (used in tests / reset flows). */
    public static void clear() {
        PREFS.remove(KEY_DB_DIR);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Path fallbackDir() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Paths.get(appData, "CVGroups", "CVAgroFarmsStore")
                : Paths.get(System.getProperty("user.home"), "CVGroups", "CVAgroFarmsStore");
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            LOG.error("Could not create fallback directory: {}", base, e);
        }
        LOG.info("Using fallback DB directory: {}", base.toAbsolutePath());
        return base;
    }
}
