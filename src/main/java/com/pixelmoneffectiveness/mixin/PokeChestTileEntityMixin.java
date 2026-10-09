package com.pixelmoneffectiveness.mixin;

import com.pixelmonmod.pixelmon.api.util.LootClaim;
import com.pixelmonmod.pixelmon.blocks.tileentity.PokeChestTileEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PokeChestTileEntity.class)
public abstract class PokeChestTileEntityMixin {

    @Shadow
    private List<LootClaim> claimed;

    @Shadow
    private boolean timeEnabled;

    @Shadow
    private boolean dropOneTime;

    @Shadow
    private boolean chestOneTime;

    @Shadow
    private boolean manualControl;

    /**
     * Syncs chest cooldown claims and time settings to client packets so the client
     * radar can know when a chest is on cooldown.
     */
    @Inject(method = "writeToNBTClient", at = @At("TAIL"))
    private void pixelmonEffectiveness$syncClaimToClient(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        ListTag listTag = new ListTag();
        if (this.claimed != null) {
            for (LootClaim claim : this.claimed) {
                if (claim != null && claim.getPlayerID() != null) {
                    CompoundTag claimTag = new CompoundTag();
                    claimTag.putUUID("Claimer", claim.getPlayerID());
                    claimTag.putLong("timeClaimed", claim.getTimeClaimed());
                    listTag.add(claimTag);
                }
            }
        }
        tag.put("claimedPlayers", listTag);
        tag.put("claimed", listTag);
        tag.putBoolean("timeEnabled", this.timeEnabled);
        tag.putBoolean("dropOneTime", this.dropOneTime);
        tag.putBoolean("chestOneTime", this.chestOneTime);
        tag.putBoolean("manualControl", this.manualControl);
    }

    /**
     * Reads the synced claims on the client from network packets.
     */
    @Inject(method = "readFromNBTClient", at = @At("TAIL"))
    private void pixelmonEffectiveness$readClaimFromClient(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        pixelmonEffectiveness$readClaims(tag);
    }

    /**
     * Reads the synced claims on the client when chunk is loaded.
     */
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void pixelmonEffectiveness$readClaimFromLoadAdditional(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        pixelmonEffectiveness$readClaims(tag);
    }

    private void pixelmonEffectiveness$readClaims(CompoundTag tag) {
        String key = tag.contains("claimedPlayers", 9) ? "claimedPlayers" : (tag.contains("claimed", 9) ? "claimed" : null);
        if (key != null) {
            if (this.claimed == null) {
                this.claimed = new java.util.ArrayList<>();
            } else {
                this.claimed.clear();
            }
            ListTag listTag = tag.getList(key, 10);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag claimTag = listTag.getCompound(i);
                if (claimTag.hasUUID("Claimer")) {
                    this.claimed.add(new LootClaim(claimTag.getUUID("Claimer"), claimTag.getLong("timeClaimed")));
                }
            }
        }
        if (tag.contains("timeEnabled")) {
            this.timeEnabled = tag.getBoolean("timeEnabled");
        }
        if (tag.contains("dropOneTime")) {
            this.dropOneTime = tag.getBoolean("dropOneTime");
        }
        if (tag.contains("chestOneTime")) {
            this.chestOneTime = tag.getBoolean("chestOneTime");
        }
        if (tag.contains("manualControl")) {
            this.manualControl = tag.getBoolean("manualControl");
        }
    }
}
