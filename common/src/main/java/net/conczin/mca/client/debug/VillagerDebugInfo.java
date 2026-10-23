package net.conczin.mca.client.debug;

import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.Config;
import net.conczin.mca.KeyBindings;
import net.conczin.mca.client.render.McaVillagerFeatureSlots;
import net.conczin.mca.client.render.VanillaVillagerLook;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.voxel.VoxelLayerRenderer;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Debug data of a villager, shared by the in-world overlay and the debug screen. AI data is read
 * from the integrated server, so it is only available in singleplayer.
 */
public final class VillagerDebugInfo {
    public static final int TITLE_COLOR = 0xFFFFDD55;
    public static final int TEXT_COLOR = 0xFFFFFFFF;
    public static final int AI_COLOR = 0xFF88DDFF;
    public static final int HOME_BUILDING_COLOR = 0xFF55FF55;

    private VillagerDebugInfo() {
    }

    public record Line(String text, int color) {
        static Line text(String text) {
            return new Line(text, TEXT_COLOR);
        }
    }

    /** Genes in percent, traits and age, as MCA stores them. */
    public static List<Line> genetics(Villager villager) {
        List<Line> lines = new ArrayList<>();
        if (!(villager instanceof VillagerLike<?> mca) || mca.getGenetics() == null) {
            lines.add(Line.text("Plain vanilla villager: no MCA genes (look seeded from its UUID)."));
            return lines;
        }
        lines.add(Line.text("Gender " + mca.getGenetics().getGender().getDataName() + "  age " + mca.getAgeState().name().toLowerCase(Locale.ROOT)));
        for (Genetics.Gene gene : mca.getGenetics()) {
            lines.add(Line.text(Component.translatable(gene.getType().getTranslationKey()).getString()
                                + ": " + Math.round(gene.get() * 100) + "%"));
        }
        List<String> traits = mca.getTraits().getTraits().stream().map(Traits.Trait::getName).map(Component::getString).toList();
        lines.add(Line.text("Traits: " + (traits.isEmpty() ? "none" : String.join(", ", traits))));
        return lines;
    }

    /** Mood, personality and work. */
    public static List<Line> social(Villager villager) {
        List<Line> lines = new ArrayList<>();
        if (!(villager instanceof VillagerEntityMCA mca)) {
            lines.add(Line.text("Plain vanilla villager: no MCA mood or personality."));
            return lines;
        }
        var brain = mca.getVillagerBrain();
        lines.add(Line.text("Mood " + brain.getMood().getName() + " (" + brain.getMoodValue() + ")"));
        lines.add(Line.text("Personality " + brain.getPersonality().getName().getString()));
        lines.add(Line.text("Chore " + brain.getCurrentJob().name().toLowerCase(Locale.ROOT)
                            + "  move " + brain.getMoveState().name().toLowerCase(Locale.ROOT)));
        lines.add(Line.text(String.format(Locale.ROOT, "Skill %.1f / %.0f  intelligence %.0f%%", // expansion: minds
                Skills.get(mca), Skills.cap(mca), Skills.intelligence(mca) * 100)));
        Needs needs = mca.getNeeds();
        if (!needs.hasNeeds()) {
            lines.add(Line.text("Needs: off (villagerNeeds) or baby"));
        } else {
            StringBuilder values = new StringBuilder("Needs");
            for (Needs.Need need : Needs.Need.values()) {
                values.append(' ').append(need.id()).append('=').append(Math.round(needs.get(need)));
            }
            lines.add(Line.text(values.toString()));
            List<String> thoughts = needs.thoughts().stream().map(t -> Component.translatable(t.key()).getString() + " " + t.mood()).toList();
            lines.add(Line.text("Thoughts: " + (thoughts.isEmpty() ? "none" : String.join(", ", thoughts))
                                + (needs.isOverridingSchedule() ? "  (overriding schedule)" : "") + (needs.isNapping() ? "  napping" : "")));
        }
        return lines;
    }

    /** What the human-like renderer draws for this villager. */
    public static List<Line> look(Villager villager) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.text(String.format(Locale.ROOT, "Hitbox %.2f x %.2f  baby=%s", villager.getBbWidth(), villager.getBbHeight(), villager.isBaby())));
        VillagerVisuals visuals = VanillaVillagerLook.visualsFor(villager);
        if (visuals == null) {
            lines.add(Line.text("Human-like look off (useSquidwardModels + squidwardHumanLikeModels)"));
            return lines;
        }

        float[] scale = VanillaVillagerLook.modelScale(visuals);
        lines.add(Line.text(String.format(Locale.ROOT, "%s %s  breast=%.2f  face gene=%.2f",
                visuals.female() ? "Female" : "Male", stageName(visuals), visuals.breastSize(), visuals.faceGene())));
        lines.add(Line.text(String.format(Locale.ROOT, "Genetic size h=%.2f w=%.2f  model scale h=%.2f w=%.2f",
                visuals.rawVerticalScaleFactor(), visuals.rawHorizontalScaleFactor(), scale[1], scale[0])));

        Identifier face = SkinExporter.getFace(visuals);
        Identifier texture = VanillaVillagerLook.textureFor(visuals);
        boolean faEyes = Config.getInstance().freshAnimationsIntegration && McaVillagerFeatureSlots.eyesMatchFreshAnimations(face);
        var player = Minecraft.getInstance().player;
        boolean cubes = Config.getInstance().squidward3dLayers && player != null && VoxelLayerRenderer.inRange(villager.distanceToSqr(player));
        lines.add(Line.text(String.format(Locale.ROOT, "Face %s  FA eyes=%s  texture=%dpx  3D=%s",
                shortId(face.toString()), faEyes, textureSize(texture), cubes)));
        lines.add(Line.text("Skin " + shortId(visuals.skin()) + "  clothes " + shortId(visuals.clothes())));
        lines.add(Line.text("Hair " + shortId(visuals.hairBase()) + " " + shortId(visuals.hairBangs()) + " " + shortId(visuals.hairBack())));
        return lines;
    }

    /** AI state from the integrated server. */
    @SuppressWarnings("deprecation")
    public static List<Line> ai(@Nullable Mob server) {
        List<Line> lines = new ArrayList<>();
        if (server == null) {
            lines.add(new Line("AI: singleplayer only", AI_COLOR));
            return lines;
        }
        try {
            var brain = server.getBrain();
            String activity = brain.getActiveNonCoreActivity().map(Object::toString).orElse("none");
            String move = server instanceof VillagerEntityMCA mca ? "  move=" + mca.getVillagerBrain().getMoveState().name() : "";
            lines.add(new Line("AI " + (server.isNoAi() ? "FROZEN" : "running") + "  activity=" + activity + move
                               + "  [" + KeyBindings.DEBUG_FREEZE.getTranslatedKeyMessage().getString() + "] freeze", AI_COLOR));
            List<String> tasks = brain.getRunningBehaviors().stream()
                    .map(behavior -> behavior.getClass().getSimpleName())
                    .filter(name -> !name.isEmpty())
                    .limit(4)
                    .toList();
            lines.add(new Line("Tasks " + (tasks.isEmpty() ? "none" : String.join(", ", tasks)), AI_COLOR));
            if (server instanceof VillagerEntityMCA queued && ActionQueue.isEnabled()) {
                List<String> queue = queued.getActionQueue().entries().stream()
                        .map(e -> e.type().id() + " " + Math.round(e.score() * 100) + e.source().code()).toList();
                lines.add(new Line("Queue " + (queue.isEmpty() ? "empty" : String.join(" > ", queue)), AI_COLOR));
            }
            if (server instanceof VillagerEntityMCA mca) {
                boolean custom = !mca.getVillagerBrain().getCustomSchedule().isEmpty();
                lines.add(new Line("Schedule " + String.format(Locale.ROOT, "%02dh", (SchedulesMCA.currentSlot(mca.level()) + 6) % 24)
                                   + " " + SchedulesMCA.scheduledActivity(mca).getName() + (custom ? " (personal)" : " (timeline)"), AI_COLOR));
            }
            String walk = brain.getMemory(MemoryModuleType.WALK_TARGET)
                    .map(target -> target.getTarget().currentBlockPosition().toShortString())
                    .orElse("none");
            Path path = server.getNavigation().getPath();
            lines.add(new Line("Walk target " + walk + (path == null ? "  no path" : "  path " + path.getNodeCount() + " nodes"), AI_COLOR));
        } catch (RuntimeException e) {
            lines.add(new Line("AI: unavailable (" + e.getClass().getSimpleName() + ")", AI_COLOR));
        }
        return lines;
    }

    /** A place the villager remembers: home bed, job site, meeting point, ... */
    public record PointOfInterest(String label, GlobalPos pos, int color) {
    }

    /** The places in the villager's brain memories, plus its home building. */
    public static List<PointOfInterest> pointsOfInterest(@Nullable Mob server) {
        List<PointOfInterest> points = new ArrayList<>();
        if (server == null) {
            return points;
        }
        try {
            var brain = server.getBrain();
            addPoint(points, brain, MemoryModuleType.HOME, "Bed", 0xFF55AAFF);
            addPoint(points, brain, MemoryModuleType.JOB_SITE, "Job site", 0xFFFFAA00);
            addPoint(points, brain, MemoryModuleType.POTENTIAL_JOB_SITE, "Job site (claiming)", 0xFFFFDD77);
            addPoint(points, brain, MemoryModuleType.MEETING_POINT, "Meeting point", 0xFFFF55FF);
            addPoint(points, brain, MemoryModuleTypeMCA.SHELTER_BED, "Shelter bed", 0xFF55FFFF);
            addPoint(points, brain, MemoryModuleTypeMCA.MOURNING_SITE, "Mourning site", 0xFFAAAAAA);
        } catch (RuntimeException ignored) {
            // Server-side state changed while reading.
        }
        return points;
    }

    private static void addPoint(List<PointOfInterest> points, Brain<?> brain, MemoryModuleType<GlobalPos> type, String label, int color) {
        if (brain.hasMemoryValue(type)) {
            brain.getMemoryInternal(type).ifPresent(pos -> points.add(new PointOfInterest(label, pos, color)));
        }
    }

    /** The building around the villager's home bed, in its home village. */
    public static @Nullable Building homeBuilding(@Nullable Mob server) {
        if (!(server instanceof VillagerEntityMCA mca)) {
            return null;
        }
        try {
            Optional<GlobalPos> home = mca.getResidency().getHome();
            return home.flatMap(pos -> mca.getResidency().getHomeVillage().flatMap(village -> village.getBuildingAt(pos.pos()))).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The places as text, for the debug screen. */
    public static List<Line> places(@Nullable Mob server) {
        List<Line> lines = new ArrayList<>();
        if (server == null) {
            return lines;
        }
        List<PointOfInterest> points = pointsOfInterest(server);
        for (PointOfInterest point : points) {
            boolean here = point.pos().dimension() == server.level().dimension();
            lines.add(new Line(point.label() + " " + point.pos().pos().toShortString() + (here ? "" : " (" + point.pos().dimension().identifier().getPath() + ")"), point.color()));
        }
        Building building = homeBuilding(server);
        if (building != null) {
            lines.add(new Line("Home building " + building.getType() + " " + building.getPos0().toShortString() + " to " + building.getPos1().toShortString(), HOME_BUILDING_COLOR));
        }
        if (lines.isEmpty()) {
            lines.add(new Line("No remembered places", AI_COLOR));
        }
        return lines;
    }

    /** The server copy of a villager, in singleplayer. */
    public static @Nullable Mob serverMob(Entity villager) {
        var integrated = Minecraft.getInstance().getSingleplayerServer();
        if (integrated == null) {
            return null;
        }
        ServerLevel level = integrated.getLevel(villager.level().dimension());
        return level != null && level.getEntity(villager.getUUID()) instanceof Mob mob ? mob : null;
    }

    /** Toggles the villager's AI (NoAI) on the integrated server; returns a message for the player. */
    public static Component toggleFreeze(@Nullable Mob server) {
        var integrated = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || integrated == null) {
            return Component.literal("[MCA debug] Freezing works in singleplayer only.");
        }
        boolean freeze = !server.isNoAi();
        integrated.execute(() -> {
            server.setNoAi(freeze);
            if (freeze) {
                server.getNavigation().stop();
            }
        });
        return Component.literal("[MCA debug] " + (freeze ? "Frozen " : "Unfrozen ") + server.getName().getString());
    }

    private static String stageName(VillagerVisuals visuals) {
        float height = visuals.dimensions().getHeight();
        for (AgeState state : AgeState.values()) {
            if (state != AgeState.UNASSIGNED && state.getHeight() == height) {
                return state.name().toLowerCase(Locale.ROOT);
            }
        }
        return "adult";
    }

    private static int textureSize(Identifier texture) {
        return Minecraft.getInstance().getTextureManager().getTexture(texture) instanceof DynamicTexture dynamic && dynamic.getPixels() != null
                ? dynamic.getPixels().getWidth()
                : 0;
    }

    private static String shortId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return "-";
        }
        String path = id.substring(id.indexOf(':') + 1);
        return path.replace("skins/", "").replace(".png", "");
    }
}
