package com.pixelmoneffectiveness.handler;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

public class PokemonFreezeDespawnHandler {

    public static final double DESPAWN_DISTANCE_CHUNKS = 62.5;
    public static final double DESPAWN_DISTANCE_BLOCKS = 1000.0; // 1000 blocks
    public static final double DESPAWN_DISTANCE_SQ = DESPAWN_DISTANCE_BLOCKS * DESPAWN_DISTANCE_BLOCKS;

    public static final double FREEZE_DISTANCE_BLOCKS = 32.0; // 2 chunks
    public static final double FREEZE_DISTANCE_SQ = FREEZE_DISTANCE_BLOCKS * FREEZE_DISTANCE_BLOCKS;

    public static final long DESPAWN_TIMEOUT_MS = 20L * 60L * 1000L; // 20 minutes

    private static final String NBT_LAST_SEEN = "pe_last_seen_time";

    private static CompoundTag getPersistentData(PixelmonEntity pixelmon) {
        if ((Object) pixelmon instanceof IEntityExtension ext) {
            return ext.getPersistentData();
        }
        return null;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof PixelmonEntity pixelmon)) {
            return;
        }

        if (isExempt(pixelmon)) {
            return;
        }

        CompoundTag data = getPersistentData(pixelmon);
        long now = System.currentTimeMillis();

        if (event.loadedFromDisk()) {
            if (data != null && data.contains(NBT_LAST_SEEN)) {
                long lastSeen = data.getLong(NBT_LAST_SEEN);
                if (now - lastSeen > DESPAWN_TIMEOUT_MS) {
                    event.setCanceled(true);
                    return;
                }
            }

            Double minDistanceSq = getMinPlayerDistanceSq(pixelmon, event.getLevel());
            if (minDistanceSq != null) {
                if (minDistanceSq >= DESPAWN_DISTANCE_SQ) {
                    event.setCanceled(true);
                    return;
                }

                if (minDistanceSq > FREEZE_DISTANCE_SQ) {
                    pixelmon.setNoAi(true);
                    pixelmon.canMove = false;
                }
            }
        } else {
            if (data != null) {
                data.putLong(NBT_LAST_SEEN, now);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof PixelmonEntity pixelmon)) {
            return;
        }

        // Run check once every 20 ticks (1 second) to ensure zero performance overhead
        if (pixelmon.tickCount % 20 != 0) {
            return;
        }

        if (isExempt(pixelmon)) {
            if (pixelmon.isNoAi()) {
                pixelmon.setNoAi(false);
                pixelmon.canMove = true;
            }
            return;
        }

        Level level = pixelmon.level();
        Double minDistanceSq = getMinPlayerDistanceSq(pixelmon, level);
        long now = System.currentTimeMillis();
        CompoundTag data = getPersistentData(pixelmon);

        // If no players are currently in this dimension, keep last seen and do not discard immediately
        if (minDistanceSq == null) {
            if (data != null && data.contains(NBT_LAST_SEEN)) {
                long lastSeen = data.getLong(NBT_LAST_SEEN);
                if (now - lastSeen > DESPAWN_TIMEOUT_MS) {
                    pixelmon.discard();
                }
            }
            return;
        }

        // Rule 1: Distance >= 50 chunks (800 blocks) -> Despawn immediately
        if (minDistanceSq >= DESPAWN_DISTANCE_SQ) {
            pixelmon.discard();
            return;
        }

        // Rule 2: 20-minute timeout if player hasn't approached within 32 blocks
        if (data != null) {
            if (data.contains(NBT_LAST_SEEN)) {
                long lastSeen = data.getLong(NBT_LAST_SEEN);
                if (now - lastSeen > DESPAWN_TIMEOUT_MS && minDistanceSq > FREEZE_DISTANCE_SQ) {
                    pixelmon.discard();
                    return;
                }
            } else {
                data.putLong(NBT_LAST_SEEN, now);
            }
        }

        // Rule 3: Freezing <= 49 chunks, unfreezing when player is close
        if (minDistanceSq > FREEZE_DISTANCE_SQ) {
            // Player is away (> 32 blocks and < 800 blocks) -> Freeze AI for optimal performance
            if (!pixelmon.isNoAi()) {
                pixelmon.setNoAi(true);
            }
            pixelmon.canMove = false;
        } else {
            // Player is nearby (<= 32 blocks) -> Unfreeze & refresh activity timer
            if (pixelmon.isNoAi()) {
                pixelmon.setNoAi(false);
            }
            pixelmon.canMove = true;
            if (data != null) {
                data.putLong(NBT_LAST_SEEN, now);
            }
        }
    }

    private static boolean isExempt(PixelmonEntity pixelmon) {
        if (pixelmon.hasOwner() || pixelmon.getOwnerUUID() != null) {
            return true;
        }
        if (pixelmon.getPokemon() != null && pixelmon.getPokemon().getStorage() != null) {
            return true;
        }
        if (pixelmon.battleController != null) {
            return true;
        }
        if (pixelmon.isPersistenceRequired() || pixelmon.hasCustomName()) {
            return true;
        }
        return false;
    }

    private static Double getMinPlayerDistanceSq(PixelmonEntity pixelmon, Level level) {
        List<? extends Player> players = level.players();
        if (players.isEmpty()) {
            return null;
        }

        double minSq = Double.MAX_VALUE;
        boolean foundValidPlayer = false;

        for (Player p : players) {
            if (p.isSpectator()) {
                continue;
            }
            double dSq = pixelmon.distanceToSqr(p);
            if (dSq < minSq) {
                minSq = dSq;
                foundValidPlayer = true;
            }
        }

        return foundValidPlayer ? minSq : null;
    }
}
