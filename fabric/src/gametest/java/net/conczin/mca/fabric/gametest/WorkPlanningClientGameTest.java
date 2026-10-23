package net.conczin.mca.fabric.gametest;

import net.conczin.mca.Config;
import net.conczin.mca.client.gui.workorder.WorkPlanningScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.BlueprintLibrary;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Plays the planning screen like a player would: open it, pick build, drag out an area,
 * confirm, and watch a builder fill it with blocks from the village storage. Screenshots
 * of every step land in the run directory's screenshots folder.
 */
public class WorkPlanningClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(100);
            context.takeScreenshot("mca_planning_00_joined");
            // MCA may greet new players with its own screens (destiny etc.); the world is what matters here
            context.setScreen(() -> null);
            context.waitTicks(20);
            singleplayer.getServer().runCommand("time set 4000");

            BlockPos ground = singleplayer.getServer().computeOnServer(WorkPlanningClientGameTest::prepareVillage);
            context.waitTicks(100);
            context.takeScreenshot("mca_planning_0_world");

            context.setScreen(WorkPlanningScreen::new);
            context.waitTicks(10);
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("mca_planning_1_opened");

            // build tool, then drag an area from the middle of the screen
            context.getInput().pressKey(GLFW.GLFW_KEY_2);
            context.waitTicks(2);
            int[] size = context.computeOnClient(mc -> new int[]{mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()});
            context.getInput().setCursorPos(size[0] * 0.45, size[1] * 0.55);
            context.waitTicks(2);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.getInput().setCursorPos(size[0] * 0.58, size[1] * 0.62);
            context.waitTicks(3);
            context.takeScreenshot("mca_planning_2_selecting");

            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            singleplayer.getConnection().waitForServerboundPackets();
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("mca_planning_3_planned");

            int orders = singleplayer.getServer().computeOnServer(server -> village(server, ground).getWorkOrders().getOrders().size());
            if (orders != 1) {
                throw new AssertionError("expected the drag to plan one work order, found " + orders);
            }

            // orders panel and some time for the builder
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
            context.waitTicks(200);
            context.takeScreenshot("mca_planning_4_working");

            singleplayer.getServer().waitFor(server -> village(server, ground).getWorkOrders().isEmpty(), 6000);
            context.waitTicks(40);
            context.takeScreenshot("mca_planning_5_done");
            // nothing of the finished order may linger in the view
            context.getInput().setCursorPos(size[0] * 0.2, size[1] * 0.3);
            context.waitTicks(60);
            context.takeScreenshot("mca_planning_5b_done_later");

            // capture the platform the builder made as a blueprint
            context.getInput().pressKey(GLFW.GLFW_KEY_4);
            context.waitTicks(2);
            context.getInput().setCursorPos(size[0] * 0.45, size[1] * 0.55);
            context.waitTicks(2);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.getInput().setCursorPos(size[0] * 0.58, size[1] * 0.62);
            context.getInput().scroll(1);
            context.waitTicks(3);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.getInput().typeChars("Plataforma");
            context.waitTicks(2);
            context.takeScreenshot("mca_planning_7_naming");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(2);
            singleplayer.getConnection().waitForServerboundPackets();
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            boolean saved = singleplayer.getServer().computeOnServer(server ->
                    BlueprintLibrary.list(server).contains(BlueprintLibrary.idFor("plataforma")));
            if (!saved) {
                throw new AssertionError("the capture did not save a blueprint");
            }

            // place it next to the original, turned once
            context.getInput().pressKey(GLFW.GLFW_KEY_3);
            context.waitTicks(10);
            singleplayer.getConnection().waitForClientboundPackets();
            context.getInput().setCursorPos(size[0] * 0.65, size[1] * 0.5);
            context.waitTicks(5);
            context.takeScreenshot("mca_planning_8_blueprint_preview");
            context.getInput().pressKey(GLFW.GLFW_KEY_R);
            context.waitTicks(3);
            context.takeScreenshot("mca_planning_9_blueprint_turned");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            singleplayer.getConnection().waitForServerboundPackets();
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("mca_planning_10_blueprint_planned");
            long blueprintOrders = singleplayer.getServer().computeOnServer(server -> village(server, ground).getWorkOrders().getOrders().stream()
                    .filter(order -> order.getType() == WorkOrderType.BLUEPRINT).count());
            if (blueprintOrders != 1) {
                throw new AssertionError("placing the blueprint should plan one order, found " + blueprintOrders);
            }

            context.setScreen(() -> null);
            context.waitTicks(5);
            boolean cameraBack = context.computeOnClient(mc -> mc.getCameraEntity() == mc.player);
            if (!cameraBack) {
                throw new AssertionError("closing the planning screen did not give the camera back to the player");
            }
            context.takeScreenshot("mca_planning_6_closed");
        }
    }

    private static Village village(MinecraftServer server, BlockPos ground) {
        return VillageManager.get(server.overworld()).findNearestVillage(ground, Village.BORDER_MARGIN)
                .orElseThrow(() -> new AssertionError("the test village is gone"));
    }

    /** A storage room south of the player, a builder, and the rank to plan. Returns the player's feet position. */
    private static BlockPos prepareVillage(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        PlayerSaveData.get(player).setOverrideVillageRequirements(true);
        // the feature is off by default
        Config.getInstance().villagerWorkOrders = true;
        Config.getServerConfig().villagerWorkOrders = true;

        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
        BlockPos feet = new BlockPos(player.getBlockX(), groundY, player.getBlockZ());
        // look south, the planning camera starts behind the player looking the same way
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 0.0F, 20.0F, true);

        BlockPos center = feet.south(16);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) == 4 || Math.abs(z) == 4 || y == 3) {
                        level.setBlock(center.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    }
                }
            }
        }
        BlockPos door = center.north(4);
        BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        level.setBlock(center.offset(-2, 0, 2), Blocks.LECTERN.defaultBlockState(), 3);
        List<BlockPos> chests = List.of(center.offset(-3, 0, -3), center.offset(3, 0, -3), center.offset(-3, 0, 1), center.offset(3, 0, 1));
        for (BlockPos chest : chests) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        }

        VillageManager manager = VillageManager.get(level);
        Building.validationResult result = manager.processBuilding(center);
        if (result != Building.validationResult.SUCCESS) {
            throw new AssertionError("could not register the storage room: " + result);
        }
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        if (!VillageStorage.exists(village)) {
            manager.forceRoomType(center, VillageStorage.STORAGE_BUILDING);
        }
        Container chest = VillageStorage.getContainer(level, chests.getFirst()).orElseThrow();
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 64));

        VillagerEntityMCA builder = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet.east(4).south(5)))
                .withName("Bob the Builder")
                .spawn(EntitySpawnReason.COMMAND);
        builder.setProfession(ProfessionsMCA.BUILDER);
        return feet;
    }
}
