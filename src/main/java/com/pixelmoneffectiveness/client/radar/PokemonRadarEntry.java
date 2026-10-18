package com.pixelmoneffectiveness.client.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class PokemonRadarEntry implements Comparable<PokemonRadarEntry> {
    public final Vec3 pos;
    public final String name;
    public final int level;
    public final boolean isShiny;
    public final boolean isLegendary;
    public final boolean isBoss;
    public final String bossTierName;
    public final int bossColor;
    public final boolean isMatchedFilter;
    public final ResourceLocation sprite;
    public double distance;
    public double relativeAngle;

    public PokemonRadarEntry(Vec3 pos, String name, int level, boolean isShiny, boolean isLegendary, boolean isBoss, String bossTierName, int bossColor, boolean isMatchedFilter, ResourceLocation sprite, double distance, double relativeAngle) {
        this.pos = pos;
        this.name = name;
        this.level = level;
        this.isShiny = isShiny;
        this.isLegendary = isLegendary;
        this.isBoss = isBoss;
        this.bossTierName = bossTierName;
        this.bossColor = bossColor;
        this.isMatchedFilter = isMatchedFilter;
        this.sprite = sprite;
        this.distance = distance;
        this.relativeAngle = relativeAngle;
    }

    public int getHudColor() {
        if (isShiny) return 0xFFFFD700;      // Gold
        if (isLegendary) return 0xFFFF55FF;  // Light Purple / Magenta
        if (isBoss) {
            if (bossColor != 0) {
                return 0xFF000000 | (bossColor & 0x00FFFFFF);
            }
            return 0xFFFF5555;               // Red
        }
        if (isMatchedFilter) return 0xFF55FFFF; // Cyan
        return 0xFFFFFFFF;                  // White
    }

    public String getPrefix() {
        if (isShiny) return "§6[✨]";
        if (isLegendary) return "§d[★]";
        if (isBoss) {
            if (bossTierName != null && !bossTierName.isEmpty()) {
                return "§c[⚔ " + bossTierName + "]";
            }
            return "§c[⚔ Босс]";
        }
        return "§b[●]";
    }

    @Override
    public int compareTo(PokemonRadarEntry other) {
        if (this.isMatchedFilter != other.isMatchedFilter) {
            return this.isMatchedFilter ? -1 : 1;
        }
        boolean thisSpecial = this.isShiny || this.isLegendary || this.isBoss;
        boolean otherSpecial = other.isShiny || other.isLegendary || other.isBoss;
        if (thisSpecial != otherSpecial) {
            return thisSpecial ? -1 : 1;
        }
        return Double.compare(this.distance, other.distance);
    }
}
