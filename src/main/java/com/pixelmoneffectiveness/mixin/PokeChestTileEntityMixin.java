package com.pixelmoneffectiveness.mixin;

import com.pixelmonmod.pixelmon.api.config.EnumPokelootModes;
import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

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
     * Prevents naturally spawned PokéChests from breaking/disappearing if the configured
     * spawn-mode is not one-time use (e.g. TIMED, PL, PU).
     */
    @Inject(method = "shouldBreakBlock", at = @At("HEAD"), cancellable = true)
    private void pixelmonEffectiveness$preventBreak(CallbackInfoReturnable<Boolean> cir) {
        if (!this.manualControl) {
            try {
                EnumPokelootModes mode = PixelmonConfigProxy.getSpawningPokeLoot().getSpawnMode();
                if (mode != null && !mode.isOneTimeUse()) {
                    cir.setReturnValue(false);
                }
            } catch (Throwable ignored) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Ensures naturally spawned PokéChests always use the configured spawn-mode settings
     * (e.g. TIMED cooldowns) whenever claim eligibility is checked.
     */
    @Inject(method = "canClaim", at = @At("HEAD"))
    private void pixelmonEffectiveness$ensureModeOnCanClaim(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (!this.manualControl) {
            try {
                EnumPokelootModes mode = PixelmonConfigProxy.getSpawningPokeLoot().getSpawnMode();
                if (mode != null) {
                    this.chestOneTime = mode.isOneTimeUse();
                    this.dropOneTime = mode.isOncePerPlayer();
                    this.timeEnabled = mode.isTimeEnabled();
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Ensures mode settings are up-to-date before syncing to client packets.
     */
    @Inject(method = "writeToNBTClient", at = @At("HEAD"))
    private void pixelmonEffectiveness$ensureModeBeforeClientSync(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!this.manualControl) {
            try {
                EnumPokelootModes mode = PixelmonConfigProxy.getSpawningPokeLoot().getSpawnMode();
                if (mode != null) {
                    this.chestOneTime = mode.isOneTimeUse();
                    this.dropOneTime = mode.isOncePerPlayer();
                    this.timeEnabled = mode.isTimeEnabled();
                }
            } catch (Throwable ignored) {}
        }
    }

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
     * When a chest is loaded from disk on the server, ensure naturally spawned chests
     * adopt the config spawn-mode, and do NOT get overridden with outdated NBT values.
     */
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void pixelmonEffectiveness$onLoadAdditional(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!this.manualControl) {
            try {
                EnumPokelootModes mode = PixelmonConfigProxy.getSpawningPokeLoot().getSpawnMode();
                if (mode != null) {
                    this.chestOneTime = mode.isOneTimeUse();
                    this.dropOneTime = mode.isOncePerPlayer();
                    this.timeEnabled = mode.isTimeEnabled();
                }
            } catch (Throwable ignored) {}
        }
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
        if (tag.contains("manualControl")) {
            this.manualControl = tag.getBoolean("manualControl");
        }
        if (!this.manualControl) {
            try {
                EnumPokelootModes mode = PixelmonConfigProxy.getSpawningPokeLoot().getSpawnMode();
                if (mode != null) {
                    this.chestOneTime = mode.isOneTimeUse();
                    this.dropOneTime = mode.isOncePerPlayer();
                    this.timeEnabled = mode.isTimeEnabled();
                }
            } catch (Throwable ignored) {}
        } else {
            if (tag.contains("timeEnabled")) {
                this.timeEnabled = tag.getBoolean("timeEnabled");
            }
            if (tag.contains("dropOneTime")) {
                this.dropOneTime = tag.getBoolean("dropOneTime");
            }
            if (tag.contains("chestOneTime")) {
                this.chestOneTime = tag.getBoolean("chestOneTime");
            }
        }
    }
}
