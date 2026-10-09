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
    public final ModConfigSpec.BooleanValue radarHideOnCooldown;

    public final ModConfigSpec.BooleanValue searchMaster;
    public final ModConfigSpec.BooleanValue searchBeast;
    public final ModConfigSpec.BooleanValue searchUltra;
    public final ModConfigSpec.BooleanValue searchSpecial;
    public final ModConfigSpec.BooleanValue searchPoke;
    public final ModConfigSpec.BooleanValue searchHidden;

    public final ModConfigSpec.BooleanValue overlayEnabled;
    public final ModConfigSpec.ConfigValue<String> overlayAnchor;
    public final ModConfigSpec.IntValue overlayOffsetX;
    public final ModConfigSpec.IntValue overlayOffsetY;
    public final ModConfigSpec.DoubleValue overlayScale;
    public final ModConfigSpec.IntValue overlayBackgroundAlpha;
    public final ModConfigSpec.IntValue overlayBackgroundColor;
    public final ModConfigSpec.IntValue overlayBorderColor;
    public final ModConfigSpec.IntValue overlayTextColor;
    public final ModConfigSpec.DoubleValue overlayReachDistance;

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

        radarHideOnCooldown = builder
            .comment("Скрывать с радара залутанные сундуки, которые находятся на кулдауне (перезарядке).",
                     "Hide looted chests from radar while they are on cooldown.")
            .define("hideOnCooldown", true);

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

        builder.push("overlay");

        overlayEnabled = builder
            .comment("Включить инфо-окно о покемоне при наведении прицела (в стиле Cobblemon).",
                     "Enable Pokémon info overlay on crosshair hover (Cobblemon style).")
            .define("enabled", true);

        overlayAnchor = builder
            .comment("Расположение окна: TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CUSTOM.",
                     "Window anchor position.")
            .define("anchor", "TOP_CENTER");

        overlayOffsetX = builder
            .comment("Смещение окна по оси X в пикселях.", "Window X offset in pixels.")
            .defineInRange("offsetX", 0, -1000, 1000);

        overlayOffsetY = builder
            .comment("Смещение окна по оси Y в пикселях.", "Window Y offset in pixels.")
            .defineInRange("offsetY", 10, -500, 1000);

        overlayScale = builder
            .comment("Размер (масштаб) окна от 0.5x до 2.0x.", "Window scale (0.5x to 2.0x).")
            .defineInRange("scale", 1.0, 0.5, 2.0);

        overlayBackgroundAlpha = builder
            .comment("Прозрачность фона окна (0 = полностью прозрачно, 255 = непрозрачно).",
                     "Background alpha (0 to 255).")
            .defineInRange("backgroundAlpha", 200, 0, 255);

        overlayBackgroundColor = builder
            .comment("Цвет фона окна в HEX (RGB, по умолчанию 0x141726 - темный сланцевый как в Cobblemon).",
                     "Window background RGB color in hex.")
            .defineInRange("backgroundColor", 0x141726, 0x000000, 0xFFFFFF);

        overlayBorderColor = builder
            .comment("Цвет рамки окна в HEX (RGB, по умолчанию 0x303650).",
                     "Window border RGB color in hex.")
            .defineInRange("borderColor", 0x303650, 0x000000, 0xFFFFFF);

        overlayTextColor = builder
            .comment("Основной цвет текста в HEX (RGB, по умолчанию 0xFFFFFF).",
                     "Window text RGB color in hex.")
            .defineInRange("textColor", 0xFFFFFF, 0x000000, 0xFFFFFF);

        overlayReachDistance = builder
            .comment("Максимальная дистанция обнаружения покемона в блоках (от 4 до 48).",
                     "Max Pokémon detection reach distance in blocks (4 to 48).")
            .defineInRange("reachDistance", 24.0, 4.0, 48.0);

        builder.pop();
    }

    static {
        Pair<EffectivenessConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(EffectivenessConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
