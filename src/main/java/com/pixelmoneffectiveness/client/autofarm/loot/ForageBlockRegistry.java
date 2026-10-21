package com.pixelmoneffectiveness.client.autofarm.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Registry and validator for blocks where Pixelmon's Forage move skill can be used.
 * Filters out all invalid blocks (hoppers, chests, furnaces, redstone, slabs, etc.)
 * so the auto-farm strictly targets valid terrain blocks yielding drops.
 */
public class ForageBlockRegistry {

    public enum ForageCategory {
        MOSS("Блоки мха", "Лучшие для фарма мяты, никогда не превращаются в землю"),
        DIRT_SOIL("Дёрн и почвы", "Трава, земля, подзол, мицелий, грязь"),
        SAND("Песок", "Обычный и красный песок, песок душ, почва душ"),
        GRAVEL_CLAY("Гравий и глина", "Гравий, глина, подозрительный гравий"),
        STONE_ORE("Камень и руды", "Камень, гранит, диорит, андезит, сланец, руды"),
        NETHER_END("Нижний мир и Энд", "Незерак, базальт, чернит, нилий, камень Энда"),
        FOLIAGE_WOOD("Листва и древесина", "Все виды листвы и бревен"),
        SNOW_ICE("Снег и лёд", "Блок снега, плотный лёд, синий лёд, лёд"),
        INVALID("Недоступно", "Блоки не поддерживают Forage (воронки, сундуки, механизмы)");

        private final String displayName;
        private final String description;

        ForageCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Determines the Forage category of a given BlockState.
     */
    public static ForageCategory getCategory(BlockState state) {
        if (state == null || state.isAir()) return ForageCategory.INVALID;
        Block b = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return ForageCategory.INVALID;

        String path = id.getPath().toLowerCase(Locale.ROOT);
        String ns = id.getNamespace().toLowerCase(Locale.ROOT);

        // 1. Moss
        if (b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET || path.contains("moss")) {
            return ForageCategory.MOSS;
        }

        // 2. Dirt & Soil
        if (state.is(BlockTags.DIRT) || b == Blocks.FARMLAND || b == Blocks.DIRT_PATH
            || path.contains("dirt") || path.contains("grass_block") || path.contains("podzol")
            || path.contains("mycelium") || path.contains("mud") || path.contains("rooted")) {
            return ForageCategory.DIRT_SOIL;
        }

        // 3. Sand
        if (state.is(BlockTags.SAND) || state.is(BlockTags.SMELTS_TO_GLASS)
            || b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.SOUL_SAND || b == Blocks.SOUL_SOIL
            || b == Blocks.SUSPICIOUS_SAND || path.contains("sand")) {
            return ForageCategory.SAND;
        }

        // 4. Gravel & Clay
        if (b == Blocks.GRAVEL || b == Blocks.SUSPICIOUS_GRAVEL || b == Blocks.CLAY
            || path.contains("gravel") || path.contains("clay")) {
            return ForageCategory.GRAVEL_CLAY;
        }

        // 5. Nether & End
        if (state.is(BlockTags.NYLIUM) || b == Blocks.NETHERRACK || b == Blocks.END_STONE
            || b == Blocks.BASALT || b == Blocks.SMOOTH_BASALT || b == Blocks.BLACKSTONE
            || path.contains("netherrack") || path.contains("nylium") || path.contains("end_stone")
            || path.contains("basalt") || path.contains("blackstone")) {
            return ForageCategory.NETHER_END;
        }

        // 6. Stone & Ores
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
            || state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
            || b == Blocks.STONE || b == Blocks.GRANITE || b == Blocks.DIORITE || b == Blocks.ANDESITE
            || b == Blocks.DEEPSLATE || b == Blocks.COBBLESTONE || b == Blocks.COBBLED_DEEPSLATE
            || b == Blocks.TUFF || b == Blocks.CALCITE || b == Blocks.DRIPSTONE_BLOCK
            || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE
            || path.contains("ore") || path.contains("stone") || path.contains("granite")
            || path.contains("diorite") || path.contains("andesite") || path.contains("deepslate")
            || path.contains("tuff") || path.contains("calcite")) {
            return ForageCategory.STONE_ORE;
        }

        // 7. Foliage & Wood
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
            || path.contains("leaves") || path.contains("log") || path.contains("wood")) {
            return ForageCategory.FOLIAGE_WOOD;
        }

        // 8. Snow & Ice
        if (state.is(BlockTags.SNOW) || state.is(BlockTags.ICE)
            || b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW || b == Blocks.ICE
            || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE
            || path.contains("snow") || path.contains("ice")) {
            return ForageCategory.SNOW_ICE;
        }

        // Pixelmon specific ultra space terrain
        if ("pixelmon".equals(ns)) {
            if (path.contains("dirt") || path.contains("grass")) return ForageCategory.DIRT_SOIL;
            if (path.contains("sand")) return ForageCategory.SAND;
            if (path.contains("stone") || path.contains("ore")) return ForageCategory.STONE_ORE;
        }

        return ForageCategory.INVALID;
    }

    /**
     * Checks if a block is explicitly blacklisted (containers, redstone, crafting stations, slabs, etc.)
     */
    public static boolean isBlacklistedBlock(Level level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) return true;

        // Any block with a BlockEntity / TileEntity is automatically blacklisted!
        // This instantly rejects Hoppers, Chests, Barrels, Furnaces, Shulkers, Dispensers, Crafters, Pixelmon machines!
        if (level != null && level.getBlockEntity(pos) != null) {
            return true;
        }

        Block b = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return true;

        String path = id.getPath().toLowerCase(Locale.ROOT);

        // Explicit name pattern blacklist
        if (path.contains("hopper") // Absolute protection against hopper target
            || path.contains("chest")
            || path.contains("barrel")
            || path.contains("shulker")
            || path.contains("furnace")
            || path.contains("smoker")
            || path.contains("dropper")
            || path.contains("dispenser")
            || path.contains("crafter")
            || path.contains("anvil")
            || path.contains("table")
            || path.contains("stand")
            || path.contains("cauldron")
            || path.contains("beacon")
            || path.contains("conduit")
            || path.contains("anchor")
            || path.contains("bed")
            || path.contains("bell")
            || path.contains("spawner")
            || path.contains("slab")
            || path.contains("stairs")
            || path.contains("wall")
            || path.contains("fence")
            || path.contains("gate")
            || path.contains("door")
            || path.contains("trapdoor")
            || path.contains("glass")
            || path.contains("pane")
            || path.contains("bars")
            || path.contains("chain")
            || path.contains("lantern")
            || path.contains("torch")
            || path.contains("candle")
            || path.contains("rod")
            || path.contains("piston")
            || path.contains("observer")
            || path.contains("repeater")
            || path.contains("comparator")
            || path.contains("sensor")
            || path.contains("button")
            || path.contains("lever")
            || path.contains("pressure_plate")
            || path.contains("rail")
            || path.contains("redstone")
            || path.contains("concrete")
            || path.contains("terracotta")
            || path.contains("glazed")
            || path.contains("wool")
            || (path.contains("carpet") && !path.contains("moss"))
            || path.contains("planks")
            || path.contains("brick")) {
            return true;
        }

        // Tag-based blacklist
        if (state.is(BlockTags.SLABS)
            || state.is(BlockTags.STAIRS)
            || state.is(BlockTags.WALLS)
            || state.is(BlockTags.FENCES)
            || state.is(BlockTags.DOORS)
            || state.is(BlockTags.TRAPDOORS)
            || state.is(BlockTags.PRESSURE_PLATES)
            || state.is(BlockTags.BUTTONS)
            || state.is(BlockTags.ANVIL)
            || state.is(BlockTags.BEDS)
            || state.is(BlockTags.WOOL)
            || state.is(BlockTags.WOOL_CARPETS)
            || state.is(BlockTags.RAILS)) {
            return true;
        }

        return false;
    }

    /**
     * Complete check: returns true ONLY if the block is a valid, proven Forage target.
     */
    public static boolean isValidForageBlock(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        // Must be solid
        if (!state.isSolid() && !state.blocksMotion() && !state.is(Blocks.MOSS_CARPET)) {
            return false;
        }

        // No fluids
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        // Must NOT be blacklisted (no hoppers, chests, redstone, machines)
        if (isBlacklistedBlock(level, pos, state)) {
            return false;
        }

        // Must belong to a recognized natural Forage category
        ForageCategory category = getCategory(state);
        if (category == ForageCategory.INVALID) {
            return false;
        }

        // Block above must allow drop/entity
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        return !aboveState.isSolid() || aboveState.canBeReplaced() || aboveState.is(Blocks.MOSS_CARPET);
    }

    /**
     * Calculates the suitability score of a block for Foraging:
     * - Moss Block: 100
     * - Moss Carpet: 90
     * - Podzol / Mycelium: 75
     * - Grass Block: 60
     * - Mud / Rooted: 45
     * - Dirt / Coarse: 35
     * - Sand / Gravel / Clay: 30
     * - Stone / Ores: 25
     * - Leaves / Wood / Snow / Ice: 20
     * - Non-forageable / Blacklisted: -1
     */
    public static int getBlockScore(BlockState state, BlockPos pos, BlockPos playerPos, Level level, boolean mintFarming) {
        if (level == null || pos == null || !isValidForageBlock(level, pos)) {
            return -1;
        }

        ForageCategory category = getCategory(state);
        Block b = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        String path = (id != null) ? id.getPath().toLowerCase(Locale.ROOT) : "";

        int score;
        if (mintFarming) {
            switch (category) {
                case MOSS -> {
                    if (b == Blocks.MOSS_BLOCK || path.equals("moss_block")) {
                        score = 100;
                    } else {
                        score = 90;
                    }
                }
                case DIRT_SOIL -> {
                    if (b == Blocks.PODZOL || b == Blocks.MYCELIUM) {
                        score = 75;
                    } else if (b == Blocks.GRASS_BLOCK || path.contains("grass_block")) {
                        score = 60;
                    } else if (b == Blocks.MUD || b == Blocks.ROOTED_DIRT || path.contains("mud") || path.contains("rooted")) {
                        score = 45;
                    } else {
                        score = 35; // Ordinary dirt, coarse dirt, farmland
                    }
                }
                case SAND -> score = 30;
                case GRAVEL_CLAY -> score = 28;
                case STONE_ORE -> score = 25;
                case FOLIAGE_WOOD -> score = 20;
                case SNOW_ICE -> score = 20;
                case NETHER_END -> score = 15;
                default -> score = -1;
            }

            // Sky visibility bonus when farming mints
            if (score > 0 && level.canSeeSky(pos.above())) {
                score += 5;
            }
        } else {
            // General loot farming: all natural blocks are viable
            switch (category) {
                case MOSS -> score = 80;
                case DIRT_SOIL -> score = 70;
                case STONE_ORE -> score = 65;
                case SAND -> score = 60;
                case GRAVEL_CLAY -> score = 60;
                case NETHER_END -> score = 55;
                case FOLIAGE_WOOD -> score = 50;
                case SNOW_ICE -> score = 50;
                default -> score = -1;
            }
        }

        // Bonus if block is directly beneath player's feet (0, -1, 0)
        if (score > 0 && playerPos != null) {
            if (pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ() && pos.getY() == playerPos.getY() - 1) {
                score += 20;
            }
        }

        return score;
    }

    /**
     * Returns a structured list of canonical valid Forage blocks by category.
     */
    public static Map<ForageCategory, List<String>> getVerifiedBlockList() {
        Map<ForageCategory, List<String>> map = new LinkedHashMap<>();

        map.put(ForageCategory.MOSS, List.of(
            "minecraft:moss_block (Блок мха - топ 1)",
            "minecraft:moss_carpet (Ковёр из мха)"
        ));

        map.put(ForageCategory.DIRT_SOIL, List.of(
            "minecraft:grass_block (Дёрн)",
            "minecraft:podzol (Подзол)",
            "minecraft:mycelium (Мицелий)",
            "minecraft:dirt (Земля)",
            "minecraft:coarse_dirt (Каменистая земля)",
            "minecraft:rooted_dirt (Укоренившаяся земля)",
            "minecraft:mud (Грязь)",
            "minecraft:muddy_mangrove_roots (Грязные мангровые корни)",
            "minecraft:farmland (Пашня)",
            "minecraft:dirt_path (Тропа)"
        ));

        map.put(ForageCategory.SAND, List.of(
            "minecraft:sand (Песок)",
            "minecraft:red_sand (Красный песок)",
            "minecraft:soul_sand (Песок душ)",
            "minecraft:soul_soil (Почва душ)",
            "minecraft:suspicious_sand (Подозрительный песок)"
        ));

        map.put(ForageCategory.GRAVEL_CLAY, List.of(
            "minecraft:gravel (Гравий)",
            "minecraft:suspicious_gravel (Подозрительный гравий)",
            "minecraft:clay (Блок глины)"
        ));

        map.put(ForageCategory.STONE_ORE, List.of(
            "minecraft:stone (Камень)",
            "minecraft:granite (Гранит)",
            "minecraft:diorite (Диорит)",
            "minecraft:andesite (Андезит)",
            "minecraft:deepslate (Глубинный сланец)",
            "minecraft:cobblestone (Булыжник)",
            "minecraft:cobbled_deepslate (Дробленый сланец)",
            "minecraft:tuff (Туф)",
            "minecraft:calcite (Кальцит)",
            "minecraft:dripstone_block (Капельник)",
            "minecraft:sandstone (Песчаник)",
            "minecraft:red_sandstone (Красный песчаник)",
            "minecraft:coal_ore / iron_ore / copper_ore / gold_ore / redstone_ore / diamond_ore / emerald_ore / lapis_ore",
            "minecraft:deepslate_*_ore (Глубинные руды)",
            "minecraft:nether_gold_ore / nether_quartz_ore",
            "pixelmon:bauxite_ore / ruby_ore / sapphire_ore / silicon_ore / platinum_ore / silver_ore"
        ));

        map.put(ForageCategory.NETHER_END, List.of(
            "minecraft:netherrack (Незерак)",
            "minecraft:crimson_nylium (Багровый нилий)",
            "minecraft:warped_nylium (Искажённый нилий)",
            "minecraft:basalt (Базальт)",
            "minecraft:smooth_basalt (Гладкий базальт)",
            "minecraft:blackstone (Чернит)",
            "minecraft:end_stone (Камень Энда)"
        ));

        map.put(ForageCategory.FOLIAGE_WOOD, List.of(
            "minecraft:oak_leaves / spruce_leaves / birch_leaves / jungle_leaves / acacia_leaves / dark_oak_leaves / mangrove_leaves / cherry_leaves / azalea_leaves",
            "minecraft:oak_log / spruce_log / birch_log / jungle_log / acacia_log / dark_oak_log / mangrove_log / cherry_log"
        ));

        map.put(ForageCategory.SNOW_ICE, List.of(
            "minecraft:snow_block (Блок снега)",
            "minecraft:powder_snow (Рыхлый снег)",
            "minecraft:ice (Лёд)",
            "minecraft:packed_ice (Плотный лёд)",
            "minecraft:blue_ice (Синий лёд)"
        ));

        return map;
    }
}
