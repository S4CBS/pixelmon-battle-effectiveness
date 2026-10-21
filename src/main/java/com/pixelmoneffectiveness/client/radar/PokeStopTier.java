package com.pixelmoneffectiveness.client.radar;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmonmod.pixelmon.init.registry.BlockRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public enum PokeStopTier {
    LEGENDARY(
        "Легендарный ПС",
        "Legendary PokéStop",
        0xFFE040FB,
        0xFFAA00AA,
        "§d[⚑]",
        1,
        ResourceLocation.fromNamespaceAndPath("pixelmon", "textures/item/pokestop/legendary.png")
    ),
    RARE(
        "Редкий ПС",
        "Rare PokéStop",
        0xFFFFD700,
        0xFFFFAA00,
        "§e[⚑]",
        3,
        ResourceLocation.fromNamespaceAndPath("pixelmon", "textures/item/pokestop/rare.png")
    ),
    COMMON(
        "Обычный ПС",
        "Common PokéStop",
        0xFF2196F3,
        0xFF0088FF,
        "§9[⚑]",
        5,
        ResourceLocation.fromNamespaceAndPath("pixelmon", "textures/item/pokestop/common.png")
    );

    private final String displayNameRu;
    private final String displayNameEn;
    private final int colorRgb;
    private final int hudColor;
    private final String iconPrefix;
    private final int priority;
    private final ResourceLocation texture;

    PokeStopTier(String displayNameRu, String displayNameEn, int colorRgb, int hudColor, String iconPrefix, int priority, ResourceLocation texture) {
        this.displayNameRu = displayNameRu;
        this.displayNameEn = displayNameEn;
        this.colorRgb = colorRgb;
        this.hudColor = hudColor;
        this.iconPrefix = iconPrefix;
        this.priority = priority;
        this.texture = texture;
    }

    public String getDisplayName(boolean isRu) {
        return isRu ? displayNameRu : displayNameEn;
    }

    public int getColorRgb() {
        return colorRgb;
    }

    public int getHudColor() {
        return hudColor;
    }

    public String getIconPrefix() {
        return iconPrefix;
    }

    public int getPriority() {
        return priority;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public boolean isSearchEnabled() {
        if (EffectivenessConfig.CONFIG == null) return true;
        return switch (this) {
            case LEGENDARY -> EffectivenessConfig.CONFIG.searchPokestopLegendary.get();
            case RARE -> EffectivenessConfig.CONFIG.searchPokestopRare.get();
            case COMMON -> EffectivenessConfig.CONFIG.searchPokestopCommon.get();
        };
    }

    public void setSearchEnabled(boolean enabled) {
        if (EffectivenessConfig.CONFIG == null) return;
        switch (this) {
            case LEGENDARY -> EffectivenessConfig.CONFIG.searchPokestopLegendary.set(enabled);
            case RARE -> EffectivenessConfig.CONFIG.searchPokestopRare.set(enabled);
            case COMMON -> EffectivenessConfig.CONFIG.searchPokestopCommon.set(enabled);
        }
        EffectivenessConfig.SPEC.save();
    }

    public static PokeStopTier fromBlock(Block block) {
        if (block == null) return COMMON;
        try {
            if (block == BlockRegistration.LEGENDARY_POKESTOP.get()) return LEGENDARY;
            if (block == BlockRegistration.RARE_POKESTOP.get()) return RARE;
            if (block == BlockRegistration.COMMON_POKESTOP.get()) return COMMON;
        } catch (Throwable ignored) {}

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            if (path.contains("legendary")) return LEGENDARY;
            if (path.contains("rare")) return RARE;
            if (path.contains("common")) return COMMON;
        }
        return COMMON;
    }
}
