package com.pixelmoneffectiveness.handler;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.ApricornEvent;
import com.pixelmonmod.pixelmon.blocks.ApricornLeavesBlock;
import com.pixelmonmod.pixelmon.enums.items.ApricornType;
import com.pixelmonmod.pixelmon.init.registry.BlockRegistration;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class ApricornTreeHarvestHandler {

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }

        if (EffectivenessConfig.CONFIG != null && !EffectivenessConfig.CONFIG.apricornHarvestEnabled.get()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos startPos = event.getPos();
        BlockState clickedState = level.getBlockState(startPos);
        Block clickedBlock = clickedState.getBlock();

        boolean isLog = isApricornLog(clickedBlock);
        boolean isLeaves = clickedBlock instanceof ApricornLeavesBlock;
        if (!isLog && !isLeaves) {
            return;
        }

        List<BlockPos> ripeLeaves = findTreeRipeLeaves(level, startPos);
        if (ripeLeaves.isEmpty()) {
            return;
        }

        // On client: swing arm and cancel event to synchronize animation and prevent unwanted block placing
        if (level.isClientSide) {
            player.swing(InteractionHand.MAIN_HAND, true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // On server: harvest all ripe apricorns
        ServerPlayer serverPlayer = (player instanceof ServerPlayer sp) ? sp : null;
        int harvestedCount = 0;

        for (BlockPos leafPos : ripeLeaves) {
            BlockState leafState = level.getBlockState(leafPos);
            if (!(leafState.getBlock() instanceof ApricornLeavesBlock leavesBlock)) {
                continue;
            }
            if (!leafState.hasProperty(ApricornLeavesBlock.AGE) || leafState.getValue(ApricornLeavesBlock.AGE) < 2) {
                continue;
            }

            ApricornType apricornType = getApricornType(leavesBlock);
            if (apricornType == null) {
                continue;
            }

            ItemStack drop = new ItemStack(apricornType.apricorn());

            if (serverPlayer != null) {
                ApricornEvent.Pick pickEvent = new ApricornEvent.Pick(level, leafState, apricornType, leafPos, serverPlayer, drop);
                Pixelmon.EVENT_BUS.post(pickEvent);
                if (pickEvent.isCanceled()) {
                    continue;
                }
                drop = pickEvent.getPickedStack();
            }

            level.setBlockAndUpdate(leafPos, leafState.setValue(ApricornLeavesBlock.AGE, 0));
            harvestedCount++;

            if (!player.addItem(drop)) {
                player.drop(drop, false);
            }
        }

        if (harvestedCount > 0) {
            level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
            );
            player.displayClientMessage(
                Component.translatable("pixelmoneffectiveness.apricorn.harvested", harvestedCount),
                true
            );
            player.swing(InteractionHand.MAIN_HAND, true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean isApricornLog(Block block) {
        return block == BlockRegistration.APRICORN_LOG.get();
    }

    private static ApricornType getApricornType(ApricornLeavesBlock leavesBlock) {
        for (ApricornType type : ApricornType.values()) {
            if (type.leavesBlock() == leavesBlock) {
                return type;
            }
        }
        return null;
    }

    private static List<BlockPos> findTreeRipeLeaves(Level level, BlockPos origin) {
        List<BlockPos> ripeLeaves = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(origin);
        visited.add(origin);

        int maxSearchBlocks = 350;

        while (!queue.isEmpty() && visited.size() < maxSearchBlocks) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            Block currentBlock = currentState.getBlock();

            boolean isCurrentLog = isApricornLog(currentBlock);
            boolean isCurrentLeaves = currentBlock instanceof ApricornLeavesBlock;

            if (!isCurrentLog && !isCurrentLeaves) {
                continue;
            }

            if (isCurrentLeaves && currentState.hasProperty(ApricornLeavesBlock.AGE)) {
                if (currentState.getValue(ApricornLeavesBlock.AGE) >= 2) {
                    ripeLeaves.add(current);
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (visited.contains(neighbor)) continue;

                        if (Math.abs(neighbor.getX() - origin.getX()) > 7 ||
                            Math.abs(neighbor.getZ() - origin.getZ()) > 7 ||
                            neighbor.getY() - origin.getY() < -3 ||
                            neighbor.getY() - origin.getY() > 10) {
                            continue;
                        }

                        BlockState nState = level.getBlockState(neighbor);
                        Block nBlock = nState.getBlock();
                        if (isApricornLog(nBlock) || nBlock instanceof ApricornLeavesBlock) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        return ripeLeaves;
    }
}
