package com.pixelmoneffectiveness.util;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.battles.attack.AttackRegistry;
import com.pixelmonmod.pixelmon.api.context.ContextKeys;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.Effectiveness;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.BattleScreen;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.List;

public class PixelmonEffectivenessHelper {

    /**
     * Determines whether type effectiveness should be rendered for the given attack.
     */
    public static boolean shouldShowEffectiveness(Attack attack) {
        if (!EffectivenessConfig.CONFIG.enabled.get()) {
            return false;
        }

        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm == null || bm.displayedEnemyPokemon == null || bm.displayedEnemyPokemon.length == 0) {
            return false;
        }

        PixelmonClientData enemy = getActiveEnemy(bm);
        if (enemy == null || enemy.getTypes() == null || enemy.getTypes().isEmpty()) {
            return false;
        }

        if (attack == null || attack.getMove() == null) {
            return false;
        }

        // Only moves targeting opponents should show effectiveness against opponents
        if (!attack.getMove().targetsOpponents()) {
            return false;
        }

        boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);
        Type attackType = attack.getType().value();
        double eff = calculateEffectiveness(attack, attackType, enemy.getTypes(), inverse);

        // Status moves handling (e.g. Thunder Wave, Will-O-Wisp)
        if (attack.getAttackCategory() == AttackCategory.STATUS) {
            if (!EffectivenessConfig.CONFIG.showForStatusMoves.get()) {
                // By default for status moves, only show when enemy is immune (0x)
                return eff == 0.0;
            }
        }

        // Neutral moves (1x) handling
        if (!EffectivenessConfig.CONFIG.showForNormalMoves.get()) {
            if (eff == 1.0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the active opponent Pokemon (either currently targeted, or first alive).
     */
    public static PixelmonClientData getActiveEnemy(ClientBattleManager bm) {
        if (bm == null || bm.displayedEnemyPokemon == null || bm.displayedEnemyPokemon.length == 0) {
            return null;
        }

        // Check if any enemy is currently targeted in the BattleScreen GUI
        if (Minecraft.getInstance().screen instanceof BattleScreen battleScreen) {
            for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
                if (enemy != null && enemy.pokemonUUID != null && battleScreen.isTargeted(enemy.pokemonUUID)) {
                    if (isAlive(enemy)) {
                        return enemy;
                    }
                }
            }
        }

        // Otherwise return the first alive enemy
        for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
            if (isAlive(enemy)) {
                return enemy;
            }
        }

        // Fallback to first non-null enemy
        for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
            if (enemy != null) {
                return enemy;
            }
        }

        return null;
    }

    /**
     * Checks if a client Pokemon is considered alive.
     */
    private static boolean isAlive(PixelmonClientData poke) {
        return poke != null && poke.health != null && poke.health.get() > 0;
    }

    /**
     * Calculates move type effectiveness against enemy types,
     * including special move matchups like Freeze-Dry and Thousand Arrows.
     */
    public static double calculateEffectiveness(Attack attack, Type attackType, List<Holder<Type>> enemyTypes, boolean inverse) {
        if (attack != null && attack.isAttack(AttackRegistry.FREEZE_DRY)) {
            double mult = 1.0;
            for (Holder<Type> typeHolder : enemyTypes) {
                if (typeHolder.is(Type.WATER)) {
                    mult *= (inverse ? 0.5 : 2.0);
                } else {
                    mult *= attackType.getEffectivenessAgainst(typeHolder, inverse);
                }
            }
            return mult;
        }

        if (attack != null && attack.isAttack(AttackRegistry.THOUSAND_ARROWS)) {
            double mult = 1.0;
            for (Holder<Type> typeHolder : enemyTypes) {
                if (typeHolder.is(Type.FLYING)) {
                    mult *= 1.0;
                } else {
                    mult *= attackType.getEffectivenessAgainst(typeHolder, inverse);
                }
            }
            return mult;
        }

        return attackType.getTotalEffectiveness(enemyTypes, inverse);
    }

    /**
     * Formats the displayed effectiveness text with clear language and exact multiplier (e.g. 2x, 4x, 0.5x).
     */
    public static Component formatDisplayName(Effectiveness eff, Attack attack) {
        if (!EffectivenessConfig.CONFIG.showMultiplier.get()) {
            return eff.displayName();
        }

        return switch (eff) {
            case MAX -> Component.translatable("pixelmoneffectiveness.max");
            case SUPER -> Component.translatable("pixelmoneffectiveness.super");
            case NORMAL -> Component.translatable("pixelmoneffectiveness.normal");
            case NOT -> Component.translatable("pixelmoneffectiveness.not");
            case BARELY -> Component.translatable("pixelmoneffectiveness.barely");
            case NONE -> Component.translatable("pixelmoneffectiveness.none");
        };
    }
}
