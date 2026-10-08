package com.pixelmoneffectiveness.client.radar;

import com.pixelmonmod.pixelmon.blocks.enums.EnumPokeChestType;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.network.chat.Component;

public enum PokeLootTier {
    MASTER("Мастер-лут", "Master Loot", 0xFFD700FF, 0xFFAA00AA, "§d[★]", 1),
    BEAST("Бист-лут", "Beast Loot", 0xFF00E5FF, 0xFF00AAAA, "§b[✦]", 2),
    ULTRA("Ультра-лут", "Ultra Loot", 0xFFFFD700, 0xFFFFAA00, "§e[◆]", 3),
    SPECIAL("Спец-лут", "Special Loot", 0xFF69F0AE, 0xFF00AA00, "§a[✤]", 4),
    POKE("Поке-лут", "Poké Loot", 0xFFFF5252, 0xFFAA0000, "§c[•]", 5);

    private final String displayNameRu;
    private final String displayNameEn;
    private final int colorRgb;
    private final int hudColor;
    private final String iconPrefix;
    private final int priority;

    PokeLootTier(String displayNameRu, String displayNameEn, int colorRgb, int hudColor, String iconPrefix, int priority) {
        this.displayNameRu = displayNameRu;
        this.displayNameEn = displayNameEn;
        this.colorRgb = colorRgb;
        this.hudColor = hudColor;
        this.iconPrefix = iconPrefix;
        this.priority = priority;
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

    public boolean isSearchEnabled() {
        if (EffectivenessConfig.CONFIG == null) return true;
        return switch (this) {
            case MASTER -> EffectivenessConfig.CONFIG.searchMaster.get();
            case BEAST -> EffectivenessConfig.CONFIG.searchBeast.get();
            case ULTRA -> EffectivenessConfig.CONFIG.searchUltra.get();
            case SPECIAL -> EffectivenessConfig.CONFIG.searchSpecial.get();
            case POKE -> EffectivenessConfig.CONFIG.searchPoke.get() && !EffectivenessConfig.CONFIG.radarFilterNormalPoke.get();
        };
    }

    public void setSearchEnabled(boolean enabled) {
        if (EffectivenessConfig.CONFIG == null) return;
        switch (this) {
            case MASTER -> EffectivenessConfig.CONFIG.searchMaster.set(enabled);
            case BEAST -> EffectivenessConfig.CONFIG.searchBeast.set(enabled);
            case ULTRA -> EffectivenessConfig.CONFIG.searchUltra.set(enabled);
            case SPECIAL -> EffectivenessConfig.CONFIG.searchSpecial.set(enabled);
            case POKE -> {
                EffectivenessConfig.CONFIG.searchPoke.set(enabled);
                EffectivenessConfig.CONFIG.radarFilterNormalPoke.set(!enabled);
            }
        }
        EffectivenessConfig.SPEC.save();
    }

    public static PokeLootTier fromChestType(EnumPokeChestType type) {
        if (type == null) {
            return POKE;
        }
        return switch (type) {
            case MASTERBALL -> MASTER;
            case BEASTBALL -> BEAST;
            case ULTRABALL -> ULTRA;
            case SPECIAL -> SPECIAL;
            default -> POKE;
        };
    }
}
