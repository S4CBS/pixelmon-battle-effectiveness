package com.pixelmoneffectiveness.client.radar;

import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper utility for detecting and tracking Dittos disguised as ordinary Pokémon.
 * Supports both confirmed Dittos (pre-rolled / tagged) and disguise candidate species.
 */
public class DittoDisguiseHelper {

    /**
     * Default canonical list of Pokémon species that can transform into Ditto on catch.
     */
    private static final Set<String> CANONICAL_DITTO_DISGUISES = Set.of(
        "Pidgey",
        "Rattata",
        "Gastly",
        "Zubat",
        "Mankey",
        "Yanma",
        "Hoothoot",
        "Sentret",
        "Zigzagoon",
        "Gulpin",
        "Whismur",
        "Taillow",
        "Remoraid",
        "Starly",
        "Meltan"
    );

    /**
     * Shared set of UUIDs of confirmed disguised Dittos in the current world session.
     */
    private static final Set<UUID> CONFIRMED_DITTO_UUIDS = ConcurrentHashMap.newKeySet();

    /**
     * Ditto icon sprite location in Pixelmon assets.
     */
    public static final ResourceLocation DITTO_SPRITE = ResourceLocation.fromNamespaceAndPath(
        "pixelmon", "textures/gui/sprites/pokemon/132.png"
    );

    /**
     * Returns the dynamic or canonical set of all Pokémon species that can be a disguised Ditto.
     */
    public static Set<String> getDisguiseSpeciesNames() {
        Set<String> set = new HashSet<>(CANONICAL_DITTO_DISGUISES);
        try {
            if (PixelmonConfigProxy.getSpawning() != null) {
                List<String> cfg = PixelmonConfigProxy.getSpawning().getCanTransformToDittoOnCatch();
                if (cfg != null && !cfg.isEmpty()) {
                    set.addAll(cfg);
                }
            }
        } catch (Throwable ignored) {}
        return set;
    }

    /**
     * Checks if the given Pokémon is a candidate species that can secretly be a Ditto.
     */
    public static boolean isDittoDisguiseCandidate(Pokemon pokemon) {
        if (pokemon == null) return false;
        Species sp = pokemon.getSpecies();
        if (sp == null) return false;

        String name = sp.getName();
        if ("Ditto".equalsIgnoreCase(name)) {
            return false; // It's already a real Ditto, not a disguise
        }

        Set<String> validSpecies = getDisguiseSpeciesNames();
        for (String candidate : validSpecies) {
            if (candidate.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given PixelmonEntity is confirmed to be a Ditto (either real or disguised).
     */
    public static boolean isConfirmedDitto(PixelmonEntity pixelmon) {
        if (pixelmon == null) return false;

        Pokemon p = pixelmon.getPokemon();
        if (p != null && p.getSpecies() != null) {
            if (p.getSpecies().is(PixelmonSpecies.DITTO) || "Ditto".equalsIgnoreCase(p.getSpecies().getName())) {
                return true;
            }
        }

        UUID uuid = pixelmon.getUUID();
        if (uuid != null && CONFIRMED_DITTO_UUIDS.contains(uuid)) {
            return true;
        }

        try {
            if (pixelmon.getTags().contains("pe_is_ditto")) {
                if (uuid != null) CONFIRMED_DITTO_UUIDS.add(uuid);
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * Marks a Pokémon UUID as a confirmed disguised Ditto.
     */
    public static void markConfirmedDitto(UUID uuid) {
        if (uuid != null) {
            CONFIRMED_DITTO_UUIDS.add(uuid);
        }
    }

    /**
     * Checks if a search filter token is requesting Ditto.
     */
    public static boolean isDittoFilterToken(String token) {
        if (token == null) return false;
        String t = token.trim().toLowerCase(Locale.ROOT);
        return t.equals("ditto") || t.equals("дитто") || t.equals("дито") || t.contains("ditto") || t.contains("дитто");
    }

    public static ResourceLocation getDittoSprite() {
        return DITTO_SPRITE;
    }

    public static void clearCache() {
        CONFIRMED_DITTO_UUIDS.clear();
    }
}
