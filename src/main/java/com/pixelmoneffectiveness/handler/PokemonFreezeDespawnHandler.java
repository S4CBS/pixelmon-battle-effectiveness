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

    public static final double DESPAWN_DISTANCE_BLOCKS = 1000.0; // 1000 blocks
    public static final double DESPAWN_DISTANCE_SQ = DESPAWN_DISTANCE_BLOCKS * DESPAWN_DISTANCE_BLOCKS;

    public static final double FREEZE_DISTANCE_BLOCKS = 300.0; // 300 blocks (AI active within 300 blocks)
    public static final double FREEZE_DISTANCE_SQ = FREEZE_DISTANCE_BLOCKS * FREEZE_DISTANCE_BLOCKS;

    public static final long FAR_DESPAWN_TIMEOUT_MS = 10L * 60L * 1000L; // 10 minutes (300 to 1000 blocks)
    public static final long NEAR_DESPAWN_TIMEOUT_MS = 4L * 60L * 1000L; // 4 minutes (active/near lifetime)
    private static final long FREEZE_REAPPROACH_THRESHOLD_MS = 10_000L; // 10 seconds away to consider re-approached

    private static final String NBT_LAST_FAR_SEEN = "pe_last_seen_time";
    private static final String NBT_GLOBAL_DESPAWN = "pe_global_despawn_time";
    private static final String NBT_FREEZE_TIME = "pe_freeze_time";
    public static final String NBT_SPAWN_TIME = "pe_spawn_time";

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
            if (data != null && !data.contains(NBT_SPAWN_TIME)) {
                data.putLong(NBT_SPAWN_TIME, now - (pixelmon.tickCount * 50L));
            }

            if (data != null && data.contains(NBT_GLOBAL_DESPAWN)) {
                long globalDespawn = data.getLong(NBT_GLOBAL_DESPAWN);
                if (now >= globalDespawn) {
                    event.setCanceled(true);
                    return;
                }
            } else if (data != null && data.contains(NBT_LAST_FAR_SEEN)) {
                long lastSeen = data.getLong(NBT_LAST_FAR_SEEN);
                if (now - lastSeen > FAR_DESPAWN_TIMEOUT_MS) {
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
                    if (data != null) {
                        data.putLong(NBT_FREEZE_TIME, now);
                    }
                } else {
                    pixelmon.setNoAi(false);
                    pixelmon.canMove = true;
                    if (data != null) {
                        data.putLong(NBT_LAST_FAR_SEEN, now);
                        if (!data.contains(NBT_GLOBAL_DESPAWN)) {
                            data.putLong(NBT_GLOBAL_DESPAWN, now + NEAR_DESPAWN_TIMEOUT_MS);
                        }
                    }
                }
            }
        } else {
            // Fresh spawn
            if (data != null) {
                data.putLong(NBT_LAST_FAR_SEEN, now);
                data.putLong(NBT_SPAWN_TIME, now);
            }

            Double minDistanceSq = getMinPlayerDistanceSq(pixelmon, event.getLevel());
            if (minDistanceSq != null) {
                if (minDistanceSq >= DESPAWN_DISTANCE_SQ) {
                    event.setCanceled(true);
                    return;
                }

                if (minDistanceSq <= FREEZE_DISTANCE_SQ) {
                    pixelmon.setNoAi(false);
                    pixelmon.canMove = true;
                    if (data != null) {
                        data.putLong(NBT_GLOBAL_DESPAWN, now + NEAR_DESPAWN_TIMEOUT_MS);
                    }
                } else {
                    pixelmon.setNoAi(true);
                    pixelmon.canMove = false;
                    if (data != null) {
                        data.putLong(NBT_FREEZE_TIME, now);
                    }
                }
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

        CompoundTag data = getPersistentData(pixelmon);
        long now = System.currentTimeMillis();

        if (isExempt(pixelmon)) {
            if (pixelmon.battleController != null && data != null && data.contains(NBT_GLOBAL_DESPAWN)) {
                data.putLong(NBT_GLOBAL_DESPAWN, now + NEAR_DESPAWN_TIMEOUT_MS);
            }
            if (pixelmon.isNoAi()) {
                pixelmon.setNoAi(false);
                pixelmon.canMove = true;
            }
            return;
        }

        Level level = pixelmon.level();
        Double minDistanceSq = getMinPlayerDistanceSq(pixelmon, level);

        // If no players are currently in this dimension, check timeouts
        if (minDistanceSq == null) {
            if (data != null) {
                if (data.contains(NBT_GLOBAL_DESPAWN)) {
                    long globalDespawn = data.getLong(NBT_GLOBAL_DESPAWN);
                    if (now >= globalDespawn) {
                        pixelmon.discard();
                        return;
                    }
                } else if (data.contains(NBT_LAST_FAR_SEEN)) {
                    long lastSeen = data.getLong(NBT_LAST_FAR_SEEN);
                    if (now - lastSeen > FAR_DESPAWN_TIMEOUT_MS) {
                        pixelmon.discard();
                        return;
                    }
                }
            }
            return;
        }

        // Rule 1: Distance >= 1000 blocks -> Despawn immediately
        if (minDistanceSq >= DESPAWN_DISTANCE_SQ) {
            pixelmon.discard();
            return;
        }

        // Rule 2: If global 4-minute timer was set, check if it expired
        if (data != null && data.contains(NBT_GLOBAL_DESPAWN)) {
            long globalDespawn = data.getLong(NBT_GLOBAL_DESPAWN);
            if (now >= globalDespawn) {
                pixelmon.discard();
                return;
            }
        }

        // Rule 3: Near vs Far handling
        if (minDistanceSq <= FREEZE_DISTANCE_SQ) {
            // Player is nearby (<= 32 blocks)
            boolean wasFrozen = pixelmon.isNoAi();
            if (wasFrozen) {
                pixelmon.setNoAi(false);
            }
            pixelmon.canMove = true;

            if (data != null) {
                // Reset the 10-minute far timer
                data.putLong(NBT_LAST_FAR_SEEN, now);

                // If approaching from far away (was frozen for >10s) or no global timer set yet:
                // Grant the 4-minute global timer!
                if (!data.contains(NBT_GLOBAL_DESPAWN)) {
                    data.putLong(NBT_GLOBAL_DESPAWN, now + NEAR_DESPAWN_TIMEOUT_MS);
                } else if (wasFrozen) {
                    long freezeDuration = now - data.getLong(NBT_FREEZE_TIME);
                    if (freezeDuration >= FREEZE_REAPPROACH_THRESHOLD_MS) {
                        data.putLong(NBT_GLOBAL_DESPAWN, now + NEAR_DESPAWN_TIMEOUT_MS);
                    }
                }
            }
        } else {
            // Player is far (> 32 blocks and < 1000 blocks) -> Freeze AI for optimal performance
            if (!pixelmon.isNoAi()) {
                pixelmon.setNoAi(true);
                if (data != null) {
                    data.putLong(NBT_FREEZE_TIME, now);
                }
            }
            pixelmon.canMove = false;

            // If no global despawn timer was set yet (never approached), check 10-minute timeout
            if (data != null && !data.contains(NBT_GLOBAL_DESPAWN)) {
                if (data.contains(NBT_LAST_FAR_SEEN)) {
                    long lastSeen = data.getLong(NBT_LAST_FAR_SEEN);
                    if (now - lastSeen > FAR_DESPAWN_TIMEOUT_MS) {
                        pixelmon.discard();
                        return;
                    }
                } else {
                    data.putLong(NBT_LAST_FAR_SEEN, now);
                }
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
