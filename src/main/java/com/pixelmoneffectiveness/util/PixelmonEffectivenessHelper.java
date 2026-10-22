package com.pixelmoneffectiveness.util;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.battles.attack.AttackRegistry;
import com.pixelmonmod.pixelmon.api.context.ContextKeys;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.ability.abilities.Overcoat;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.Effectiveness;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.BattleScreen;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import com.pixelmonmod.pixelmon.init.registry.PixelmonRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.awt.Color;
import java.util.*;

public class PixelmonEffectivenessHelper {

    private static final Map<Integer, ResourceKey<Type>> COLOR_TO_TYPE_KEY = new HashMap<>();
    static {
        COLOR_TO_TYPE_KEY.put(new Color(145, 160, 24).getRGB(), Type.BUG);
        COLOR_TO_TYPE_KEY.put(new Color(79, 65, 62).getRGB(), Type.DARK);
        COLOR_TO_TYPE_KEY.put(new Color(78, 97, 227).getRGB(), Type.DRAGON);
        COLOR_TO_TYPE_KEY.put(new Color(252, 192, 4).getRGB(), Type.ELECTRIC);
        COLOR_TO_TYPE_KEY.put(new Color(240, 115, 238).getRGB(), Type.FAIRY);
        COLOR_TO_TYPE_KEY.put(new Color(250, 130, 3).getRGB(), Type.FIGHTING);
        COLOR_TO_TYPE_KEY.put(new Color(231, 66, 66).getRGB(), Type.FIRE);
        COLOR_TO_TYPE_KEY.put(new Color(129, 184, 240).getRGB(), Type.FLYING);
        COLOR_TO_TYPE_KEY.put(new Color(112, 65, 111).getRGB(), Type.GHOST);
        COLOR_TO_TYPE_KEY.put(new Color(71, 181, 39).getRGB(), Type.GRASS);
        COLOR_TO_TYPE_KEY.put(new Color(146, 81, 30).getRGB(), Type.GROUND);
        COLOR_TO_TYPE_KEY.put(new Color(60, 219, 252).getRGB(), Type.ICE);
        COLOR_TO_TYPE_KEY.put(new Color(128, 128, 128).getRGB(), Type.MYSTERY);
        COLOR_TO_TYPE_KEY.put(new Color(138, 140, 139).getRGB(), Type.NORMAL);
        COLOR_TO_TYPE_KEY.put(new Color(147, 63, 200).getRGB(), Type.POISON);
        COLOR_TO_TYPE_KEY.put(new Color(240, 66, 121).getRGB(), Type.PSYCHIC);
        COLOR_TO_TYPE_KEY.put(new Color(170, 165, 128).getRGB(), Type.ROCK);
        COLOR_TO_TYPE_KEY.put(new Color(96, 162, 186).getRGB(), Type.STEEL);
        COLOR_TO_TYPE_KEY.put(new Color(237, 153, 180).getRGB(), Type.STELLAR);
        COLOR_TO_TYPE_KEY.put(new Color(41, 129, 239).getRGB(), Type.WATER);
    }

    public static boolean isTerastallized(PixelmonClientData data) {
        if (data == null) return false;
        if (data.terastallize) return true;
        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm != null && bm.terastallize != null && data.pokemonUUID != null && bm.terastallize.equals(data.pokemonUUID)) {
            return true;
        }
        return false;
    }

    public static boolean isTerastallized(UUID uuid) {
        if (uuid == null) return false;
        ClientBattleManager bm = ClientProxy.battleManager;
        return bm != null && bm.terastallize != null && bm.terastallize.equals(uuid);
    }

    public static boolean isTerastallized(PixelmonClientData data, Pokemon partyPoke) {
        if (data != null && isTerastallized(data)) return true;
        if (partyPoke != null && isTerastallized(partyPoke.getUUID())) return true;
        return false;
    }

    public static Holder<Type> resolveTeraType(PixelmonClientData data, Pokemon partyPoke) {
        if (partyPoke != null && partyPoke.getTeraType() != null) {
            return partyPoke.getTeraType();
        }
        if (data == null) return null;
        Color c = data.getTeraTypeColor();
        if (c == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        try {
            var optReg = mc.level.registryAccess().registry(PixelmonRegistry.TYPE_REGISTRY);
            if (!optReg.isPresent()) return null;
            var reg = optReg.get();

            ResourceKey<Type> resKey = COLOR_TO_TYPE_KEY.get(c.getRGB());
            if (resKey != null) {
                return reg.getHolderOrThrow(resKey);
            }

            ResourceKey<Type> bestKey = null;
            int minDistance = Integer.MAX_VALUE;
            for (Map.Entry<Integer, ResourceKey<Type>> entry : COLOR_TO_TYPE_KEY.entrySet()) {
                Color sc = new Color(entry.getKey());
                int dR = sc.getRed() - c.getRed();
                int dG = sc.getGreen() - c.getGreen();
                int dB = sc.getBlue() - c.getBlue();
                int dist = dR * dR + dG * dG + dB * dB;
                if (dist < minDistance) {
                    minDistance = dist;
                    bestKey = entry.getValue();
                }
            }
            if (bestKey != null && minDistance < 500) {
                return reg.getHolderOrThrow(bestKey);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static List<Holder<Type>> getEffectiveDefenderTypes(PixelmonClientData enemy) {
        if (enemy == null) return Collections.emptyList();
        if (isTerastallized(enemy)) {
            Holder<Type> tera = resolveTeraType(enemy, null);
            if (tera != null) {
                if (!tera.is(Type.STELLAR)) {
                    return Collections.singletonList(tera);
                }
            }
        }
        List<Holder<Type>> types = enemy.getTypes();
        return (types != null) ? types : Collections.emptyList();
    }

    public static boolean hasGuaranteedAbility(PixelmonClientData enemy, String abilityName) {
        if (enemy == null || enemy.species == null) return false;
        Stats stats = enemy.getBaseStats();
        if (stats == null || stats.getAbilities() == null) return false;
        var all = stats.getAbilities().getAll();
        if (all == null || all.length == 0) return false;
        for (var ab : all) {
            if (ab == null || !ab.getName().equalsIgnoreCase(abilityName)) {
                return false;
            }
        }
        return true;
    }

    public static boolean shouldShowEffectiveness(Attack attack) {
        if (!EffectivenessConfig.CONFIG.enabled.get()) {
            return false;
        }

        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm == null || bm.displayedEnemyPokemon == null || bm.displayedEnemyPokemon.length == 0) {
            return false;
        }

        PixelmonClientData enemy = getActiveEnemy(bm);
        List<Holder<Type>> enemyTypes = getEffectiveDefenderTypes(enemy);
        if (enemyTypes == null || enemyTypes.isEmpty()) {
            return false;
        }

        if (attack == null || attack.getMove() == null) {
            return false;
        }

        if (!attack.getMove().targetsOpponents()) {
            return false;
        }

        boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);
        Type attackType = (attack.getType() != null) ? attack.getType().value() : null;
        if (attackType == null) return false;

        double eff = calculateEffectiveness(attack, attackType, enemyTypes, inverse, enemy);

        if (attack.getAttackCategory() == AttackCategory.STATUS) {
            if (!EffectivenessConfig.CONFIG.showForStatusMoves.get()) {
                return eff == 0.0;
            }
        }

        if (!EffectivenessConfig.CONFIG.showForNormalMoves.get()) {
            if (eff == 1.0) {
                return false;
            }
        }

        return true;
    }

    public static PixelmonClientData getActiveEnemy(ClientBattleManager bm) {
        if (bm == null || bm.displayedEnemyPokemon == null || bm.displayedEnemyPokemon.length == 0) {
            return null;
        }

        if (Minecraft.getInstance().screen instanceof BattleScreen battleScreen) {
            for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
                if (enemy != null && enemy.pokemonUUID != null && battleScreen.isTargeted(enemy.pokemonUUID)) {
                    if (isAlive(enemy)) {
                        return enemy;
                    }
                }
            }
        }

        for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
            if (isAlive(enemy)) {
                return enemy;
            }
        }

        for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
            if (enemy != null) {
                return enemy;
            }
        }

        return null;
    }

    private static boolean isAlive(PixelmonClientData poke) {
        return poke != null && poke.health != null && poke.health.get() > 0;
    }

    public static double calculateEffectiveness(Attack attack, Type attackType, List<Holder<Type>> enemyTypes, boolean inverse) {
        return calculateEffectiveness(attack, attackType, enemyTypes, inverse, null);
    }

    public static double calculateEffectiveness(Attack attack, Type attackType, List<Holder<Type>> enemyTypes, boolean inverse, PixelmonClientData enemy) {
        if (attackType == null || enemyTypes == null || enemyTypes.isEmpty()) {
            return 1.0;
        }

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

        if (attack != null && attack.isAttack(AttackRegistry.FLYING_PRESS)) {
            double fightingEff = attackType.getTotalEffectiveness(enemyTypes, inverse);
            double flyingEff = 1.0;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    var reg = mc.level.registryAccess().registry(PixelmonRegistry.TYPE_REGISTRY);
                    if (reg.isPresent()) {
                        Holder<Type> flyingHolder = reg.get().getHolderOrThrow(Type.FLYING);
                        flyingEff = flyingHolder.value().getTotalEffectiveness(enemyTypes, inverse);
                    }
                }
            } catch (Throwable ignored) {}
            return fightingEff * flyingEff;
        }

        if (attack != null && attack.isAttack(AttackRegistry.THUNDER_WAVE)) {
            for (Holder<Type> th : enemyTypes) {
                if (th.is(Type.GROUND) || th.is(Type.ELECTRIC)) {
                    return 0.0;
                }
            }
        }

        if (attack != null && Overcoat.isPowderMove(attack)) {
            for (Holder<Type> th : enemyTypes) {
                if (th.is(Type.GRASS)) {
                    return 0.0;
                }
            }
        }

        if (enemy != null) {
            if (attack != null && attack.getType() != null && attack.getType().is(Type.GROUND) && hasGuaranteedAbility(enemy, "levitate")) {
                if (!attack.isAttack(AttackRegistry.THOUSAND_ARROWS)) {
                    return 0.0;
                }
            }

            if (attack != null && attack.getType() != null && attack.getType().is(Type.GROUND) && hasGuaranteedAbility(enemy, "eartheater")) {
                return 0.0;
            }

            if (attack != null && attack.getType() != null && attack.getType().is(Type.ELECTRIC) &&
                (hasGuaranteedAbility(enemy, "voltabsorb") ||
                 hasGuaranteedAbility(enemy, "motordrive") ||
                 hasGuaranteedAbility(enemy, "lightningrod"))) {
                return 0.0;
            }

            if (attack != null && attack.getType() != null && attack.getType().is(Type.WATER) &&
                (hasGuaranteedAbility(enemy, "waterabsorb") ||
                 hasGuaranteedAbility(enemy, "stormdrain") ||
                 hasGuaranteedAbility(enemy, "dryskin"))) {
                return 0.0;
            }

            if (attack != null && attack.getType() != null && attack.getType().is(Type.FIRE) &&
                (hasGuaranteedAbility(enemy, "flashfire") ||
                 hasGuaranteedAbility(enemy, "wellbakedbody"))) {
                return 0.0;
            }

            if (attack != null && attack.getType() != null && attack.getType().is(Type.GRASS) && hasGuaranteedAbility(enemy, "sapsipper")) {
                return 0.0;
            }

            if (attack != null && attack.getAttackCategory() == AttackCategory.STATUS && hasGuaranteedAbility(enemy, "goodasgold")) {
                return 0.0;
            }

            if (hasGuaranteedAbility(enemy, "wonderguard")) {
                double rawEff = attackType.getTotalEffectiveness(enemyTypes, inverse);
                if (rawEff <= 1.0) {
                    return 0.0;
                }
            }
        }

        return attackType.getTotalEffectiveness(enemyTypes, inverse);
    }

    public static String getImmunityReason(Attack attack, Type attackType, List<Holder<Type>> enemyTypes, PixelmonClientData enemy) {
        if (attack != null && attack.isAttack(AttackRegistry.THUNDER_WAVE)) {
            for (Holder<Type> th : enemyTypes) {
                if (th.is(Type.GROUND)) return "Земляной тип цели невосприимчив к Грозовой Волне";
                if (th.is(Type.ELECTRIC)) return "Электрический тип цели невосприимчив к параличу";
            }
        }
        if (attack != null && Overcoat.isPowderMove(attack)) {
            for (Holder<Type> th : enemyTypes) {
                if (th.is(Type.GRASS)) return "Травяной тип цели невосприимчив к спорам и пыльце";
            }
        }
        if (attack != null && attack.getType() != null) {
            Holder<Type> atHolder = attack.getType();
            if (atHolder.is(Type.PSYCHIC)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.DARK)) return "Тёмный тип цели полностью блокирует Психические атаки (0x)";
                }
            }
            if (atHolder.is(Type.NORMAL) || atHolder.is(Type.FIGHTING)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.GHOST)) return "Призрачный тип цели невосприимчив к Нормальным и Боевым атакам (0x)";
                }
            }
            if (atHolder.is(Type.GHOST)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.NORMAL)) return "Нормальный тип цели невосприимчив к Призрачным атакам (0x)";
                }
            }
            if (atHolder.is(Type.GROUND)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.FLYING)) return "Летающий тип цели невосприимчив к Земляным атакам (0x)";
                }
                if (enemy != null && hasGuaranteedAbility(enemy, "levitate")) {
                    return "Способность цели «Левитация» даёт иммунитет к Земляным атакам";
                }
                if (enemy != null && hasGuaranteedAbility(enemy, "eartheater")) {
                    return "Способность цели «Пожиратель Земли» поглощает Земляные атаки";
                }
            }
            if (atHolder.is(Type.ELECTRIC)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.GROUND)) return "Земляной тип цели невосприимчив к Электрическим атакам (0x)";
                }
                if (enemy != null && (hasGuaranteedAbility(enemy, "voltabsorb") || hasGuaranteedAbility(enemy, "lightningrod") || hasGuaranteedAbility(enemy, "motordrive"))) {
                    return "Способность цели поглощает Электрические атаки";
                }
            }
            if (atHolder.is(Type.DRAGON)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.FAIRY)) return "Волшебный тип цели невосприимчив к Драконьим атакам (0x)";
                }
            }
            if (atHolder.is(Type.POISON)) {
                for (Holder<Type> th : enemyTypes) {
                    if (th.is(Type.STEEL)) return "Стальной тип цели невосприимчив к Ядовитым атакам (0x)";
                }
            }
        }
        if (enemy != null && hasGuaranteedAbility(enemy, "wonderguard")) {
            return "Способность цели «Чудо-строение» (Wonder Guard) блокирует любой не суперэффективный урон";
        }
        return "Цель полностью невосприимчива к этой атаке (0x урон)";
    }

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
