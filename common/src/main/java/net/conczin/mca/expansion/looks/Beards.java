package net.conczin.mca.expansion.looks;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Beards: a texture drawn over the face (under the talking mouth), tinted with the hair colour. Grown men often
 * have one; anyone can get one in the villager editor. Shown on adults only.
 */
public final class Beards {
    /** Built-in styles, from {@code assets/mca/skins/beard/<style>.png}. */
    public static final List<String> STYLES = List.of("stubble", "mustache", "goatee", "full", "long", "sideburns");
    private static final float MEN_WITH_BEARDS = 0.4F;

    /** The beard texture, or empty. */
    public static final CDataParameter<String> BEARD = CParameter.create("Beard", "");

    private Beards() {
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(BEARD);
    }

    public static boolean enabled() {
        return Config.getInstance().villagerBeards;
    }

    public static String texture(String style) {
        return MCA.locate("skins/beard/" + style + ".png").toString();
    }

    /** The choices in order: none first, then every style's texture. */
    public static List<String> choices() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(""), STYLES.stream().map(Beards::texture)).toList();
    }

    /** The style name of a beard texture, for labels ("" for none). */
    public static String styleOf(String texture) {
        for (String style : STYLES) {
            if (texture(style).equals(texture)) {
                return style;
            }
        }
        return texture.isEmpty() ? "" : texture;
    }

    public static String get(VillagerLike<?> villager) {
        return villager.getTrackedValue(BEARD);
    }

    public static void set(VillagerLike<?> villager, String texture) {
        villager.setTrackedValue(BEARD, texture);
    }

    /** At birth: some men get a beard (it shows once they are grown up). */
    public static void randomize(VillagerLike<?> villager) {
        if (!enabled()) {
            return;
        }
        var random = villager.asEntity().getRandom();
        boolean beard = villager.getGenetics().getGender() == Gender.MALE && random.nextFloat() < MEN_WITH_BEARDS;
        set(villager, beard ? texture(STYLES.get(random.nextInt(STYLES.size()))) : "");
    }

    /** What to draw: the beard of a grown up, when beards are on. */
    public static String forVisuals(VillagerLike<?> villager) {
        if (!enabled() || villager.getAgeState() != AgeState.ADULT) {
            return "";
        }
        String beard = get(villager);
        return beard == null ? "" : beard;
    }

    /** Cycles to the previous or next choice. */
    public static String cycle(String current, int step) {
        List<String> choices = choices();
        int index = Math.max(0, choices.indexOf(current));
        return choices.get(Math.floorMod(index + step, choices.size()));
    }
}
