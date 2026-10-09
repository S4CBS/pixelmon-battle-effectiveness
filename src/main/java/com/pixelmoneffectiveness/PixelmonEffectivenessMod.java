package com.pixelmoneffectiveness;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmoneffectiveness.client.overlay.PokemonOverlayClient;
import com.pixelmoneffectiveness.client.radar.PokeLootRadarClient;
import com.pixelmoneffectiveness.handler.ApricornTreeHarvestHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PixelmonEffectivenessMod.MOD_ID)
public class PixelmonEffectivenessMod {
    public static final String MOD_ID = "pixelmoneffectiveness";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public PixelmonEffectivenessMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Pixelmon Battle Effectiveness mod");
        modContainer.registerConfig(ModConfig.Type.CLIENT, EffectivenessConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(ApricornTreeHarvestHandler::onRightClickBlock);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PokeLootRadarClient.init(modEventBus);
            PokemonOverlayClient.init(modEventBus);
        }
    }
}
