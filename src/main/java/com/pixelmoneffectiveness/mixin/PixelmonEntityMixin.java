package com.pixelmoneffectiveness.mixin;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PixelmonEntity.class)
public class PixelmonEntityMixin {

    /**
     * Fixes a bug in Pixelmon 9.4.1 where PixelmonEntity.unloadEntity() calls
     * this.pokemon.getWorld().dimension() without checking if getWorld() is null.
     * When capturing wild Pokémon (like Kyogre) outside battle with a Poké Ball,
     * pokemon.getWorld() is null, causing an NPE and crashing the server.
     */
    @Inject(method = "unloadEntity", at = @At("HEAD"))
    private void pixelmonEffectiveness$fixNullWorldOnUnload(CallbackInfo ci) {
        PixelmonEntity self = (PixelmonEntity) (Object) this;
        Pokemon pokemon = self.getPokemon();
        if (pokemon != null && self.level() != null) {
            if (pokemon.getWorld() == null) {
                pokemon.updateDimensionAndEntityID(self.level().dimension(), self.getId());
            }
        }
    }
}
