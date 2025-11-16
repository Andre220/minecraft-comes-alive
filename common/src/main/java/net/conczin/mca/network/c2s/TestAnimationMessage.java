package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record TestAnimationMessage(UUID villagerUUID, String animationType, String choreParam) implements HandleablePayload {
    public static final CustomPacketPayload.Type<TestAnimationMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("test_animation"));
    public static final StreamCodec<FriendlyByteBuf, TestAnimationMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TestAnimationMessage::villagerUUID,
            ByteBufCodecs.STRING_UTF8, TestAnimationMessage::animationType,
            ByteBufCodecs.STRING_UTF8, m -> m.choreParam != null ? m.choreParam : "",
            (uuid, type, chore) -> new TestAnimationMessage(uuid, type, chore.isEmpty() ? null : chore)
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity v = player.serverLevel().getEntity(villagerUUID);
        if (v instanceof VillagerLike<?> villager) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "▶ Test: " + animationType + (choreParam != null ? " (" + choreParam + ")" : "")
            ));
            
            // Close interaction screen so villager can move freely
            villager.getInteractions().stopInteracting();
            
            // Force villager into animation state for 5 seconds (100 ticks)
            switch (animationType) {
                case "walk" -> {
                    // Make villager walk towards player
                    villager.asEntity().getNavigation().moveTo(player, 1.0);
                }
                case "run" -> {
                    // Force panic to trigger running animation
                    villager.asEntity().getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.PANIC);
                    
                    // Calcular direção do olhar do player (para onde ele está olhando)
                    float playerYaw = player.getYRot(); // Rotação Y do player (em graus)
                    double playerYawRad = Math.toRadians(playerYaw); // Converter para radianos
                    
                    // Direção do olhar do player (vetor unitário)
                    double lookDirX = -Math.sin(playerYawRad);
                    double lookDirZ = Math.cos(playerYawRad);
                    
                    // NPC corre NESSA DIREÇÃO, se afastando do player (20 blocos para frente do olhar)
                    double targetX = player.getX() + lookDirX * 20;
                    double targetZ = player.getZ() + lookDirZ * 20;
                    
                    // Navegar até o ponto calculado com velocidade de corrida (1.8)
                    villager.asEntity().getNavigation().moveTo(targetX, player.getY(), targetZ, 1.8);
                    
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("🏃 Running in your look direction!"));
                    
                    // Reset panic after 5 seconds
                    player.server.tell(new net.minecraft.server.TickTask(100, () -> {
                        villager.asEntity().getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE);
                    }));
                }
                case "panic" -> {
                    // Just panic in place
                    villager.asEntity().getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.PANIC);
                    player.server.tell(new net.minecraft.server.TickTask(100, () -> {
                        villager.asEntity().getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE);
                    }));
                }
                case "trade" -> {
                    // Already interacting - just show message
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("💼 Trading animation plays during actual trade interaction"));
                }
                case "chore" -> {
                    if (choreParam != null) {
                        try {
                            net.conczin.mca.entity.ai.Chore chore = net.conczin.mca.entity.ai.Chore.valueOf(choreParam);
                            
                            // IMPORTANTE: Parar o villager antes de atribuir chore (animações funcionam melhor parado)
                            villager.asEntity().getNavigation().stop();
                            
                            // Atribuir o trabalho
                            villager.getVillagerBrain().assignJob(chore, player);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("🔨 Chore: " + chore.name() + " - OBSERVE o villager fazendo o trabalho!"));
                            
                            // Reset after 10 seconds so you can see the animation
                            player.server.tell(new net.minecraft.server.TickTask(200, () -> {
                                villager.getVillagerBrain().abandonJob();
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("✅ Chore finished!"));
                            }));
                        } catch (IllegalArgumentException e) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("❌ Invalid chore: " + choreParam));
                        }
                    }
                }
                case "jump" -> {
                    // Make villager jump repeatedly for visibility
                    for (int i = 0; i < 3; i++) {
                        final int delay = i * 15;
                        player.server.tell(new net.minecraft.server.TickTask(delay, () -> {
                            villager.asEntity().jumpFromGround();
                        }));
                    }
                }
                case "yes" -> {
                    // Animação de "SIM" - Cabeça para cima e para baixo (positivo)
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("👍 Nodding YES"));
                    // Força o villager a fazer movimento de sim por 3 segundos
                    for (int i = 0; i < 6; i++) {
                        final int tick = i * 10;
                        player.server.tell(new net.minecraft.server.TickTask(tick, () -> {
                            // Simula movimento de cabeça através de look at temporário
                            double baseY = villager.asEntity().getY();
                            villager.asEntity().getLookControl().setLookAt(
                                villager.asEntity().getX(),
                                baseY + (tick % 20 == 0 ? 2 : -1), // Alterna olhar cima/baixo
                                villager.asEntity().getZ()
                            );
                        }));
                    }
                }
                case "no" -> {
                    // Animação de "NÃO" - Cabeça de um lado para outro (negativo)
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("👎 Shaking NO"));
                    // Força o villager a fazer movimento de não por 3 segundos
                    for (int i = 0; i < 6; i++) {
                        final int tick = i * 10;
                        player.server.tell(new net.minecraft.server.TickTask(tick, () -> {
                            // Simula movimento de cabeça através de look at temporário
                            double baseX = villager.asEntity().getX();
                            double baseZ = villager.asEntity().getZ();
                            villager.asEntity().getLookControl().setLookAt(
                                baseX + (tick % 20 == 0 ? 1.5 : -1.5), // Alterna olhar esquerda/direita
                                villager.asEntity().getY() + 1,
                                baseZ
                            );
                        }));
                    }
                }
                case "positive" -> {
                    villager.getVillagerBrain().modifyMoodValue(20);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("😊 Mood increased"));
                }
                case "negative" -> {
                    villager.getVillagerBrain().modifyMoodValue(-20);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("😠 Mood decreased"));
                }
                case "follow" -> {
                    villager.getVillagerBrain().setMoveState(net.conczin.mca.entity.ai.MoveState.FOLLOW, player);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("👣 Following enabled for 10 seconds"));
                    // Reset after 10 seconds
                    player.server.tell(new net.minecraft.server.TickTask(200, () -> {
                        if (villager.getVillagerBrain().getMoveState() == net.conczin.mca.entity.ai.MoveState.FOLLOW) {
                            villager.getVillagerBrain().setMoveState(net.conczin.mca.entity.ai.MoveState.MOVE, null);
                        }
                    }));
                }
                case "stay" -> {
                    villager.getVillagerBrain().setMoveState(net.conczin.mca.entity.ai.MoveState.STAY, player);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("🛑 Staying in place for 10 seconds"));
                    // Reset after 10 seconds
                    player.server.tell(new net.minecraft.server.TickTask(200, () -> {
                        if (villager.getVillagerBrain().getMoveState() == net.conczin.mca.entity.ai.MoveState.STAY) {
                            villager.getVillagerBrain().setMoveState(net.conczin.mca.entity.ai.MoveState.MOVE, null);
                        }
                    }));
                }
            }
        }
    }

    @Override
    public Type<TestAnimationMessage> type() {
        return TYPE;
    }
}
