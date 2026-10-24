package net.conczin.mca.expansion.wardrobe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** A dresser: two blocks wide and one tall (low enough to see over), eight drawers. */
public class DresserBlock extends FurnitureBlock {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class, Part.BOTTOM_LEFT, Part.BOTTOM_RIGHT);
    private static final List<Part> PARTS = List.of(Part.BOTTOM_LEFT, Part.BOTTOM_RIGHT);
    public static final EnumProperty<Piece> PIECE = EnumProperty.create("piece", Piece.class, Piece.BODY, Piece.DRAWER);
    /** Four drawers each side, two by two, an outfit in each. */
    public static final int DRAWERS = 4;

    public DresserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, Part.BOTTOM_LEFT).setValue(PIECE, Piece.BODY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, PIECE);
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
        return DRAWERS;
    }

    @Override
    protected boolean holdsCloset(BlockState state) {
        return true;
    }

    @Override
    protected BlockPos closetPos(BlockPos pos, BlockState state) {
        return pos;
    }

    /** Drawers are numbered from the bottom left: 0 and 1 below, 2 and 3 above. */
    @Override
    protected int compartmentAt(double x, double y) {
        return (y < 0.5 ? 0 : 2) + (x < 0.5 ? 0 : 1);
    }

    @Override
    protected int slotAt(double x, double y) {
        return compartmentAt(x, y);
    }

    @Override
    protected SoundEvent openSound() {
        return SoundEvents.BARREL_OPEN;
    }

    @Override
    protected SoundEvent closeSound() {
        return SoundEvents.BARREL_CLOSE;
    }

    @Override
    protected VoxelShape bodyShape(BlockState state) {
        return box(state.getValue(FACING), 0, 0, 1, 16, 16, 15);
    }

    /** Each open drawer, slid 8 px out. */
    @Override
    protected VoxelShape openShape(BlockState state, ClosetBlockEntity closet) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < DRAWERS; i++) {
            if (closet.isOpen(i)) {
                double x = (i % 2) * 7.5;
                double y = (i / 2) * 7.0;
                shape = Shapes.or(shape, box(state.getValue(FACING), 1 + x, 1 + y, 15, 7.5 + x, 7 + y, 24));
            }
        }
        return shape;
    }
}
