package com.pixelmoneffectiveness.handler;

import com.pixelmoneffectiveness.PixelmonEffectivenessMod;
import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.spawning.LegendarySpawner;
import com.pixelmonmod.pixelmon.spawning.PixelmonSpawning;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles proactive despawning of the 20% oldest wild non-boss/non-legendary Pokemon
 * 5 seconds before a scheduled Legendary or Boss spawn attempt if the spawn capacity is full.
 */
public class PokemonSpawnCapReliefHandler {

    private static long lastLegendaryProcessedTime = -1L;
    private static long lastBossProcessedTime = -1L;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        // Check once per second (every 20 ticks)
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        long now = System.currentTimeMillis();

        // 1. Check Legendary Spawner (fires 5 seconds before next spawn time)
        try {
            LegendarySpawner legSpawner = PixelmonSpawning.legendarySpawner;
            if (legSpawner != null && legSpawner.nextSpawnTime > now) {
                long remainingMs = legSpawner.nextSpawnTime - now;
                if (remainingMs <= 5000L && lastLegendaryProcessedTime != legSpawner.nextSpawnTime) {
                    lastLegendaryProcessedTime = legSpawner.nextSpawnTime;
                    checkAndRelieveCap(server, legSpawner, "Legendary (" + (remainingMs / 1000L) + "s before spawn)");
                }
            }
        } catch (Throwable t) {
            PixelmonEffectivenessMod.LOGGER.debug("Error checking legendary spawner capacity relief", t);
        }

        // 2. Check Boss Spawner (Mega Boss / Bosses - fires 5 seconds before next spawn time)
        try {
            LegendarySpawner bossSpawner = PixelmonSpawning.megaBossSpawner;
            if (bossSpawner != null && bossSpawner.nextSpawnTime > now) {
                long remainingMs = bossSpawner.nextSpawnTime - now;
                if (remainingMs <= 5000L && lastBossProcessedTime != bossSpawner.nextSpawnTime) {
                    lastBossProcessedTime = bossSpawner.nextSpawnTime;
                    checkAndRelieveCap(server, bossSpawner, "Boss (" + (remainingMs / 1000L) + "s before spawn)");
                }
            }
        } catch (Throwable t) {
            PixelmonEffectivenessMod.LOGGER.debug("Error checking boss spawner capacity relief", t);
        }
    }

    private static void checkAndRelieveCap(MinecraftServer server, LegendarySpawner spawner, String triggerContext) {
        List<PixelmonEntity> eligibleWild = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PixelmonEntity pixelmon && pixelmon.isAlive()) {
                    if (!isExemptFromPurge(pixelmon)) {
                        eligibleWild.add(pixelmon);
                    }
                }
            }
        }

        int maxSpawned = -1;
        int entitiesPerPlayer = 150;
        try {
            maxSpawned = PixelmonConfigProxy.getSpawningWild().getMaximumSpawnedPokemon();
            entitiesPerPlayer = PixelmonConfigProxy.getSpawningWild().getEntitiesPerPlayer();
        } catch (Throwable ignored) {}

        int playerCount = server.getPlayerCount();
        int playerLimit = entitiesPerPlayer * Math.max(1, playerCount);
        int effectiveCap = maxSpawned >= 0 ? Math.min(maxSpawned, playerLimit) : playerLimit;

        boolean hasCapacity = true;
        try {
            hasCapacity = spawner.hasCapacity(1);
        } catch (Throwable ignored) {}

        // Spawn capacity is considered full if Spawner.hasCapacity(1) fails or active wild count reaches effective cap
        boolean isCapFull = !hasCapacity || (effectiveCap > 0 && eligibleWild.size() >= (effectiveCap - 5));

        if (!isCapFull || eligibleWild.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Sort descending by existence duration (oldest Pokemon first)
        eligibleWild.sort((p1, p2) -> Long.compare(getPokemonAgeMs(p2, now), getPokemonAgeMs(p1, now)));

        // Despawn 20% of the oldest wild Pokemon
        int toDespawn = (int) Math.ceil(eligibleWild.size() * 0.20);
        int despawnedCount = 0;

        for (int i = 0; i < toDespawn && i < eligibleWild.size(); i++) {
            PixelmonEntity victim = eligibleWild.get(i);
            victim.discard();
            despawnedCount++;
        }

        PixelmonEffectivenessMod.LOGGER.info("[CapRelief] Spawn cap full ({}/{}). Despawned {} oldest wild Pokemon (20%) 5s before {} attempt. Remaining: {}",
                eligibleWild.size(), effectiveCap, despawnedCount, triggerContext, eligibleWild.size() - despawnedCount);
    }

    public static boolean isExemptFromPurge(PixelmonEntity pixelmon) {
        if (pixelmon == null || !pixelmon.isAlive()) {
            return true;
        }

        // 1. Owned by player
        if (pixelmon.hasOwner() || pixelmon.getOwnerUUID() != null) {
            return true;
        }
        Pokemon pokemon = pixelmon.getPokemon();
        if (pokemon != null && pokemon.getStorage() != null) {
            return true;
        }

        // 2. In active battle
        if (pixelmon.battleController != null) {
            return true;
        }

        // 3. Custom named (Name Tag) or persistent entity
        if (pixelmon.isPersistenceRequired() || pixelmon.hasCustomName()) {
            return true;
        }

        // 4. Legendary / Mythical / Ultra Beast / Shiny
        if (pokemon != null) {
            if (pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast() || pokemon.isShiny()) {
                return true;
            }
        }

        // 5. Boss Pokemon (any tier: Ultra, Legendary, Epic, Rare, Uncommon, Mega, etc.)
        try {
            if (pixelmon.isBossPokemon()) {
                return true;
            }
            BossTier tier = pixelmon.getBossTier();
            if (tier != null && tier.isBoss()) {
                return true;
            }
        } catch (Throwable ignored) {}

        if (pokemon != null && pokemon.isBoss()) {
            return true;
        }

        return false;
    }

    public static long getPokemonAgeMs(PixelmonEntity pixelmon, long now) {
        if ((Object) pixelmon instanceof IEntityExtension ext) {
            CompoundTag data = ext.getPersistentData();
            if (data != null && data.contains("pe_spawn_time")) {
                long spawnTime = data.getLong("pe_spawn_time");
                return Math.max(0L, now - spawnTime);
            }
        }
        return pixelmon.tickCount * 50L;
    }
}
