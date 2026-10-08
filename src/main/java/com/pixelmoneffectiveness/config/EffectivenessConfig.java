package com.pixelmoneffectiveness.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class EffectivenessConfig {
    public static final ModConfigSpec SPEC;
    public static final EffectivenessConfig CONFIG;

    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.BooleanValue showMultiplier;
    public final ModConfigSpec.BooleanValue showForNormalMoves;
    public final ModConfigSpec.BooleanValue showForStatusMoves;
    public final ModConfigSpec.BooleanValue apricornHarvestEnabled;

    public EffectivenessConfig(ModConfigSpec.Builder builder) {
        builder.push("general");

        enabled = builder
            .comment("Включить или отключить показ эффективности атак во всех битвах.",
                     "Enable or disable battle move effectiveness display in all battles.")
            .define("enabled", true);

        showMultiplier = builder
            .comment("Показывать ли точный множитель урона рядом с надписью (например, 2x, 0.5x).",
                     "Show effectiveness multiplier number next to text (e.g. 2x, 0.5x).")
            .define("showMultiplier", true);

        showForNormalMoves = builder
            .comment("Показывать ли эффективность для нейтральных атак с 1x уроном (Эффективно / 1x).",
                     "Show effectiveness for neutral 1x moves (Effective / 1x).")
            .define("showForNormalMoves", true);

        showForStatusMoves = builder
            .comment("Показывать ли эффективность для статусных атак (без урона). Если false, то для них пишется только 'Не действует' при невосприимчивости цели (0x).",
                     "If true, show type effectiveness for status moves. If false, status moves only display when target is immune (0x).")
            .define("showForStatusMoves", false);

        apricornHarvestEnabled = builder
            .comment("Включить быстрый сбор всех априкорнов с дерева при нажатии Shift + ПКМ по стволу или листве.",
                     "Enable harvesting all apricorns from a tree when Shift + Right-Clicking the trunk or leaves.")
            .define("apricornHarvestEnabled", true);

        builder.pop();
    }

    static {
        Pair<EffectivenessConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(EffectivenessConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
