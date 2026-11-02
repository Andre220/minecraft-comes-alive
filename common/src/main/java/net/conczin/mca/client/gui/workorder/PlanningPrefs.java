package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.MCA;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Choices made in the planning screen that should outlive it, like whether the help lines are shown. Kept in
 * {@code config/mca_planning.properties}.
 */
final class PlanningPrefs {
    private static final Properties PROPERTIES = new Properties();
    private static boolean loaded;

    private PlanningPrefs() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("mca_planning.properties");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                PROPERTIES.load(reader);
            } catch (IOException e) {
                MCA.LOGGER.warn("Could not read {}", file, e);
            }
        }
    }

    static boolean getBoolean(String key, boolean fallback) {
        load();
        String value = PROPERTIES.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    static void setBoolean(String key, boolean value) {
        load();
        PROPERTIES.setProperty(key, Boolean.toString(value));
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                PROPERTIES.store(writer, "MCA planning screen");
            }
        } catch (IOException e) {
            MCA.LOGGER.warn("Could not save {}", file, e);
        }
    }
}
