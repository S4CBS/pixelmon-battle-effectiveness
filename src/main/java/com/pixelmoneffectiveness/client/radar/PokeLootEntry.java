package com.pixelmoneffectiveness.client.radar;

import net.minecraft.core.BlockPos;

public class PokeLootEntry implements Comparable<PokeLootEntry> {
    public final BlockPos pos;
    public final PokeLootTier tier;
    public final boolean isHidden;
    public double distance;
    public double relativeAngle;

    public PokeLootEntry(BlockPos pos, PokeLootTier tier, boolean isHidden, double distance, double relativeAngle) {
        this.pos = pos;
        this.tier = tier;
        this.isHidden = isHidden;
        this.distance = distance;
        this.relativeAngle = relativeAngle;
    }

    @Override
    public int compareTo(PokeLootEntry other) {
        // High priority tiers (Master, Beast, Ultra) first
        int tierComp = Integer.compare(this.tier.getPriority(), other.tier.getPriority());
        if (tierComp != 0) {
            return tierComp;
        }
        // Then by distance
        return Double.compare(this.distance, other.distance);
    }
}
