package net.conczin.mca.expansion.mirror;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors: a small one for tables and the like, and a standing one two blocks tall. With {@link Config#villagerMirrors}
 * on, a click on the glass opens the hair editor, without wearing out a comb; with {@link Config#mirrorReflections}
 * on too, the glass shows who stands in front of it (see MirrorReflections).
 */
public final class Mirrors {
    /** How far from the glass a player can be to look into it. */
    static final double REACH = 4.0;

    public static final Map<String, Block> SMALL = new LinkedHashMap<>();
    public static final Map<String, Block> STANDING = new LinkedHashMap<>();
    /** The block holding a mirror's glass. Set once registered. */
    public static BlockEntityType<MirrorBlockEntity> MIRROR;

    static {
        mirrors("oak", Blocks.OAK_PLANKS);
        mirrors("spruce", Blocks.SPRUCE_PLANKS);
        mirrors("birch", Blocks.BIRCH_PLANKS);
        mirrors("jungle", Blocks.JUNGLE_PLANKS);
        mirrors("acacia", Blocks.ACACIA_PLANKS);
        mirrors("dark_oak", Blocks.DARK_OAK_PLANKS);
        mirrors("mangrove", Blocks.MANGROVE_PLANKS);
        mirrors("cherry", Blocks.CHERRY_PLANKS);
        mirrors("pale_oak", Blocks.PALE_OAK_PLANKS);
        mirrors("bamboo", Blocks.BAMBOO_PLANKS);
        mirrors("crimson", Blocks.CRIMSON_PLANKS);
        mirrors("warped", Blocks.WARPED_PLANKS);
    }

    private static void mirrors(String wood, Block planks) {
        String small = wood + "_mirror";
        Block block = BlocksMCA.register(small, new SmallMirrorBlock(BlocksMCA.baseProps(small, planks).noOcclusion()));
        ItemsMCA.register(small, new BlockItem(block, ItemsMCA.blockProps(small)));
        SMALL.put(wood, block);

        String standing = wood + "_standing_mirror";
        block = BlocksMCA.register(standing, new StandingMirrorBlock(BlocksMCA.baseProps(standing, planks).noOcclusion()));
        ItemsMCA.register(standing, new BlockItem(block, ItemsMCA.blockProps(standing)));
        STANDING.put(wood, block);
    }

    private Mirrors() {
    }

    /** Loads the class, so its blocks and items are in MCA's registration maps. */
    public static void bootstrap() {
    }

    public static boolean enabled() {
        return Config.getInstance().villagerMirrors;
    }

    /** Whether a player is near enough to the glass, and in front of it, to see into it. */
    public static boolean canLookInto(Player player, BlockPos glassPos, BlockState state) {
        if (!(state.getBlock() instanceof MirrorBlock mirror)) {
            return false;
        }
        Vec3 center = mirror.glassCenter(glassPos, state);
        Vec3 eye = player.getEyePosition();
        return eye.distanceToSqr(center) <= REACH * REACH && eye.subtract(center).dot(MirrorBlock.normal(state)) > 0.0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerBlockEntityTypes(BlockEntityTypesMCA.TriFunction register) {
        List<Block> blocks = new ArrayList<>(SMALL.values());
        blocks.addAll(STANDING.values());
        MIRROR = register.apply(MCA.locate("mirror"), (BlockEntityTypesMCA.BlockEntitySupplier) MirrorBlockEntity::new, blocks.toArray(new Block[0]));
    }
}
