package com.pixelmoneffectiveness.client.battle;

import com.pixelmoneffectiveness.util.PixelmonEffectivenessHelper;
import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.context.ContextKeys;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Moveset;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import com.pixelmonmod.pixelmon.client.gui.battles.battleScreens.PokemonMenuButton;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.*;

public class BattleSwitchEffectivenessHelper {

    public static class SwitchEvaluationResult {
        public final PixelmonClientData enemy;
        public final PokemonMatchupRating best;
        public final Map<UUID, PokemonMatchupRating> ratingsByUuid;

        public SwitchEvaluationResult(PixelmonClientData enemy, PokemonMatchupRating best, Map<UUID, PokemonMatchupRating> ratingsByUuid) {
            this.enemy = enemy;
            this.best = best;
            this.ratingsByUuid = ratingsByUuid;
        }

        public PokemonMatchupRating getRating(UUID uuid) {
            return (uuid != null) ? ratingsByUuid.get(uuid) : null;
        }
    }

    public static SwitchEvaluationResult evaluateSwitchOptions(ClientBattleManager bm, List<PokemonMenuButton> buttons) {
        if (bm == null) return null;
        PixelmonClientData enemy = PixelmonEffectivenessHelper.getActiveEnemy(bm);
        if (enemy == null) return null;

        List<Holder<Type>> enemyTypes = enemy.getTypes();
        boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);

        Minecraft mc = Minecraft.getInstance();
        PlayerPartyStorage party = (mc.player != null) ? StorageProxy.getPartyNow(mc.player) : null;
        if (party == null) {
            try {
                party = ClientStorageManager.party();
            } catch (Throwable ignored) {}
        }

        Map<UUID, PokemonMatchupRating> map = new HashMap<>();
        PokemonMatchupRating best = null;

        if (buttons != null && !buttons.isEmpty()) {
            for (PokemonMenuButton btn : buttons) {
                PixelmonClientData pig = btn.getPig();
                if (pig == null || pig.pokemonUUID == null) continue;

                Pokemon fullPoke = (party != null) ? party.get(pig.pokemonUUID) : null;
                PokemonMatchupRating rating = evaluateSingle(pig, fullPoke, enemyTypes, inverse);
                map.put(pig.pokemonUUID, rating);

                if (rating.totalScore > 0 && (best == null || rating.totalScore > best.totalScore)) {
                    best = rating;
                }
            }
        } else if (bm.fullOurPokemon != null) {
            for (PixelmonClientData pig : bm.fullOurPokemon) {
                if (pig == null || pig.pokemonUUID == null) continue;

                Pokemon fullPoke = (party != null) ? party.get(pig.pokemonUUID) : null;
                PokemonMatchupRating rating = evaluateSingle(pig, fullPoke, enemyTypes, inverse);
                map.put(pig.pokemonUUID, rating);

                if (rating.totalScore > 0 && (best == null || rating.totalScore > best.totalScore)) {
                    best = rating;
                }
            }
        }

        if (best != null) {
            best.isMostEffective = true;
        }

        return new SwitchEvaluationResult(enemy, best, map);
    }

    public static PokemonMatchupRating evaluateSingle(
        PixelmonClientData pig,
        Pokemon fullPoke,
        List<Holder<Type>> enemyTypes,
        boolean inverse
    ) {
        String name = pig.getDisplayName().getString();
        UUID uuid = pig.pokemonUUID;
        int level = pig.level;
        double currentHp = (pig.health != null) ? pig.health.get() : 0.0;
        int maxHp = pig.maxHealth;
        boolean isFainted = currentHp <= 0;
        boolean isEgg = false;

        List<Holder<Type>> ourTypes = pig.getTypes();
        Moveset moveset = (fullPoke != null && fullPoke.getMoveset() != null) ? fullPoke.getMoveset() : pig.moveset;

        return evaluatePokemon(name, uuid, level, currentHp, maxHp, isFainted, isEgg, ourTypes, moveset, enemyTypes, enemyTypes, inverse);
    }

    public static PokemonMatchupRating evaluatePokemon(
        String name,
        UUID uuid,
        int level,
        double currentHp,
        int maxHp,
        boolean isFainted,
        boolean isEgg,
        List<Holder<Type>> candidateTypes,
        Moveset moveset,
        List<Holder<Type>> targetDefensiveTypes,
        List<Holder<Type>> targetOffensiveTypes,
        boolean inverse
    ) {
        if (isEgg || isFainted || currentHp <= 0) {
            return new PokemonMatchupRating(name, uuid, level, currentHp, maxHp, isFainted, isEgg, null, "—", null, 0.0, 0, false, 0.0, 1.0, -1.0);
        }

        Attack bestAtk = null;
        String bestAtkName = "—";
        Type bestAtkType = null;
        double bestMult = 0.0;
        int bestBasePow = 0;
        boolean bestStab = false;
        double bestAtkScore = 0.0;

        if (moveset != null) {
            for (int i = 0; i < moveset.size(); i++) {
                Attack atk = moveset.get(i);
                if (atk == null || atk.getMove() == null) continue;
                if (atk.getAttackCategory() == AttackCategory.STATUS) continue;

                Type atkType = atk.getType().value();
                double mult = (targetDefensiveTypes != null && !targetDefensiveTypes.isEmpty())
                    ? PixelmonEffectivenessHelper.calculateEffectiveness(atk, atkType, targetDefensiveTypes, inverse)
                    : 1.0;

                int pow = atk.getMove().getBasePower();
                if (pow <= 0) pow = 60;

                boolean stab = false;
                if (candidateTypes != null) {
                    for (Holder<Type> ct : candidateTypes) {
                        if (ct != null && ct.value().equals(atkType)) {
                            stab = true;
                            break;
                        }
                    }
                }

                double score = pow * mult * (stab ? 1.5 : 1.0);
                if (score > bestAtkScore || (score == bestAtkScore && mult > bestMult)) {
                    bestAtkScore = score;
                    bestAtk = atk;
                    bestAtkType = atkType;
                    bestMult = mult;
                    bestBasePow = pow;
                    bestStab = stab;
                    bestAtkName = getAttackDisplayName(atk);
                }
            }
        }

        // Defensive calculation against opponent types
        double worstDefMult = 1.0;
        if (targetOffensiveTypes != null && candidateTypes != null && !candidateTypes.isEmpty()) {
            for (Holder<Type> tot : targetOffensiveTypes) {
                if (tot != null) {
                    double defM = tot.value().getTotalEffectiveness(candidateTypes, inverse);
                    if (defM > worstDefMult) {
                        worstDefMult = defM;
                    }
                }
            }
        }

        double defenseFactor = 1.0;
        if (worstDefMult <= 0.25) defenseFactor = 1.35;
        else if (worstDefMult <= 0.5) defenseFactor = 1.2;
        else if (worstDefMult >= 4.0) defenseFactor = 0.65;
        else if (worstDefMult >= 2.0) defenseFactor = 0.8;

        double levelFactor = 0.8 + (Math.min(level, 100) / 100.0) * 0.4;
        double totalScore = bestAtkScore * defenseFactor * levelFactor;

        return new PokemonMatchupRating(
            name, uuid, level, currentHp, maxHp, isFainted, isEgg,
            bestAtk, bestAtkName, bestAtkType, bestMult, bestBasePow, bestStab,
            bestAtkScore, worstDefMult, totalScore
        );
    }

    public static String getAttackDisplayName(Attack attack) {
        if (attack == null || attack.getMove() == null) return "—";
        try {
            String key = attack.getMove().getTranslationKey();
            if (key != null && !key.isEmpty()) {
                return Component.translatable(key).getString();
            }
        } catch (Throwable ignored) {}
        return attack.getMove().getAttackName();
    }
}
