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

    public final ModConfigSpec.BooleanValue radarEnabled;
    public final ModConfigSpec.BooleanValue radarHudEnabled;
    public final ModConfigSpec.BooleanValue radarEsp3dEnabled;
    public final ModConfigSpec.IntValue radarScanRadius;
    public final ModConfigSpec.BooleanValue radarSoundAlert;
    public final ModConfigSpec.BooleanValue radarFilterNormalPoke;

    public final ModConfigSpec.BooleanValue searchMaster;
    public final ModConfigSpec.BooleanValue searchBeast;
    public final ModConfigSpec.BooleanValue searchUltra;
    public final ModConfigSpec.BooleanValue searchSpecial;
    public final ModConfigSpec.BooleanValue searchPoke;
    public final ModConfigSpec.BooleanValue searchHidden;

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

        builder.push("radar");

        radarEnabled = builder
            .comment("Включить радар PokéLoot для поиска сундуков-покеболов.",
                     "Enable PokéLoot radar for finding pokéchest balls.")
            .define("enabled", true);

        radarHudEnabled = builder
            .comment("Показывать экранную панель радара со стрелками направления и дистанцией.",
                     "Show on-screen radar HUD with direction arrows and distance.")
            .define("hudEnabled", true);

        radarEsp3dEnabled = builder
            .comment("Отображать 3D метки лута прямо в мире сквозь стены и блоки.",
                     "Display 3D loot markers in-world through walls and blocks.")
            .define("esp3dEnabled", true);

        radarScanRadius = builder
            .comment("Радиус сканирования чанков вокруг игрока (от 2 до 24). 12 чанков = ~192 блока.",
                     "Chunk scan radius around player (2 to 24). 12 chunks = ~192 blocks.")
            .defineInRange("scanRadius", 12, 2, 24);

        radarSoundAlert = builder
            .comment("Воспроизводить звуковое оповещение при обнаружении редкого Мастер или Бист лута.",
                     "Play audio alert chime when rare Master or Beast loot is found.")
            .define("soundAlert", true);

        radarFilterNormalPoke = builder
            .comment("Скрывать обычные (красные) поке-луты, отображая только Ультра, Мастер, Бист и Спец.",
                     "Hide normal (red) poké loot, only showing Ultra, Master, Beast, and Special loot.")
            .define("filterNormalPoke", false);

        searchMaster = builder
            .comment("Искать Мастер-лут (Master Ball).",
                     "Search for Master loot (Master Ball).")
            .define("searchMaster", true);

        searchBeast = builder
            .comment("Искать Бист-лут (Beast Ball).",
                     "Search for Beast loot (Beast Ball).")
            .define("searchBeast", true);

        searchUltra = builder
            .comment("Искать Ультра-лут (Ultra Ball).",
                     "Search for Ultra loot (Ultra Ball).")
            .define("searchUltra", true);

        searchSpecial = builder
            .comment("Искать Спец-лут (Special Poké Chest).",
                     "Search for Special loot (Special Poké Chest).")
            .define("searchSpecial", true);

        searchPoke = builder
            .comment("Искать обычный Поке-лут (Poké Ball).",
                     "Search for regular Poké loot (Poké Ball).")
            .define("searchPoke", true);

        searchHidden = builder
            .comment("Искать скрытый лут (невидимые сундуки-покеболы).",
                     "Search for hidden Poké chests (invisible pokeballs).")
            .define("searchHidden", true);

        builder.pop();
    }

    static {
        Pair<EffectivenessConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(EffectivenessConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
