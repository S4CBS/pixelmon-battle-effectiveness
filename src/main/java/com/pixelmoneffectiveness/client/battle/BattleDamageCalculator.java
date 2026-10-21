package com.pixelmoneffectiveness.client.battle;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmoneffectiveness.util.PixelmonEffectivenessHelper;
import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.context.ContextKeys;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calculates estimated damage, HP percentages, and determines the most effective attack
 * for the player's active Pokemon against the active opponent in battle.
 */
public class BattleDamageCalculator {

    public static class BattleDamageResult {
        public final Attack attack;
        public final String moveName;
        public final String categoryName;
        public final String typeName;
        public final int basePower;
        public final int accuracy;
        public final double effectiveness;
        public final double stab;
        public final boolean isStatus;
        public final boolean isImmune;
        public final int damageMin;
        public final int damageMax;
        public final int damageAvg;
        public final int percentMin;
        public final int percentMax;
        public final int percentAvg;
        public final boolean isOneHitKO;
        public final boolean isGuaranteedKO;
        public final boolean isTop;

        public BattleDamageResult(
            Attack attack,
            String moveName,
            String categoryName,
            String typeName,
            int basePower,
            int accuracy,
            double effectiveness,
            double stab,
            boolean isStatus,
            boolean isImmune,
            int damageMin,
            int damageMax,
            int damageAvg,
            int percentMin,
            int percentMax,
            int percentAvg,
            boolean isOneHitKO,
            boolean isGuaranteedKO,
            boolean isTop
        ) {
            this.attack = attack;
            this.moveName = moveName;
            this.categoryName = categoryName;
            this.typeName = typeName;
            this.basePower = basePower;
            this.accuracy = accuracy;
            this.effectiveness = effectiveness;
            this.stab = stab;
            this.isStatus = isStatus;
            this.isImmune = isImmune;
            this.damageMin = damageMin;
            this.damageMax = damageMax;
            this.damageAvg = damageAvg;
            this.percentMin = percentMin;
            this.percentMax = percentMax;
            this.percentAvg = percentAvg;
            this.isOneHitKO = isOneHitKO;
            this.isGuaranteedKO = isGuaranteedKO;
            this.isTop = isTop;
        }

        public List<Component> buildTooltip() {
            List<Component> lines = new ArrayList<>();

            if (isTop) {
                lines.add(Component.literal("§6★ САМАЯ ЭФФЕКТИВНАЯ АТАКА"));
            }

            lines.add(Component.literal("§f" + moveName + " §7(" + categoryName + " • " + typeName + ")"));

            if (isStatus) {
                lines.add(Component.literal("§7Категория: §eСтатусная (без прямого урона)"));
                lines.add(Component.literal("§7Точность: §f" + (accuracy > 0 ? accuracy + "%" : "—") +
                    " §7| PP: §f" + attack.getPP() + "/" + attack.getMaxPP()));
                if (effectiveness == 0.0) {
                    lines.add(Component.literal("§cЦель невосприимчива к статусу этого типа (0x)!"));
                }
                return lines;
            }

            if (isImmune) {
                lines.add(Component.literal("§cЦель невосприимчива к атаке (0x урон)!"));
                lines.add(Component.literal("§7Базовая сила: §f" + basePower +
                    " §7| Точность: §f" + (accuracy > 0 ? accuracy + "%" : "—") +
                    " §7| PP: §f" + attack.getPP() + "/" + attack.getMaxPP()));
                return lines;
            }

            lines.add(Component.literal("§7Расчётный урон: §e" + damageMin + " - " + damageMax +
                " §7(в среднем: §f" + damageAvg + "§7)"));

            if (isGuaranteedKO) {
                lines.add(Component.literal("§c💥 ГАРАНТИРОВАННЫЙ ВАНШОТ! §7(100%+ HP противника)"));
            } else if (isOneHitKO) {
                lines.add(Component.literal("§e💥 ШАНС ВАНШОТА: §f" + percentMin + "% - " + percentMax + "% §7HP"));
            } else {
                lines.add(Component.literal("§7Доля здоровья цели: §a" + percentMin + "% - " + percentMax + "% §7(средн. §f" + percentAvg + "%§7)"));
            }

            lines.add(Component.literal("§7Эффективность типа: " + formatEffDescription(effectiveness)));

            if (stab > 1.0) {
                int bonus = (int) Math.round((stab - 1.0) * 100);
                lines.add(Component.literal("§bБонус совпадения типа (STAB): §f+" + bonus + "%"));
            }

            lines.add(Component.literal("§7Базовая сила: §f" + basePower +
                " §7| Точность: §f" + (accuracy > 0 ? accuracy + "%" : "—") +
                " §7| PP: §f" + attack.getPP() + "/" + attack.getMaxPP()));

            return lines;
        }

        private String formatEffDescription(double eff) {
            if (eff >= 4.0) return "§aСверх-эффективно (x4.0)";
            if (eff >= 2.0) return "§aСуперэффективно (x2.0)";
            if (eff >= 1.0) return "§fНейтрально (x1.0)";
            if (eff >= 0.5) return "§cМалоэффективно (x0.5)";
            if (eff > 0.0) return "§cКрайне неэффективно (x0.25)";
            return "§cНе действует (0x)";
        }
    }

    // Cache to avoid recalculating within the same render tick
    private static UUID cachedOurUUID = null;
    private static UUID cachedEnemyUUID = null;
    private static double cachedEnemyHp = -1;
    private static Attack cachedTopAttack = null;
    private static final Map<Attack, BattleDamageResult> cache = new HashMap<>();

    public static synchronized BattleDamageResult getDamageResult(Attack attack) {
        if (attack == null || attack.getMove() == null) return null;

        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm == null) return null;

        PixelmonClientData activeOur = bm.getCurrentPokemon();
        PixelmonClientData activeEnemy = PixelmonEffectivenessHelper.getActiveEnemy(bm);
        if (activeOur == null || activeEnemy == null) return null;

        double currentEnemyHp = (activeEnemy.health != null) ? activeEnemy.health.get() : activeEnemy.maxHealth;
        boolean cacheValid = activeOur.pokemonUUID != null &&
            activeOur.pokemonUUID.equals(cachedOurUUID) &&
            activeEnemy.pokemonUUID != null &&
            activeEnemy.pokemonUUID.equals(cachedEnemyUUID) &&
            Math.abs(currentEnemyHp - cachedEnemyHp) < 0.01;

        if (cacheValid && cache.containsKey(attack)) {
            return cache.get(attack);
        }

        if (!cacheValid) {
            cache.clear();
            cachedOurUUID = activeOur.pokemonUUID;
            cachedEnemyUUID = activeEnemy.pokemonUUID;
            cachedEnemyHp = currentEnemyHp;

            Pokemon partyPoke = getPartyPokemon(activeOur.pokemonUUID);
            boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);
            cachedTopAttack = findTopAttack(activeOur, partyPoke, activeEnemy, inverse);

            if (activeOur.moveset != null) {
                for (int i = 0; i < activeOur.moveset.size(); i++) {
                    Attack a = activeOur.moveset.get(i);
                    if (a != null && a.getMove() != null) {
                        BattleDamageResult res = calculateDamage(a, activeOur, partyPoke, activeEnemy, inverse, cachedTopAttack);
                        cache.put(a, res);
                    }
                }
            }
        }

        if (cache.containsKey(attack)) {
            return cache.get(attack);
        }

        // Fallback for single query
        Pokemon partyPoke = getPartyPokemon(activeOur.pokemonUUID);
        boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);
        BattleDamageResult res = calculateDamage(attack, activeOur, partyPoke, activeEnemy, inverse, cachedTopAttack);
        cache.put(attack, res);
        return res;
    }

    private static Pokemon getPartyPokemon(UUID uuid) {
        if (uuid == null) return null;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                var party = StorageProxy.getPartyNow(mc.player);
                if (party != null) {
                    return party.get(uuid);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Attack findTopAttack(
        PixelmonClientData activeOur,
        Pokemon partyPoke,
        PixelmonClientData activeEnemy,
        boolean inverse
    ) {
        if (activeOur == null || activeOur.moveset == null || activeEnemy == null) return null;

        Attack best = null;
        int maxAvgDmg = 0;

        for (int i = 0; i < activeOur.moveset.size(); i++) {
            Attack a = activeOur.moveset.get(i);
            if (a == null || a.getMove() == null) continue;
            if (a.getPP() <= 0 || a.getDisabled()) continue;
            if (a.getAttackCategory() == AttackCategory.STATUS) continue;

            BattleDamageResult res = calculateDamage(a, activeOur, partyPoke, activeEnemy, inverse, null);
            if (res.damageAvg > maxAvgDmg) {
                maxAvgDmg = res.damageAvg;
                best = a;
            }
        }
        return best;
    }

    private static BattleDamageResult calculateDamage(
        Attack attack,
        PixelmonClientData clientPoke,
        Pokemon partyPoke,
        PixelmonClientData enemy,
        boolean inverse,
        Attack topAttack
    ) {
        ImmutableAttack move = attack.getMove();
        String moveName = move != null ? move.getLocalizedName() : "Атака";
        AttackCategory cat = attack.getAttackCategory();

        String categoryName = switch (cat) {
            case PHYSICAL -> "Физическая";
            case SPECIAL -> "Специальная";
            case STATUS -> "Статусная";
        };

        String typeName = (attack.getType() != null && attack.getType().value() != null)
            ? attack.getType().value().name().getString()
            : "Тип";

        int basePower = move != null ? move.getBasePower() : 0;
        int accuracy = move != null ? move.getAccuracy() : 100;

        Type attackType = attack.getType() != null ? attack.getType().value() : null;
        double eff = (attackType != null && enemy.getTypes() != null)
            ? PixelmonEffectivenessHelper.calculateEffectiveness(attack, attackType, enemy.getTypes(), inverse)
            : 1.0;

        // Status moves deal 0 damage
        if (cat == AttackCategory.STATUS) {
            return new BattleDamageResult(
                attack, moveName, categoryName, typeName, 0, accuracy, eff, 1.0,
                true, eff == 0.0, 0, 0, 0, 0, 0, 0, false, false, false
            );
        }

        // Attacker stats & level
        int attackerLevel = partyPoke != null ? partyPoke.getPokemonLevel() : Math.max(1, clientPoke.level);
        List<Holder<Type>> attackerTypes = partyPoke != null ? partyPoke.getForm().getTypes() : clientPoke.getTypes();

        int atkStat = partyPoke != null ? partyPoke.getStat(BattleStatsType.ATTACK)
            : estimateStat(clientPoke.getBaseStats() != null ? clientPoke.getBaseStats().getBattleStats().getStat(BattleStatsType.ATTACK) : 80, attackerLevel);
        int spAtkStat = partyPoke != null ? partyPoke.getStat(BattleStatsType.SPECIAL_ATTACK)
            : estimateStat(clientPoke.getBaseStats() != null ? clientPoke.getBaseStats().getBattleStats().getStat(BattleStatsType.SPECIAL_ATTACK) : 80, attackerLevel);
        int defStat = partyPoke != null ? partyPoke.getStat(BattleStatsType.DEFENSE)
            : estimateStat(clientPoke.getBaseStats() != null ? clientPoke.getBaseStats().getBattleStats().getStat(BattleStatsType.DEFENSE) : 80, attackerLevel);

        // Defender stats & level
        int enemyLevel = Math.max(1, enemy.level);
        Stats enemyBase = enemy.species != null ? enemy.getBaseStats() : null;
        int enemyDefBase = (enemyBase != null && enemyBase.getBattleStats() != null) ? enemyBase.getBattleStats().getStat(BattleStatsType.DEFENSE) : 80;
        int enemySpDefBase = (enemyBase != null && enemyBase.getBattleStats() != null) ? enemyBase.getBattleStats().getStat(BattleStatsType.SPECIAL_DEFENSE) : 80;
        int enemyHpBase = (enemyBase != null && enemyBase.getBattleStats() != null) ? enemyBase.getBattleStats().getStat(BattleStatsType.HP) : 80;

        int enemyDef = estimateStat(enemyDefBase, enemyLevel);
        int enemySpDef = estimateStat(enemySpDefBase, enemyLevel);
        int enemyMaxHp = enemy.maxHealth > 0 ? enemy.maxHealth : estimateHp(enemyHpBase, enemyLevel);
        double enemyCurrentHp = (enemy.health != null && enemy.health.get() > 0) ? enemy.health.get() : enemyMaxHp;

        // Check immunity
        if (eff == 0.0) {
            return new BattleDamageResult(
                attack, moveName, categoryName, typeName, basePower, accuracy, 0.0, 1.0,
                false, true, 0, 0, 0, 0, 0, 0, false, false, false
            );
        }

        // Determine A (attack) and D (defense)
        int aVal;
        int dVal;

        String moveId = (move != null && move.getRegistryKey() != null)
            ? move.getRegistryKey().replace("pixelmon:", "").toLowerCase()
            : "";

        if (moveId.equals("photon_geyser")) {
            // Photon Geyser compares Attack vs Sp.Attack
            if (atkStat > spAtkStat) {
                aVal = atkStat;
                dVal = enemyDef;
            } else {
                aVal = spAtkStat;
                dVal = enemySpDef;
            }
        } else if (moveId.equals("psyshock") || moveId.equals("psystrike") || moveId.equals("secret_sword")) {
            aVal = spAtkStat;
            dVal = enemyDef;
        } else if (moveId.equals("body_press")) {
            aVal = defStat;
            dVal = enemyDef;
        } else if (moveId.equals("foul_play")) {
            aVal = estimateStat(enemyDefBase, enemyLevel);
            dVal = enemyDef;
        } else {
            if (cat == AttackCategory.PHYSICAL) {
                aVal = atkStat;
                dVal = enemyDef;
            } else {
                aVal = spAtkStat;
                dVal = enemySpDef;
            }
        }

        // Fixed damage moves
        int dmgMin;
        int dmgMax;

        if (moveId.equals("seismic_toss") || moveId.equals("night_shade")) {
            dmgMin = dmgMax = attackerLevel;
        } else if (moveId.equals("dragon_rage")) {
            dmgMin = dmgMax = 40;
        } else if (moveId.equals("sonic_boom")) {
            dmgMin = dmgMax = 20;
        } else if (moveId.equals("super_fang") || moveId.equals("natures_madness")) {
            dmgMin = dmgMax = Math.max(1, (int) (enemyCurrentHp / 2.0));
        } else {
            // Variable power estimate fallback
            int effectivePower = basePower > 0 ? basePower : 60;

            // STAB
            boolean hasStab = false;
            if (attackerTypes != null && attack.getType() != null) {
                for (Holder<Type> t : attackerTypes) {
                    if (t != null && t.is(attack.getType().unwrapKey().orElse(null))) {
                        hasStab = true;
                        break;
                    }
                }
            }
            double stab = hasStab ? 1.5 : 1.0;

            // Pokemon Gen 9 standard damage formula:
            // BaseDamage = ((((2 * Level / 5) + 2) * Power * A / D) / 50) + 2
            int levelFactor = (2 * attackerLevel) / 5 + 2;
            double statRatio = (double) aVal / Math.max(1, dVal);
            double baseDamage = Math.floor((Math.floor(levelFactor * effectivePower * statRatio) / 50.0)) + 2.0;

            double mod = stab * eff;
            dmgMin = Math.max(1, (int) Math.floor(baseDamage * mod * 0.85));
            dmgMax = Math.max(1, (int) Math.floor(baseDamage * mod * 1.00));
        }

        int dmgAvg = (dmgMin + dmgMax) / 2;

        int pctMin = Math.min(100, (int) Math.round(((double) dmgMin / enemyMaxHp) * 100.0));
        int pctMax = Math.min(100, (int) Math.round(((double) dmgMax / enemyMaxHp) * 100.0));
        int pctAvg = Math.min(100, (int) Math.round(((double) dmgAvg / enemyMaxHp) * 100.0));

        boolean isGuaranteedKO = (dmgMin >= enemyCurrentHp);
        boolean isOneHitKO = (dmgMax >= enemyCurrentHp);
        boolean isTop = (topAttack != null && topAttack == attack);

        // STAB calculation for result
        boolean hasStabFinal = false;
        if (attackerTypes != null && attack.getType() != null) {
            for (Holder<Type> t : attackerTypes) {
                if (t != null && t.is(attack.getType().unwrapKey().orElse(null))) {
                    hasStabFinal = true;
                    break;
                }
            }
        }
        double stabVal = hasStabFinal ? 1.5 : 1.0;

        return new BattleDamageResult(
            attack, moveName, categoryName, typeName, basePower, accuracy, eff, stabVal,
            false, false, dmgMin, dmgMax, dmgAvg, pctMin, pctMax, pctAvg,
            isOneHitKO, isGuaranteedKO, isTop
        );
    }

    private static int estimateStat(int base, int level) {
        return Math.max(1, ((2 * base + 31) * level) / 100 + 5);
    }

    private static int estimateHp(int base, int level) {
        if (base == 1) return 1; // Shedinja
        return Math.max(1, ((2 * base + 31) * level) / 100 + level + 10);
    }
}
