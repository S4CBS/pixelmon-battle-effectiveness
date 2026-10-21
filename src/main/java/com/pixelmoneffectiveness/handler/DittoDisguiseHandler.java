package com.pixelmoneffectiveness.handler;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.pokemon.InitializeCategory;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.util.helpers.RandomHelper;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmoneffectiveness.client.radar.DittoDisguiseHelper;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/**
 * Server-side handler that pre-determines if a wild candidate Pokémon is a disguised Ditto
 * when spawning, marks it, and guarantees it transforms into Ditto upon capture.
 */
public class DittoDisguiseHandler {

    private static final String NBT_DITTO_CHECKED = "pe_ditto_checked";
    private static final String NBT_IS_DITTO = "pe_is_ditto";

    private static CompoundTag getPersistentData(PixelmonEntity pixelmon) {
        if ((Object) pixelmon instanceof IEntityExtension ext) {
            return ext.getPersistentData();
        }
        return null;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof PixelmonEntity pixelmon)) return;
        if (pixelmon.getOwner() != null) return; // Wild only

        Pokemon p = pixelmon.getPokemon();
        if (p == null) return;

        CompoundTag data = getPersistentData(pixelmon);
        if (data != null && data.getBoolean(NBT_IS_DITTO)) {
            DittoDisguiseHelper.markConfirmedDitto(pixelmon.getUUID());
            pixelmon.addTag("pe_is_ditto");
            return;
        }

        if (DittoDisguiseHelper.isDittoDisguiseCandidate(p)) {
            if (data == null || !data.getBoolean(NBT_DITTO_CHECKED)) {
                if (data != null) data.putBoolean(NBT_DITTO_CHECKED, true);

                double chance = 0.001; // Default 0.1%
                try {
                    if (EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.dittoDisguiseChance.get() >= 0.0) {
                        chance = EffectivenessConfig.CONFIG.dittoDisguiseChance.get();
                    } else if (p.getSpecies() != null && "Meltan".equalsIgnoreCase(p.getSpecies().getName())) {
                        chance = PixelmonConfigProxy.getSpawningMeltan().getMeltanTransformChance();
                    } else if (PixelmonConfigProxy.getSpawning() != null) {
                        chance = PixelmonConfigProxy.getSpawning().getTransformToDittoOnCatch();
                    }
                } catch (Throwable ignored) {}

                if (RandomHelper.getRandom().nextDouble() < chance) {
                    if (data != null) data.putBoolean(NBT_IS_DITTO, true);
                    pixelmon.addTag("pe_is_ditto");
                    DittoDisguiseHelper.markConfirmedDitto(pixelmon.getUUID());
                }
            }
        }
    }

    public static void onSuccessfulCapture(CaptureEvent.SuccessfulCapture event) {
        Pokemon p = event.getPokemon();
        if (p == null) return;

        UUID uuid = p.getUUID();
        boolean isDitto = false;
        if (uuid != null && DittoDisguiseHelper.isConfirmedDitto(p.getEntity())) {
            isDitto = true;
        }

        if (isDitto && !p.getSpecies().is(PixelmonSpecies.DITTO)) {
            int level = p.getPokemonLevel();
            boolean shiny = p.isShiny();
            String oldName = p.getDisplayName().getString();

            p.setSpecies(PixelmonSpecies.DITTO, true);
            p.initialize(InitializeCategory.INTRINSIC_FORCEFUL, InitializeCategory.SPECIES);
            p.setLevel(level);
            p.setShiny(shiny);

            ServerPlayer player = event.getPlayer();
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                    "§d[Pixelmon] ✨ Ого! Пойманный " + oldName + " оказался замаскированным Дитто!"
                ));
            }
        }
    }
}
