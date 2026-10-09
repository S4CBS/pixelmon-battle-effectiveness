package com.pixelmoneffectiveness.client.integration.jade;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmoneffectiveness.PixelmonEffectivenessMod;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(PixelmonEffectivenessMod.MOD_ID)
public class JadePixelmonPlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addBeforeRenderCallback((tooltip, rect, guiGraphics, accessor) -> {
            if (accessor instanceof EntityAccessor entityAccessor) {
                if (entityAccessor.getEntity() instanceof PixelmonEntity) {
                    return true; // suppresses Jade tooltip when looking at any Pokémon entity
                }
            }
            return false;
        });
    }
}
