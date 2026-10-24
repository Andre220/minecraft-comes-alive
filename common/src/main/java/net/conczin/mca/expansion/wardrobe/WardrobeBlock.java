package net.conczin.mca.expansion.wardrobe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A wardrobe: one door, two blocks tall. Wardrobes side by side (facing the same way) join into one piece of
 * furniture, at most three doors wide, like fences join; a longer row makes several.
 */
public class WardrobeBlock extends FurnitureBlock {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class, Part.BOTTOM_LEFT, Part.TOP_LEFT);
    private static final List<Part> PARTS = List.of(Part.BOTTOM_LEFT, Part.TOP_LEFT);
    /** Joined to the wardrobe on its left or right, seen from the front. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** The most doors joined into one wardrobe. */
    public static final int MAX_WIDTH = 3;
    /** How far along a row the doors are counted. */
    private static final int ROW_LIMIT = 48;
    public static final EnumProperty<Piece> PIECE = EnumProperty.create("piece", Piece.class, Piece.BODY, Piece.DOOR);
    /** Three outfits behind each door, one above the other. */
    public static final int SHELVES = 3;

    public WardrobeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, Part.BOTTOM_LEFT)
                .setValue(LEFT, false).setValue(RIGHT, false).setValue(PIECE, Piece.BODY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LEFT, RIGHT, PIECE);
    }

    @Override
    protected EnumProperty<Part> partProperty() {
        return PART;
    }

    @Override
    protected List<Part> parts() {
        return PARTS;
    }

    @Override
    public int slots() {
        return SHELVES;
    }

    @Override
    protected boolean holdsCloset(BlockState state) {
        return state.getValue(PART) == Part.BOTTOM_LEFT;
    }

    @Override
    protected BlockPos closetPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.TOP_LEFT ? pos.below() : pos;
    }

    @Override
    protected int compartmentAt(double x, double y) {
        return 0;
    }

    /** The shelf at a height: the inside runs from 2 to 30 px, a third each. */
    @Override
    protected int slotAt(double x, double y) {
        return Math.clamp((int) ((y - 2.0 / 16.0) / (28.0 / 16.0 / SHELVES)), 0, SHELVES - 1);
    }

    @Override
    protected SoundEvent openSound() {
        return BlockSetType.OAK.doorOpen();
    }

    @Override
    protected SoundEvent closeSound() {
        return BlockSetType.OAK.doorClose();
    }

    private boolean sameRow(LevelReader level, BlockPos pos, BlockState state) {
        BlockState other = level.getBlockState(pos);
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING) && other.getValue(PART) == state.getValue(PART);
    }

    /** Counting along the row from its left end, wardrobes go in threes: each joins the others of its three. */
    @Override
    protected BlockState connect(LevelReader level, BlockPos pos, BlockState state) {
        if (!state.is(this)) {
            return state;
        }
        Direction right = right(state.getValue(FACING));
        int index = 0;
        BlockPos left = pos.relative(right.getOpposite());
        while (index < ROW_LIMIT && sameRow(level, left, state)) {
            index++;
            left = left.relative(right.getOpposite());
        }
        boolean hasLeft = index > 0;
        boolean hasRight = sameRow(level, pos.relative(right), state);
        return state.setValue(LEFT, hasLeft && index % MAX_WIDTH != 0)
                .setValue(RIGHT, hasRight && (index + 1) % MAX_WIDTH != 0);
    }

    @Override
    protected VoxelShape bodyShape(BlockState state) {
        return box(state.getValue(FACING), 0, 0, 1, 16, 16, 15);
    }

    /** The open door, swung out about 100 degrees on its hinge: from the front edge some 14 px out. */
    @Override
    protected VoxelShape openShape(BlockState state, ClosetBlockEntity closet) {
        if (!closet.isOpen(0)) {
            return Shapes.empty();
        }
        boolean left = state.getValue(LEFT);
        boolean right = state.getValue(RIGHT);
        boolean hingeRight = left && !right;
        double hinge = hingeRight ? (right ? 15.5 : 15) : (left ? 0.5 : 1);
        double x0 = hingeRight ? hinge - 1 : hinge - 2.5;
        double x1 = hingeRight ? hinge + 2.5 : hinge + 1;
        boolean top = state.getValue(PART) == Part.TOP_LEFT;
        return box(state.getValue(FACING), x0, top ? 0 : 3, 16, x1, top ? 13 : 16, 30);
    }
}
