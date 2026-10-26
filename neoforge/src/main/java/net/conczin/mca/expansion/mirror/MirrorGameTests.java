package net.conczin.mca.expansion.mirror;

import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Mirrors: the standing one is one piece, the small one needs something to stand on, and who can look into them. */
@PrefixGameTestTemplate(false)
public final class MirrorGameTests {
    private MirrorGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void standingMirrorIsOnePiece(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17866, 2, 400));
        prepareFlatArea(helper, center, 4, 4);
        Block block = Mirrors.STANDING.get("oak");
        BlockState state = block.defaultBlockState().setValue(MirrorBlock.FACING, Direction.SOUTH);
        level.setBlockAndUpdate(center, state);
        block.setPlacedBy(level, center, state, null, ItemStack.EMPTY);
        helper.assertTrue(level.getBlockState(center.above()).is(block)
                          && level.getBlockState(center.above()).getValue(StandingMirrorBlock.HALF) == DoubleBlockHalf.UPPER, "puts its upper half up");
        helper.assertTrue(level.getBlockEntity(center) instanceof MirrorBlockEntity && level.getBlockEntity(center.above()) == null,
                "the lower half holds the glass");
        level.setBlockAndUpdate(center.above(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(center).isAir(), "goes whole when its upper half is broken");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void smallMirrorStandsOnSomething(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17876, 2, 400));
        prepareFlatArea(helper, center, 4, 4);
        BlockPos on = center.above(2);
        Block block = Mirrors.SMALL.get("oak");
        BlockState state = block.defaultBlockState().setValue(MirrorBlock.FACING, Direction.SOUTH);
        helper.assertTrue(!state.canSurvive(level, on), "nothing to stand on in the air");

        level.setBlockAndUpdate(on.below(), Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(on, state);
        helper.assertTrue(state.canSurvive(level, on) && !level.getBlockState(on).getValue(SmallMirrorBlock.LOW), "stands on a full block");
        level.setBlockAndUpdate(on.below(), Blocks.OAK_SLAB.defaultBlockState());
        helper.assertTrue(level.getBlockState(on).is(block) && level.getBlockState(on).getValue(SmallMirrorBlock.LOW), "sits down onto a slab");
        level.setBlockAndUpdate(on.below(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(on).isAir(), "falls with what it stood on");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void lookingIntoAMirror(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17886, 2, 400));
        prepareFlatArea(helper, center, 6, 4);
        Block block = Mirrors.STANDING.get("oak");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = block.defaultBlockState().setValue(MirrorBlock.FACING, facing);
            MirrorBlock mirror = (MirrorBlock) block;
            Vec3 glass = mirror.glassCenter(center, state);
            Vec3 middle = Vec3.atBottomCenterOf(center).add(0.0, glass.y - center.getY(), 0.0);
            helper.assertTrue(glass.subtract(middle).dot(MirrorBlock.normal(state)) > 0.0 && glass.distanceTo(middle) < 0.1,
                    "the glass is a little in front of the middle, facing " + facing + ": " + glass);
        }

        BlockState state = block.defaultBlockState().setValue(MirrorBlock.FACING, Direction.SOUTH);
        level.setBlockAndUpdate(center, state);
        block.setPlacedBy(level, center, state, null, ItemStack.EMPTY);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 2.5);
        helper.assertTrue(Mirrors.canLookInto(player, center, state), "in front of it");
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() - 1.5);
        helper.assertTrue(!Mirrors.canLookInto(player, center, state), "not from behind");
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 9.5);
        helper.assertTrue(!Mirrors.canLookInto(player, center, state), "not from far away");
        helper.succeed();
    }
}
