package com.pixelmoneffectiveness.client.battle;

import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores evaluation data for a candidate Pokemon against an opponent (battle enemy or raid boss).
 */
public class PokemonMatchupRating {

    public final String pokemonName;
    public final UUID uuid;
    public final int level;
    public final double currentHp;
    public final int maxHp;
    public final boolean isFainted;
    public final boolean isEgg;

    public final Attack bestAttack;
    public final String bestAttackName;
    public final Type bestAttackType;
    public final double bestMultiplier;
    public final int bestBasePower;
    public final boolean hasStab;
    public final double attackScore;

    public final double defenseMultiplier;
    public final double totalScore;
    public boolean isMostEffective;

    public PokemonMatchupRating(
        String pokemonName,
        UUID uuid,
        int level,
        double currentHp,
        int maxHp,
        boolean isFainted,
        boolean isEgg,
        Attack bestAttack,
        String bestAttackName,
        Type bestAttackType,
        double bestMultiplier,
        int bestBasePower,
        boolean hasStab,
        double attackScore,
        double defenseMultiplier,
        double totalScore
    ) {
        this.pokemonName = pokemonName;
        this.uuid = uuid;
        this.level = level;
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.isFainted = isFainted;
        this.isEgg = isEgg;
        this.bestAttack = bestAttack;
        this.bestAttackName = bestAttackName;
        this.bestAttackType = bestAttackType;
        this.bestMultiplier = bestMultiplier;
        this.bestBasePower = bestBasePower;
        this.hasStab = hasStab;
        this.attackScore = attackScore;
        this.defenseMultiplier = defenseMultiplier;
        this.totalScore = totalScore;
        this.isMostEffective = false;
    }

    public String getMultiplierString() {
        if (bestMultiplier >= 4.0) return "4.0x Макс";
        if (bestMultiplier >= 2.0) return "2.0x Супер";
        if (bestMultiplier == 1.0) return "1.0x Норм";
        if (bestMultiplier > 0.0 && bestMultiplier < 1.0) {
            return String.format(Locale.ROOT, "%.1fx Слабо", bestMultiplier);
        }
        return "0x Не действует";
    }

    public int getMultiplierColor() {
        if (bestMultiplier >= 4.0) return 0xFF55FF55; // Bright Green
        if (bestMultiplier >= 2.0) return 0xFF55FF55; // Green
        if (bestMultiplier == 1.0) return 0xFFFFFFFF; // White
        if (bestMultiplier > 0.0 && bestMultiplier < 1.0) return 0xFFFF7777; // Reddish
        return 0xFFAAAAAA; // Gray
    }

    public String getFormattedMultiplierWithColor() {
        if (bestMultiplier >= 4.0) return "§a4.0x (Максимально эффективно)";
        if (bestMultiplier >= 2.0) return "§a2.0x (Супер-эффективно)";
        if (bestMultiplier == 1.0) return "§f1.0x (Обычный урон)";
        if (bestMultiplier > 0.0 && bestMultiplier < 1.0) {
            return String.format(Locale.ROOT, "§c%.1fx (Не очень эффективно)", bestMultiplier);
        }
        return "§40x (Не действует / Иммунитет)";
    }

    public String getShortBadge() {
        if (isMostEffective) {
            return "§6[★ ТОП]";
        }
        if (bestMultiplier >= 4.0) return "§e[4x]";
        if (bestMultiplier >= 2.0) return "§a[2x]";
        if (bestMultiplier == 1.0) return "§7[1x]";
        if (bestMultiplier > 0.0 && bestMultiplier < 1.0) return "§c[0.5x]";
        return "§4[0x]";
    }

    public List<Component> buildTooltip(String contextTitle, String opponentName) {
        List<Component> list = new ArrayList<>();
        list.add(Component.literal("§6★ " + contextTitle + ": §e" + pokemonName + " §7(Ур. " + level + ")"));
        list.add(Component.literal("§7Против: §c" + opponentName));
        list.add(Component.empty());

        if (isFainted || currentHp <= 0) {
            list.add(Component.literal("§c✕ Покемон без сознания (HP 0)"));
            return list;
        }

        if (bestAttack != null) {
            String stabText = hasStab ? " §e[STAB x1.5]" : "";
            list.add(Component.literal("§a• Лучшая атака: §f" + bestAttackName + stabText));
            String typeName = bestAttackType != null ? bestAttackType.name().getString() : "—";
            list.add(Component.literal("  §7Тип: §b" + typeName + " §7| Мощь: §f" + bestBasePower));
            list.add(Component.literal("  §7Эффективность: " + getFormattedMultiplierWithColor()));
        } else {
            list.add(Component.literal("§e• Нет атакующих способностей"));
        }

        list.add(Component.empty());
        String defText;
        if (defenseMultiplier <= 0.25) defText = "§aДвойная устойчивость (0.25x урона)";
        else if (defenseMultiplier <= 0.5) defText = "§aУстойчив (0.5x урона)";
        else if (defenseMultiplier >= 4.0) defText = "§cКритическая уязвимость (4x урона)";
        else if (defenseMultiplier >= 2.0) defText = "§cУязвим (2x урона)";
        else defText = "§7Нейтральная защита (1x урона)";
        list.add(Component.literal("§b• Защита от врага: " + defText));

        list.add(Component.empty());
        if (isMostEffective) {
            list.add(Component.literal("§6★ РЕКОМЕНДАЦИЯ: САМЫЙ ЭФФЕКТИВНЫЙ ВЫБОР!"));
        } else if (bestMultiplier >= 2.0) {
            list.add(Component.literal("§a✔ Отличный выбор (Супер-эффективен)"));
        } else if (bestMultiplier == 1.0) {
            list.add(Component.literal("§7Нейтральный выбор"));
        } else {
            list.add(Component.literal("§cНе рекомендуется против этого типа"));
        }

        return list;
    }
}
