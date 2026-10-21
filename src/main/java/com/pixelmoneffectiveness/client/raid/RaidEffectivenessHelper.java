package com.pixelmoneffectiveness.client.raid;

import com.pixelmoneffectiveness.client.battle.BattleSwitchEffectivenessHelper;
import com.pixelmoneffectiveness.client.battle.PokemonMatchupRating;
import com.pixelmonmod.api.client.screen.StatefulScreen;
import com.pixelmonmod.api.client.screen.util.ScreenArea;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.raids.RaidType;
import com.pixelmonmod.pixelmon.battles.raids.data.RaidData;
import com.pixelmonmod.pixelmon.client.gui.raids.RaidStartScreen;
import com.pixelmonmod.pixelmon.client.gui.raids.TeraRaidStartScreen;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RaidEffectivenessHelper {

    private static Field TERA_RAID_FIELD = null;
    private static Field TERA_SHOW_PARTY_FIELD = null;
    private static Method TERA_LAYOUT_METHOD = null;
    private static Field STANDARD_RAID_FIELD = null;

    static {
        try {
            TERA_RAID_FIELD = TeraRaidStartScreen.class.getDeclaredField("raid");
            TERA_RAID_FIELD.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            TERA_SHOW_PARTY_FIELD = TeraRaidStartScreen.class.getDeclaredField("showParty");
            TERA_SHOW_PARTY_FIELD.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            TERA_LAYOUT_METHOD = TeraRaidStartScreen.class.getDeclaredMethod("layout");
            TERA_LAYOUT_METHOD.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            STANDARD_RAID_FIELD = RaidStartScreen.class.getDeclaredField("raid");
            STANDARD_RAID_FIELD.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    public static class RaidEvaluationResult {
        public final RaidData raidData;
        public final Pokemon boss;
        public final boolean isTera;
        public final int stars;
        public final PokemonMatchupRating best;
        public final PokemonMatchupRating[] ratings;

        public RaidEvaluationResult(RaidData raidData, Pokemon boss, boolean isTera, int stars, PokemonMatchupRating best, PokemonMatchupRating[] ratings) {
            this.raidData = raidData;
            this.boss = boss;
            this.isTera = isTera;
            this.stars = stars;
            this.best = best;
            this.ratings = ratings;
        }

        public PokemonMatchupRating getRating(int slot) {
            if (ratings != null && slot >= 0 && slot < ratings.length) {
                return ratings[slot];
            }
            return null;
        }
    }

    public static RaidData getRaidData(Screen screen) {
        if (screen instanceof TeraRaidStartScreen tera) {
            if (TERA_RAID_FIELD != null) {
                try {
                    return (RaidData) TERA_RAID_FIELD.get(tera);
                } catch (Throwable ignored) {}
            }
        } else if (screen instanceof StatefulScreen stateful && "raid_start".equals(stateful.identifier())) {
            if (STANDARD_RAID_FIELD != null) {
                try {
                    return (RaidData) STANDARD_RAID_FIELD.get(null);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static boolean isTeraShowingParty(TeraRaidStartScreen screen) {
        if (TERA_SHOW_PARTY_FIELD != null) {
            try {
                return TERA_SHOW_PARTY_FIELD.getBoolean(screen);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static ScreenArea getTeraLayout(TeraRaidStartScreen screen) {
        if (TERA_LAYOUT_METHOD != null) {
            try {
                return (ScreenArea) TERA_LAYOUT_METHOD.invoke(screen);
            } catch (Throwable ignored) {}
        }
        int w = Math.min(Math.max(1, screen.width - 24), 420);
        int h = (int) (w / 1.72f);
        int maxH = Math.min(244, Math.max(1, screen.height - 24));
        if (h > maxH) {
            h = maxH;
            w = (int) (h * 1.72f);
        }
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;
        return new ScreenArea(x, y, w, h);
    }

    public static RaidEvaluationResult evaluate(RaidData raid) {
        if (raid == null) return null;
        Pokemon boss = raid.getPokemon();
        if (boss == null) return null;

        boolean isTera = (raid.getType() != null && raid.getType().is(RaidType.TERA));
        int stars = raid.getStars();

        // 1. Determine boss defensive types (for Tera Raid: purely its Tera Type!)
        List<Holder<Type>> defTypes = new ArrayList<>();
        if (isTera && boss.getTeraType() != null) {
            defTypes.add(boss.getTeraType());
        } else if (boss.getForm() != null && boss.getForm().getTypes() != null) {
            defTypes.addAll(boss.getForm().getTypes());
        }

        // 2. Determine boss offensive types
        List<Holder<Type>> offTypes = new ArrayList<>();
        if (boss.getForm() != null && boss.getForm().getTypes() != null) {
            offTypes.addAll(boss.getForm().getTypes());
        }
        if (isTera && boss.getTeraType() != null && !offTypes.contains(boss.getTeraType())) {
            offTypes.add(boss.getTeraType());
        }

        Minecraft mc = Minecraft.getInstance();
        PlayerPartyStorage party = (mc.player != null) ? StorageProxy.getPartyNow(mc.player) : null;
        if (party == null) {
            try {
                party = ClientStorageManager.party();
            } catch (Throwable ignored) {}
        }

        PokemonMatchupRating[] ratings = new PokemonMatchupRating[6];
        PokemonMatchupRating best = null;

        if (party != null) {
            for (int i = 0; i < 6; i++) {
                Pokemon p = party.get(i);
                if (p == null) continue;

                String name = p.getDisplayName().getString();
                int level = p.getPokemonLevel();
                double hp = p.getHealth();
                int maxHp = p.getMaxHealth();
                boolean fainted = p.isFainted() || hp <= 0;
                boolean egg = p.isEgg();

                List<Holder<Type>> candidateTypes = (p.getForm() != null && p.getForm().getTypes() != null)
                    ? p.getForm().getTypes() : Collections.emptyList();

                PokemonMatchupRating rating = BattleSwitchEffectivenessHelper.evaluatePokemon(
                    name, p.getUUID(), level, hp, maxHp, fainted, egg,
                    candidateTypes, p.getMoveset(), defTypes, offTypes, false
                );
                ratings[i] = rating;

                if (rating.totalScore > 0 && (best == null || rating.totalScore > best.totalScore)) {
                    best = rating;
                }
            }
        }

        if (best != null) {
            best.isMostEffective = true;
        }

        return new RaidEvaluationResult(raid, boss, isTera, stars, best, ratings);
    }
}
