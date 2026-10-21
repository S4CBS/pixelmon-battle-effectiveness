package com.pixelmoneffectiveness.client.integration.journeymap;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.support.JourneyMapIntegration;
import com.pixelmoneffectiveness.client.radar.DittoDisguiseHelper;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.client.event.EntityRadarUpdateEvent;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

@JourneyMapPlugin(apiVersion = "2.0")
public class PixelmonJourneyMapPlugin implements IClientPlugin {

    private static IClientAPI clientApi;

    @Override
    public void initialize(IClientAPI api) {
        clientApi = api;

        // Try initializing Pixelmon's own integration
        try {
            JourneyMapIntegration pjm = new JourneyMapIntegration();
            pjm.initialize(api);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Also subscribe our own handler to ensure Pokémon icons/sprites are always set
        try {
            ClientEventRegistry.ENTITY_RADAR_UPDATE_EVENT.subscribe(getModId(), this::onRadarEntityUpdate);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public String getModId() {
        return "pixelmoneffectiveness";
    }

    private void onRadarEntityUpdate(EntityRadarUpdateEvent event) {
        if (event == null || event.getWrappedEntity() == null || event.getWrappedEntity().getEntityRef() == null) {
            return;
        }
        Entity entity = event.getWrappedEntity().getEntityRef().get();
        if (entity instanceof PixelmonEntity pixelmon) {
            boolean isConfirmed = DittoDisguiseHelper.isConfirmedDitto(pixelmon);
            if (isConfirmed) {
                event.getWrappedEntity().setEntityIconLocation(DittoDisguiseHelper.DITTO_SPRITE);
                String spName = pixelmon.getPokemon() != null && pixelmon.getPokemon().getSpecies() != null
                    ? pixelmon.getPokemon().getSpecies().getName() : "";
                event.getWrappedEntity().setCustomName(Component.literal("§d[🎭 ДИТТО!] " + spName));
                event.getWrappedEntity().setLabelColor(0xFFFF55FF);
                event.getWrappedEntity().setColor(0xFFFF55FF);
                event.getWrappedEntity().setDrawOutline(true);
            } else {
                ResourceLocation sprite = pixelmon.getSprite();
                if (sprite != null) {
                    event.getWrappedEntity().setEntityIconLocation(sprite);
                }
            }
        }
    }
}
