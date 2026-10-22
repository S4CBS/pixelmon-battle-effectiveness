package com.pixelmoneffectiveness.client.battle;

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
import com.pixelmonmod.pixelmon.battles.status.StatusType;
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
 * Accounts in real time for:
 * - Terastallization & active type overrides
 * - Tera Raid shields & Max Raid shields
 * - Weather & Terrain damage multipliers
 * - Attacker status ailments (Burn halves physical attack)
 * - Ability immunities & special move matchups
 * - Dynamic state-hash cache invalidation
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
        public final String immunityReason;
        public final String enemyTeraTypeName;
        public final int shields;
        public final int maxShields;
        public final String weatherName;
        public final String terrainName;
        public final boolean isAttackerBurned;
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
            String immunityReason,
            String enemyTeraTypeName,
            int shields,
            int maxShields,
            String weatherName,
            String terrainName,
            boolean isAttackerBurned,
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
            this.immunityReason = immunityReason;
            this.enemyTeraTypeName = enemyTeraTypeName;
            this.shields = shields;
            this.maxShields = maxShields;
            this.weatherName = weatherName;
            this.terrainName = terrainName;
            this.isAttackerBurned = isAttackerBurned;
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

            if (enemyTeraTypeName != null && !enemyTeraTypeName.isEmpty()) {
                lines.add(Component.literal("§d✦ Тера-тип цели: §f" + enemyTeraTypeName + " §7(заменяет базовые типы!)"));
            }

            if (shields > 0) {
                lines.add(Component.literal("§5🛡 Щит рейда: §e" + shields + "/" + maxShields + " §7(урон снижен до 20% без Терастала)"));
            }

            if (weatherName != null) {
                lines.add(Component.literal("§e☀️ Погода: §f" + weatherName));
            }

            if (terrainName != null) {
                lines.add(Component.literal("§b⚡ Поле боя: §f" + terrainName));
            }

            if (isAttackerBurned) {
                lines.add(Component.literal("§c🔥 Ожог: §7физический урон снижен на 50%"));
            }

            if (isStatus) {
                lines.add(Component.literal("§7Категория: §eСтатусная (без прямого урона)"));
                lines.add(Component.literal("§7Точность: §f" + (accuracy > 0 ? accuracy + "%" : "—") +
                    " §7| PP: §f" + attack.getPP() + "/" + attack.getMaxPP()));
                if (isImmune || effectiveness == 0.0) {
                    lines.add(Component.literal("§cЦель невосприимчива: " + (immunityReason != null ? immunityReason : "0x") + "!"));
                }
                return lines;
            }

            if (isImmune) {
                lines.add(Component.literal("§c💥 НЕ ДЕЙСТВУЕТ (0x УРОН)!"));
                if (immunityReason != null && !immunityReason.isEmpty()) {
                    lines.add(Component.literal("§cПричина: §f" + immunityReason));
                }
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

    // Dynamic state hash for real-time reactivity without stale caching
    private static long cachedStateHash = Long.MIN_VALUE;
    private static Attack cachedTopAttack = null;
    private static final Map<Attack, BattleDamageResult> cache = new HashMap<>();

    public static synchronized BattleDamageResult getDamageResult(Attack attack) {
        if (attack == null || attack.getMove() == null) return null;

        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm == null) return null;

        PixelmonClientData activeOur = bm.getCurrentPokemon();
        PixelmonClientData activeEnemy = PixelmonEffectivenessHelper.getActiveEnemy(bm);
        if (activeOur == null || activeEnemy == null) return null;

        Pokemon partyPoke = getPartyPokemon(activeOur.pokemonUUID);
        long currentStateHash = computeStateHash(activeOur, partyPoke, activeEnemy, bm);

        boolean cacheValid = (currentStateHash == cachedStateHash);

        if (cacheValid && cache.containsKey(attack)) {
            return cache.get(attack);
        }

        if (!cacheValid) {
            cache.clear();
            cachedStateHash = currentStateHash;

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
        boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);
        BattleDamageResult res = calculateDamage(attack, activeOur, partyPoke, activeEnemy, inverse, cachedTopAttack);
        cache.put(attack, res);
        return res;
    }

    private static long computeStateHash(
        PixelmonClientData activeOur,
        Pokemon partyPoke,
        PixelmonClientData activeEnemy,
        ClientBattleManager bm
    ) {
        long h = 17;
        if (activeOur != null) {
            h = 31 * h + (activeOur.pokemonUUID != null ? activeOur.pokemonUUID.hashCode() : 0);
            h = 31 * h + (activeOur.health != null ? Double.doubleToLongBits(activeOur.health.get()) : 0);
            h = 31 * h + (activeOur.terastallize ? 1 : 0);
            h = 31 * h + (activeOur.getTeraTypeColor() != null ? activeOur.getTeraTypeColor().getRGB() : 0);
            h = 31 * h + activeOur.status;
            h = 31 * h + (activeOur.form != null ? activeOur.form.hashCode() : 0);
            if (activeOur.moveset != null) {
                for (int i = 0; i < activeOur.moveset.size(); i++) {
                    Attack a = activeOur.moveset.get(i);
                    if (a != null) {
                        h = 31 * h + a.getPP();
                        h = 31 * h + (a.getDisabled() ? 1 : 0);
                    }
                }
            }
        }
        if (partyPoke != null) {
            h = 31 * h + (PixelmonEffectivenessHelper.isTerastallized(activeOur, partyPoke) ? 1 : 0);
        }
        if (activeEnemy != null) {
            h = 31 * h + (activeEnemy.pokemonUUID != null ? activeEnemy.pokemonUUID.hashCode() : 0);
            h = 31 * h + (activeEnemy.health != null ? Double.doubleToLongBits(activeEnemy.health.get()) : 0);
            h = 31 * h + activeEnemy.shields;
            h = 31 * h + activeEnemy.maxShields;
            h = 31 * h + (activeEnemy.terastallize ? 1 : 0);
            h = 31 * h + (activeEnemy.getTeraTypeColor() != null ? activeEnemy.getTeraTypeColor().getRGB() : 0);
            h = 31 * h + activeEnemy.status;
            h = 31 * h + (activeEnemy.form != null ? activeEnemy.form.hashCode() : 0);
            List<Holder<Type>> enemyTypes = activeEnemy.getTypes();
            if (enemyTypes != null) {
                for (Holder<Type> t : enemyTypes) {
                    h = 31 * h + (t != null && t.unwrapKey().isPresent() ? t.unwrapKey().get().hashCode() : 0);
                }
            }
        }
        if (bm != null) {
            h = 31 * h + bm.battleTurn;
            h = 31 * h + (bm.weather != null ? bm.weather.ordinal() : -1);
            h = 31 * h + (bm.terrain != null ? bm.terrain.ordinal() : -1);
            h = 31 * h + (bm.terastallize != null ? bm.terastallize.hashCode() : 0);
        }
        return h;
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
            if (res == null || res.isImmune || res.effectiveness <= 0.0 || res.damageAvg <= 0) continue;

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

        List<Holder<Type>> enemyTypes = PixelmonEffectivenessHelper.getEffectiveDefenderTypes(enemy);
        Holder<Type> enemyTeraHolder = PixelmonEffectivenessHelper.isTerastallized(enemy)
            ? PixelmonEffectivenessHelper.resolveTeraType(enemy, null)
            : null;
        String enemyTeraTypeName = (enemyTeraHolder != null && enemyTeraHolder.value() != null)
            ? enemyTeraHolder.value().name().getString()
            : null;

        Type attackType = attack.getType() != null ? attack.getType().value() : null;
        double eff = (attackType != null && !enemyTypes.isEmpty())
            ? PixelmonEffectivenessHelper.calculateEffectiveness(attack, attackType, enemyTypes, inverse, enemy)
            : 1.0;

        String immunityReason = (eff == 0.0)
            ? PixelmonEffectivenessHelper.getImmunityReason(attack, attackType, enemyTypes, enemy)
            : null;

        int shields = enemy.shields;
        int maxShields = enemy.maxShields;

        ClientBattleManager bm = ClientProxy.battleManager;
        StatusType weather = (bm != null) ? bm.weather : null;
        StatusType terrain = (bm != null) ? bm.terrain : null;
        String weatherName = formatWeatherName(weather);
        String terrainName = formatTerrainName(terrain);

        String moveId = (move != null && move.getRegistryKey() != null)
            ? move.getRegistryKey().replace("pixelmon:", "").toLowerCase()
            : "";

        // Attacker Burn check (Burn halves physical attack damage unless Facade)
        boolean isAttackerBurned = (partyPoke != null && partyPoke.getStatus() != null && partyPoke.getStatus().type == StatusType.Burn) ||
                                   (clientPoke != null && clientPoke.status == StatusType.Burn.ordinal());

        // Status moves handling
        if (cat == AttackCategory.STATUS) {
            boolean statusBlockedByShield = (shields > 0);
            boolean isImmuneStatus = (eff == 0.0) || statusBlockedByShield;
            String statusReason = statusBlockedByShield
                ? "Щит рейда полностью блокирует любые статусные атаки!"
                : immunityReason;

            return new BattleDamageResult(
                attack, moveName, categoryName, typeName, 0, accuracy, eff, 1.0,
                true, isImmuneStatus, statusReason, enemyTeraTypeName, shields, maxShields,
                weatherName, terrainName, false, 0, 0, 0, 0, 0, 0, false, false, false
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

        // Sandstorm boosts Rock-type Sp.Def by 50%
        if (weather == StatusType.Sandstorm) {
            for (Holder<Type> th : enemyTypes) {
                if (th.is(Type.ROCK)) {
                    enemySpDef = (int) Math.round(enemySpDef * 1.5);
                    break;
                }
            }
        }

        // Snow boosts Ice-type Defense by 50%
        if (weather == StatusType.Snow) {
            for (Holder<Type> th : enemyTypes) {
                if (th.is(Type.ICE)) {
                    enemyDef = (int) Math.round(enemyDef * 1.5);
                    break;
                }
            }
        }

        // Check immunity (0x effectiveness)
        if (eff == 0.0) {
            return new BattleDamageResult(
                attack, moveName, categoryName, typeName, basePower, accuracy, 0.0, 1.0,
                false, true, immunityReason, enemyTeraTypeName, shields, maxShields,
                weatherName, terrainName, isAttackerBurned, 0, 0, 0, 0, 0, 0, false, false, false
            );
        }

        // Determine A (attack) and D (defense)
        int aVal;
        int dVal;

        if (moveId.equals("photon_geyser")) {
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
            int effectivePower = basePower > 0 ? basePower : 60;

            // STAB (Same-Type Attack Bonus) with Terastallization support
            boolean isOurTera = PixelmonEffectivenessHelper.isTerastallized(clientPoke, partyPoke);
            Holder<Type> ourTeraType = PixelmonEffectivenessHelper.resolveTeraType(clientPoke, partyPoke);

            boolean hasOriginalStab = false;
            if (attackerTypes != null && attack.getType() != null) {
                for (Holder<Type> t : attackerTypes) {
                    if (t != null && t.is(attack.getType().unwrapKey().orElse(null))) {
                        hasOriginalStab = true;
                        break;
                    }
                }
            }

            boolean hasTeraStab = (isOurTera && ourTeraType != null && attack.getType() != null &&
                                   ourTeraType.is(attack.getType().unwrapKey().orElse(null)));

            double stab;
            if (isOurTera && ourTeraType != null) {
                if (ourTeraType.is(Type.STELLAR)) {
                    stab = hasOriginalStab ? 2.0 : 1.2;
                } else if (hasOriginalStab && hasTeraStab) {
                    stab = 2.0;
                } else if (hasOriginalStab || hasTeraStab) {
                    stab = 1.5;
                } else {
                    stab = 1.0;
                }
            } else {
                stab = hasOriginalStab ? 1.5 : 1.0;
            }

            // Standard Damage Formula:
            // BaseDamage = ((((2 * Level / 5) + 2) * Power * A / D) / 50) + 2
            int levelFactor = (2 * attackerLevel) / 5 + 2;
            double statRatio = (double) aVal / Math.max(1, dVal);
            double baseDamage = Math.floor((Math.floor(levelFactor * effectivePower * statRatio) / 50.0)) + 2.0;

            // Weather multipliers
            if (weather == StatusType.Sunny) {
                if (attack.getType() != null && attack.getType().is(Type.FIRE)) {
                    baseDamage = Math.floor(baseDamage * 1.5);
                } else if (attack.getType() != null && attack.getType().is(Type.WATER)) {
                    baseDamage = Math.floor(baseDamage * 0.5);
                }
            } else if (weather == StatusType.Rainy) {
                if (attack.getType() != null && attack.getType().is(Type.WATER)) {
                    baseDamage = Math.floor(baseDamage * 1.5);
                } else if (attack.getType() != null && attack.getType().is(Type.FIRE)) {
                    baseDamage = Math.floor(baseDamage * 0.5);
                }
            }

            // Terrain multipliers
            if (terrain == StatusType.ElectricTerrain) {
                if (attack.getType() != null && attack.getType().is(Type.ELECTRIC)) {
                    baseDamage = Math.floor(baseDamage * 1.3);
                }
            } else if (terrain == StatusType.GrassyTerrain) {
                if (attack.getType() != null && attack.getType().is(Type.GRASS)) {
                    baseDamage = Math.floor(baseDamage * 1.3);
                } else if (moveId.equals("earthquake") || moveId.equals("bulldoze") || moveId.equals("magnitude")) {
                    baseDamage = Math.floor(baseDamage * 0.5);
                }
            } else if (terrain == StatusType.PsychicTerrain) {
                if (attack.getType() != null && attack.getType().is(Type.PSYCHIC)) {
                    baseDamage = Math.floor(baseDamage * 1.3);
                }
            } else if (terrain == StatusType.MistyTerrain) {
                if (attack.getType() != null && attack.getType().is(Type.DRAGON)) {
                    baseDamage = Math.floor(baseDamage * 0.5);
                }
            }

            // Burn halves physical damage
            if (isAttackerBurned && cat == AttackCategory.PHYSICAL && !moveId.equals("facade")) {
                baseDamage = Math.floor(baseDamage * 0.5);
            }

            double mod = stab * eff;
            dmgMin = Math.max(1, (int) Math.floor(baseDamage * mod * 0.85));
            dmgMax = Math.max(1, (int) Math.floor(baseDamage * mod * 1.00));

            // Tera Raid Shield damage reduction
            if (shields > 0) {
                double shieldMod;
                if (isOurTera) {
                    shieldMod = hasTeraStab ? 0.75 : 0.35;
                } else {
                    shieldMod = 0.20;
                }
                dmgMin = Math.max(1, (int) Math.floor(dmgMin * shieldMod));
                dmgMax = Math.max(1, (int) Math.floor(dmgMax * shieldMod));
            }
        }

        int dmgAvg = (dmgMin + dmgMax) / 2;

        int pctMin = Math.min(100, (int) Math.round(((double) dmgMin / enemyMaxHp) * 100.0));
        int pctMax = Math.min(100, (int) Math.round(((double) dmgMax / enemyMaxHp) * 100.0));
        int pctAvg = Math.min(100, (int) Math.round(((double) dmgAvg / enemyMaxHp) * 100.0));

        boolean isGuaranteedKO = (dmgMin >= enemyCurrentHp);
        boolean isOneHitKO = (dmgMax >= enemyCurrentHp);
        boolean isTop = (topAttack != null && topAttack == attack);

        // Final STAB display value
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
            false, false, null, enemyTeraTypeName, shields, maxShields,
            weatherName, terrainName, isAttackerBurned,
            dmgMin, dmgMax, dmgAvg, pctMin, pctMax, pctAvg,
            isOneHitKO, isGuaranteedKO, isTop
        );
    }

    private static String formatWeatherName(StatusType weather) {
        if (weather == null || weather == StatusType.None) return null;
        return switch (weather) {
            case Sunny -> "Ясно / Солнце (+50% Огонь, -50% Вода)";
            case Rainy -> "Дождь (+50% Вода, -50% Огонь)";
            case Sandstorm -> "Песчаная буря (+50% Спец.Защита Камня)";
            case Snow -> "Снегопад (+50% Защита Льда)";
            case Hail -> "Град";
            default -> weather.name();
        };
    }

    private static String formatTerrainName(StatusType terrain) {
        if (terrain == null || terrain == StatusType.None) return null;
        return switch (terrain) {
            case ElectricTerrain -> "Электрическое поле (+30% Электро, защита от сна)";
            case GrassyTerrain -> "Травяное поле (+30% Трава, -50% Земля)";
            case PsychicTerrain -> "Психическое поле (+30% Психо, защита от приоритета)";
            case MistyTerrain -> "Туманное поле (-50% Дракон, защита от статусов)";
            default -> terrain.name();
        };
    }

    private static int estimateStat(int base, int level) {
        return Math.max(1, ((2 * base + 31) * level) / 100 + 5);
    }

    private static int estimateHp(int base, int level) {
        if (base == 1) return 1; // Shedinja
        return Math.max(1, ((2 * base + 31) * level) / 100 + level + 10);
    }
}
