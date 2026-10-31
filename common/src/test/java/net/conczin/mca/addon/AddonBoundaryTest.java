package net.conczin.mca.addon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An addon must be able to leave MCA's jar: nothing outside it may use its classes, except the list of built-in
 * addons and the addon's own mixins (mixin/addon/&lt;id&gt;). See docs/planning/addons.md.
 */
class AddonBoundaryTest {
    private static final Path SOURCES = Path.of("src/main/java/net/conczin/mca");

    @Test
    void noClassOutsideAnAddonUsesIt() throws IOException {
        List<String> addons = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(SOURCES.resolve("addon"))) {
            dirs.filter(Files::isDirectory).forEach(dir -> addons.add(dir.getFileName().toString()));
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = SOURCES.relativize(file).toString().replace('\\', '/');
                String text = Files.readString(file);
                for (String addon : addons) {
                    boolean inside = rel.startsWith("addon/" + addon + "/") || rel.startsWith("mixin/addon/" + addon + "/")
                                     || rel.equals("addon/BuiltInAddons.java");
                    if (!inside && text.contains("net.conczin.mca.addon." + addon + ".")) {
                        offenders.add(rel + " uses addon '" + addon + "'");
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Classes outside an addon use it:\n" + String.join("\n", offenders));
    }
}
