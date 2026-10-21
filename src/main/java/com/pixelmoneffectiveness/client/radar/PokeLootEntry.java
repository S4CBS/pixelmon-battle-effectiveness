package com.pixelmoneffectiveness.client.radar;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class PokeLootEntry implements Comparable<PokeLootEntry> {
    public final BlockPos pos;
    public final PokeLootTier tier;
    public final boolean isHidden;
    public final boolean isPokeStop;
    public final PokeStopTier pokeStopTier;
    public final boolean isOnCooldown;
    public double distance;
    public double relativeAngle;

    // Constructor for PokeChest
    public PokeLootEntry(BlockPos pos, PokeLootTier tier, boolean isHidden, double distance, double relativeAngle) {
        this(pos, tier, isHidden, false, null, false, distance, relativeAngle);
    }

    // Constructor for PokeStop
    public PokeLootEntry(BlockPos pos, PokeStopTier pokeStopTier, boolean isOnCooldown, double distance, double relativeAngle) {
        this(pos, null, false, true, pokeStopTier, isOnCooldown, distance, relativeAngle);
    }

    public PokeLootEntry(
        BlockPos pos,
        PokeLootTier tier,
        boolean isHidden,
        boolean isPokeStop,
        PokeStopTier pokeStopTier,
        boolean isOnCooldown,
        double distance,
        double relativeAngle
    ) {
        this.pos = pos;
        this.tier = tier;
        this.isHidden = isHidden;
        this.isPokeStop = isPokeStop;
        this.pokeStopTier = pokeStopTier;
        this.isOnCooldown = isOnCooldown;
        this.distance = distance;
        this.relativeAngle = relativeAngle;
    }

    public String getDisplayName(boolean isRu) {
        if (isPokeStop && pokeStopTier != null) {
            return pokeStopTier.getDisplayName(isRu);
        }
        return (tier != null) ? tier.getDisplayName(isRu) : "Loot";
    }

    public int getColorRgb() {
        if (isPokeStop && pokeStopTier != null) {
            return pokeStopTier.getColorRgb();
        }
        return (tier != null) ? tier.getColorRgb() : 0xFFFFFFFF;
    }

    public int getHudColor() {
        if (isPokeStop && pokeStopTier != null) {
            return pokeStopTier.getHudColor();
        }
        return (tier != null) ? tier.getHudColor() : 0xFFFFFFFF;
    }

    public String getIconPrefix() {
        if (isPokeStop && pokeStopTier != null) {
            return pokeStopTier.getIconPrefix();
        }
        return (tier != null) ? tier.getIconPrefix() : "§f[•]";
    }

    public ResourceLocation getTexture() {
        if (isPokeStop && pokeStopTier != null) {
            return pokeStopTier.getTexture();
        }
        return (tier != null) ? tier.getBallTexture() : null;
    }

    public int getPriority() {
        if (isPokeStop && pokeStopTier != null) {
            return pokeStopTier.getPriority();
        }
        return (tier != null) ? tier.getPriority() : 99;
    }

    @Override
    public int compareTo(PokeLootEntry other) {
        // High priority tiers (Master, Beast, Legendary Pokestop, etc.) first
        int tierComp = Integer.compare(this.getPriority(), other.getPriority());
        if (tierComp != 0) {
            return tierComp;
        }
        // Then by distance
        return Double.compare(this.distance, other.distance);
    }
}
