package com.pixelmoneffectiveness.client.autofarm.loot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ForageLootRegistry {

    private static List<ForageLootEntry> ALL_ENTRIES = null;

    private static final String[] MINTS = {
        "pixelmon:mint_seeds",
        "pixelmon:mint_adamant",
        "pixelmon:mint_bold",
        "pixelmon:mint_brave",
        "pixelmon:mint_calm",
        "pixelmon:mint_careful",
        "pixelmon:mint_gentle",
        "pixelmon:mint_hasty",
        "pixelmon:mint_impish",
        "pixelmon:mint_jolly",
        "pixelmon:mint_lax",
        "pixelmon:mint_lonely",
        "pixelmon:mint_mild",
        "pixelmon:mint_modest",
        "pixelmon:mint_naive",
        "pixelmon:mint_naughty",
        "pixelmon:mint_quiet",
        "pixelmon:mint_rash",
        "pixelmon:mint_relaxed",
        "pixelmon:mint_sassy",
        "pixelmon:mint_serious",
        "pixelmon:mint_timid",
        "pixelmon:mint_bashful",
        "pixelmon:mint_docile",
        "pixelmon:mint_hardy",
        "pixelmon:mint_quirky"
    };

    private static final String[] BONES_DROPS = {
        "minecraft:bone",
        "minecraft:skeleton_skull",
        "minecraft:wither_skeleton_skull",
        "minecraft:blaze_powder",
        "minecraft:ghast_tear",
        "minecraft:ender_pearl",
        "minecraft:ender_eye",
        "minecraft:slime_ball",
        "minecraft:gunpowder",
        "minecraft:rotten_flesh",
        "minecraft:turtle_scute"
    };

    private static final String[] STONES = {
        "pixelmon:water_stone_shard",
        "pixelmon:leaf_stone_shard",
        "pixelmon:fire_stone_shard",
        "pixelmon:thunder_stone_shard",
        "pixelmon:moon_stone_shard",
        "pixelmon:sun_stone_shard",
        "pixelmon:dawn_stone_shard",
        "pixelmon:dusk_stone_shard",
        "pixelmon:shiny_stone_shard",
        "pixelmon:ice_stone_shard",
        "pixelmon:water_stone",
        "pixelmon:leaf_stone",
        "pixelmon:fire_stone",
        "pixelmon:thunder_stone",
        "pixelmon:moon_stone",
        "pixelmon:sun_stone",
        "pixelmon:dawn_stone",
        "pixelmon:dusk_stone",
        "pixelmon:shiny_stone",
        "pixelmon:ice_stone"
    };

    private static final String[] TREASURES = {
        "pixelmon:pearl",
        "pixelmon:big_pearl",
        "pixelmon:pearl_string",
        "pixelmon:stardust",
        "pixelmon:star_piece",
        "pixelmon:sapphire",
        "pixelmon:crystal",
        "pixelmon:silicon",
        "pixelmon:aluminum_ingot",
        "pixelmon:silver_ingot",
        "pixelmon:platinum_ingot",
        "pixelmon:dubious_disc",
        "pixelmon:mirror_herb",
        "pixelmon:cracked_pot",
        "pixelmon:chipped_pot",
        "pixelmon:black_sludge",
        "pixelmon:bright_powder",
        "pixelmon:galarica_twig",
        "minecraft:iron_ingot",
        "minecraft:iron_nugget",
        "minecraft:gold_nugget",
        "minecraft:amethyst_shard",
        "minecraft:nautilus_shell",
        "minecraft:prismarine_shard",
        "minecraft:prismarine_crystals",
        "minecraft:sponge"
    };

    private static final String[] BERRIES = {
        "pixelmon:oran_berry",
        "pixelmon:sitrus_berry",
        "pixelmon:lum_berry",
        "pixelmon:leppa_berry",
        "pixelmon:pecha_berry",
        "pixelmon:cheri_berry",
        "pixelmon:chesto_berry",
        "pixelmon:rawst_berry",
        "pixelmon:aspear_berry",
        "pixelmon:persim_berry",
        "pixelmon:figy_berry",
        "pixelmon:wiki_berry",
        "pixelmon:mago_berry",
        "pixelmon:aguav_berry",
        "pixelmon:iapapa_berry",
        "pixelmon:razz_berry",
        "pixelmon:bluk_berry",
        "pixelmon:nanab_berry",
        "pixelmon:wepear_berry",
        "pixelmon:pinap_berry",
        "pixelmon:pomeg_berry",
        "pixelmon:kelpsy_berry",
        "pixelmon:qualot_berry",
        "pixelmon:hondew_berry",
        "pixelmon:grepa_berry",
        "pixelmon:tamato_berry",
        "pixelmon:cornn_berry",
        "pixelmon:magost_berry",
        "pixelmon:rabuta_berry",
        "pixelmon:nomel_berry",
        "pixelmon:spelon_berry",
        "pixelmon:pamtre_berry",
        "pixelmon:watmel_berry",
        "pixelmon:durin_berry",
        "pixelmon:belue_berry",
        "pixelmon:occa_berry",
        "pixelmon:passho_berry",
        "pixelmon:wacan_berry",
        "pixelmon:rindo_berry",
        "pixelmon:yache_berry",
        "pixelmon:chople_berry",
        "pixelmon:kebia_berry",
        "pixelmon:shuca_berry",
        "pixelmon:coba_berry",
        "pixelmon:payapa_berry",
        "pixelmon:tanga_berry",
        "pixelmon:charti_berry",
        "pixelmon:kasib_berry",
        "pixelmon:haban_berry",
        "pixelmon:colbur_berry",
        "pixelmon:babiri_berry",
        "pixelmon:chilan_berry",
        "pixelmon:roseli_berry",
        "pixelmon:liechi_berry",
        "pixelmon:ganlon_berry",
        "pixelmon:salac_berry",
        "pixelmon:petaya_berry",
        "pixelmon:apicot_berry",
        "pixelmon:lansat_berry",
        "pixelmon:starf_berry",
        "pixelmon:enigma_berry",
        "pixelmon:micle_berry",
        "pixelmon:custap_berry",
        "pixelmon:jaboca_berry",
        "pixelmon:rowap_berry",
        "pixelmon:kee_berry",
        "pixelmon:maranga_berry"
    };

    private static final String[] APRICORNS = {
        "pixelmon:black_apricorn",
        "pixelmon:blue_apricorn",
        "pixelmon:green_apricorn",
        "pixelmon:pink_apricorn",
        "pixelmon:red_apricorn",
        "pixelmon:white_apricorn",
        "pixelmon:yellow_apricorn",
        "pixelmon:grassy_seed",
        "pixelmon:electric_seed",
        "pixelmon:misty_seed",
        "pixelmon:psychic_seed"
    };

    private static final String[] MINECRAFT_NATURAL = {
        "minecraft:bamboo",
        "minecraft:cactus",
        "minecraft:carrot",
        "minecraft:potato",
        "minecraft:beetroot",
        "minecraft:beetroot_seeds",
        "minecraft:wheat_seeds",
        "minecraft:melon_seeds",
        "minecraft:pumpkin_seeds",
        "minecraft:cocoa_beans",
        "minecraft:sugar_cane",
        "minecraft:red_mushroom",
        "minecraft:brown_mushroom",
        "minecraft:allium",
        "minecraft:azure_bluet",
        "minecraft:blue_orchid",
        "minecraft:dandelion",
        "minecraft:poppy",
        "minecraft:orange_tulip",
        "minecraft:pink_tulip",
        "minecraft:red_tulip",
        "minecraft:white_tulip",
        "minecraft:oxeye_daisy",
        "minecraft:sunflower",
        "minecraft:lilac",
        "minecraft:rose_bush",
        "minecraft:peony",
        "minecraft:fern",
        "minecraft:nether_wart",
        "minecraft:chorus_fruit",
        "minecraft:flint",
        "minecraft:clay",
        "minecraft:redstone",
        "minecraft:glowstone_dust",
        "minecraft:quartz"
    };

    public static synchronized List<ForageLootEntry> getAllEntries() {
        if (ALL_ENTRIES == null) {
            List<ForageLootEntry> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            addCategoryItems(list, seen, MINTS, ForageLootCategory.MINTS);
            addCategoryItems(list, seen, BONES_DROPS, ForageLootCategory.BONES_DROPS);
            addCategoryItems(list, seen, STONES, ForageLootCategory.STONES);
            addCategoryItems(list, seen, TREASURES, ForageLootCategory.TREASURES);
            addCategoryItems(list, seen, BERRIES, ForageLootCategory.BERRIES);
            addCategoryItems(list, seen, APRICORNS, ForageLootCategory.APRICORNS);
            addCategoryItems(list, seen, MINECRAFT_NATURAL, ForageLootCategory.MINECRAFT);

            ALL_ENTRIES = Collections.unmodifiableList(list);
        }
        return ALL_ENTRIES;
    }

    private static void addCategoryItems(List<ForageLootEntry> list, Set<String> seen, String[] ids, ForageLootCategory cat) {
        for (String id : ids) {
            if (seen.add(id.toLowerCase(Locale.ROOT))) {
                ForageLootEntry entry = new ForageLootEntry(id, cat);
                if (entry.isValid) {
                    list.add(entry);
                }
            }
        }
    }

    public static Set<String> parseFilterString(String filterStr) {
        Set<String> set = new LinkedHashSet<>();
        if (filterStr == null || filterStr.trim().isEmpty()) {
            set.add("pixelmon:mint_seeds");
            set.add("pixelmon:*mint*");
            return set;
        }

        String[] tokens = filterStr.split("[,;]+");
        for (String t : tokens) {
            String token = t.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                set.add(token);
            }
        }

        if (set.isEmpty()) {
            set.add("pixelmon:mint_seeds");
            set.add("pixelmon:*mint*");
        }
        return set;
    }

    public static boolean isItemAllowed(ItemStack stack, Set<String> keepSet) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        String fullId = id.toString().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);

        if (keepSet == null || keepSet.isEmpty()) {
            // Default: keep all mints
            return path.contains("mint");
        }

        if (keepSet.contains(fullId) || keepSet.contains(path)) {
            return true;
        }

        // Support wildcards e.g. "*mint*" or "pixelmon:*mint*"
        for (String pattern : keepSet) {
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (fullId.matches(regex) || path.matches(regex)) {
                    return true;
                }
            }
            if (pattern.equalsIgnoreCase("mints") || pattern.equalsIgnoreCase("mint")) {
                if (path.contains("mint")) return true;
            }
            if (pattern.equalsIgnoreCase("bones") || pattern.equalsIgnoreCase("bone")) {
                if (path.contains("bone")) return true;
            }
        }

        return false;
    }

    public static Set<String> getDefaultMintSet() {
        Set<String> set = new LinkedHashSet<>();
        set.add("pixelmon:mint_seeds");
        set.add("pixelmon:*mint*");
        for (String m : MINTS) {
            set.add(m);
        }
        return set;
    }
}
