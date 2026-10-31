package net.conczin.mca.expansion.minds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerAttributesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyProfessionLeansOnAtLeastTwoDifferentAttributes() {
        VillagerAttributes.FITS.forEach((profession, fit) -> {
            assertFalse(fit.others().isEmpty(), profession);
            assertFalse(fit.others().contains(fit.primary()), profession);
        });
    }

    @Test
    void everyAttributeMattersToSomeProfession() {
        for (VillagerAttributes.Stat stat : VillagerAttributes.Stat.values()) {
            assertTrue(VillagerAttributes.FITS.values().stream().anyMatch(fit -> fit.primary() == stat || fit.others().contains(stat)), stat.id());
        }
    }

    @Test
    void levelsGetSlowerAndStarsCoverTheRange() {
        for (int level = 1; level < VillagerAttributes.MAX_LEVEL; level++) {
            assertTrue(VillagerAttributes.needed(level + 1) > VillagerAttributes.needed(level));
        }
        assertEquals(0, VillagerAttributes.stars(0.0F));
        assertEquals(5, VillagerAttributes.stars(1.0F));
        assertEquals(VillagerAttributes.Stat.WISDOM, VillagerAttributes.Stat.byId("wisdom"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "pt_br"})
    void everyAttributeIsTranslated(String language) throws Exception {
        JsonObject lang;
        try (InputStream stream = VillagerAttributesTest.class.getResourceAsStream("/assets/mca/lang/" + language + ".json")) {
            assertNotNull(stream, language);
            lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (VillagerAttributes.Stat stat : VillagerAttributes.Stat.values()) {
            assertTrue(lang.has(((TranslatableContents) stat.getName().getContents()).getKey()), language + " " + stat.id());
            assertTrue(lang.has(((TranslatableContents) stat.getDescription().getContents()).getKey()), language + " " + stat.id());
        }
    }
}
