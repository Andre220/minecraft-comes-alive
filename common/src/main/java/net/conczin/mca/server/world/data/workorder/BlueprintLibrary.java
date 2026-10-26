package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Blueprints are ordinary structure files (the same format as a vanilla structure block saves),
 * stored as {@code mca:blueprints/<name>}. Captured ones live in the world's
 * {@code generated/mca/structure/blueprints} folder; data packs and the mod can ship more.
 */
public final class BlueprintLibrary {
    public static final String PREFIX = "blueprints/";
    private static final int MAX_NAME_LENGTH = 40;
    /** Parsed blueprints per loaded template, dropped together with the template. */
    private static final Map<StructureTemplate, Blueprint> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private BlueprintLibrary() {
    }

    /** The created blueprint, or why it could not be saved. */
    public record CaptureResult(@Nullable Blueprint blueprint, @Nullable Component error, boolean replaced) {
        static CaptureResult error(String key, Object... args) {
            return new CaptureResult(null, Component.translatable(key, args), false);
        }
    }

    /** "Small House!" becomes "small_house"; empty when nothing usable is left. */
    public static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.length() > MAX_NAME_LENGTH ? slug.substring(0, MAX_NAME_LENGTH) : slug;
    }

    public static Identifier idFor(String name) {
        return MCA.locate(PREFIX + slug(name));
    }

    public static boolean isBlueprint(Identifier id) {
        return id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith(PREFIX) && id.getPath().length() > PREFIX.length();
    }

    /** "mca:blueprints/small_house" becomes "Small house". */
    public static String displayName(Identifier id) {
        String path = id.getPath().startsWith(PREFIX) ? id.getPath().substring(PREFIX.length()) : id.getPath();
        String spaced = path.replace('_', ' ').trim();
        return spaced.isEmpty() ? path : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public static int maxSize() {
        return Math.max(1, Config.getInstance().blueprintMaxSize);
    }

    private static StructureTemplateManager manager(MinecraftServer server) {
        return server.getStructureManager();
    }

    public static List<Identifier> list(MinecraftServer server) {
        return manager(server).listTemplates().filter(BlueprintLibrary::isBlueprint).sorted().toList();
    }

    public static Optional<Blueprint> get(MinecraftServer server, Identifier id) {
        if (!isBlueprint(id)) {
            return Optional.empty();
        }
        return manager(server).get(id).map(template -> CACHE.computeIfAbsent(template, t -> parse(id, t)));
    }

    /** Saves everything inside the area, air included, as a blueprint. Entities are left out. */
    public static CaptureResult capture(ServerLevel level, WorkArea area, String name, String author) {
        String slug = slug(name);
        if (slug.isEmpty()) {
            return CaptureResult.error("blueprint.error.name");
        }
        int max = maxSize();
        if (area.sizeX() > max || area.sizeY() > max || area.sizeZ() > max) {
            return CaptureResult.error("blueprint.error.too_big", max);
        }
        BlockPos min = new BlockPos(area.minX(), area.minY(), area.minZ());
        BlockPos max2 = new BlockPos(area.maxX(), area.maxY(), area.maxZ());
        if (!level.isLoaded(min) || !level.isLoaded(max2)) {
            return CaptureResult.error("blueprint.error.unloaded");
        }

        Identifier id = MCA.locate(PREFIX + slug);
        StructureTemplateManager manager = manager(level.getServer());
        boolean replaced = manager.get(id).isPresent();
        StructureTemplate template = manager.getOrCreate(id);
        // structure voids mark spots the blueprint does not care about, like in vanilla
        template.fillFromWorld(level, min, new Vec3i(area.sizeX(), area.sizeY(), area.sizeZ()), false, List.of(Blocks.STRUCTURE_VOID));
        template.setAuthor(author);
        CACHE.remove(template);
        if (!manager.save(id)) {
            return CaptureResult.error("blueprint.error.save");
        }
        Blueprint blueprint = parse(id, template);
        CACHE.put(template, blueprint);
        return new CaptureResult(blueprint, null, replaced);
    }

    /**
     * Puts a blueprint into the world at once, as it was saved (chests keep their contents, signs their text), its
     * lowest corner on {@code origin} and turned like {@link Blueprint#place}. For players in creative. False if its
     * structure file is gone.
     */
    public static boolean placeNow(ServerLevel level, Identifier id, Blueprint blueprint, BlockPos origin, int rotation) {
        Optional<StructureTemplate> template = manager(level.getServer()).get(id);
        if (template.isEmpty()) {
            return false;
        }
        int turns = BlueprintTransform.normalize(rotation);
        // vanilla turns about the pivot (0, 0, 0); moved back so the lowest corner lands on origin, as BlueprintTransform does
        BlockPos shift = switch (turns) {
            case 1 -> new BlockPos(blueprint.sizeZ() - 1, 0, 0);
            case 2 -> new BlockPos(blueprint.sizeX() - 1, 0, blueprint.sizeZ() - 1);
            case 3 -> new BlockPos(0, 0, blueprint.sizeX() - 1);
            default -> BlockPos.ZERO;
        };
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.values()[turns])
                .setRotationPivot(BlockPos.ZERO)
                .setIgnoreEntities(true);
        BlockPos at = origin.offset(shift);
        return template.get().placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_CLIENTS);
    }

    /** Reads the blocks of a template through its saved form, which is public and stable. */
    static Blueprint parse(Identifier id, StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag paletteTag = tag.getList("palette")
                .or(() -> tag.getListOrEmpty("palettes").getList(0))
                .orElseGet(ListTag::new);
        List<BlockState> palette = paletteTag.compoundStream()
                .map(state -> NbtUtils.readBlockState(BuiltInRegistries.BLOCK, state))
                .toList();

        List<Blueprint.Entry> entries = new ArrayList<>();
        tag.getListOrEmpty("blocks").compoundStream().forEach(block -> {
            ListTag pos = block.getListOrEmpty("pos");
            int state = block.getIntOr("state", -1);
            if (pos.size() == 3 && state >= 0 && state < palette.size()) {
                entries.add(new Blueprint.Entry(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), palette.get(state)));
            }
        });
        Vec3i size = template.getSize();
        return new Blueprint(id, displayName(id), size.getX(), size.getY(), size.getZ(), List.copyOf(entries));
    }
}
