package com.pixelmoneffectiveness.mixin;

import com.pixelmonmod.pixelmon.battles.raids.data.RaidData;
import com.pixelmonmod.pixelmon.entities.DenEntity;
import com.pixelmoneffectiveness.client.telegram.TelegramEventWatcher;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DenEntity.class)
public class DenEntityMixin {

    @Unique
    private boolean pixelmonEffectiveness$isLoadingFromSave = false;

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void pixelmonEffectiveness$beforeReadSave(CompoundTag tag, CallbackInfo ci) {
        this.pixelmonEffectiveness$isLoadingFromSave = true;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void pixelmonEffectiveness$afterReadSave(CompoundTag tag, CallbackInfo ci) {
        this.pixelmonEffectiveness$isLoadingFromSave = false;
    }

    @Inject(method = "setData", at = @At("RETURN"))
    private void pixelmonEffectiveness$onSetRaidData(RaidData raidData, CallbackInfo ci) {
        if (this.pixelmonEffectiveness$isLoadingFromSave) {
            return;
        }
        DenEntity den = (DenEntity) (Object) this;
        if (den.level() != null && !den.level().isClientSide()) {
            if (raidData != null && raidData.getPokemon() != null && raidData.getStars() > 0) {
                TelegramEventWatcher.handleDenSpawn(den, den.level());
            }
        }
    }
}
