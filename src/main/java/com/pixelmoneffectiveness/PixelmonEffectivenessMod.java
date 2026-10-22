package com.pixelmoneffectiveness;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmoneffectiveness.client.autofarm.MintAutoFarmClient;
import com.pixelmoneffectiveness.client.battle.BattleMoveOverlayClient;
import com.pixelmoneffectiveness.client.battle.BattleSwitchOverlayClient;
import com.pixelmoneffectiveness.client.overlay.PokemonOverlayClient;
import com.pixelmoneffectiveness.client.radar.PokeLootRadarClient;
import com.pixelmoneffectiveness.client.raid.RaidMenuOverlayClient;
import com.pixelmoneffectiveness.client.render.PokemonDespawnDebugRenderer;
import com.pixelmoneffectiveness.command.NbtPixelmonDebugCommand;
import com.pixelmoneffectiveness.command.NightVisionCommand;
import com.pixelmoneffectiveness.handler.ApricornTreeHarvestHandler;
import com.pixelmoneffectiveness.handler.PokemonFreezeDespawnHandler;
import com.pixelmoneffectiveness.handler.PokemonSpawnCapReliefHandler;
import com.pixelmoneffectiveness.handler.UltraSpaceFlightHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
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
        NeoForge.EVENT_BUS.addListener(PokemonFreezeDespawnHandler::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(PokemonFreezeDespawnHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(PokemonSpawnCapReliefHandler::onServerTick);

        // Commands and persistent effects
        NeoForge.EVENT_BUS.addListener(NightVisionCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(NightVisionCommand::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(NightVisionCommand::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(NightVisionCommand::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(NbtPixelmonDebugCommand::onRegisterCommands);

        // Telegram Bot event watchers and server command
        NeoForge.EVENT_BUS.addListener(com.pixelmoneffectiveness.client.telegram.TelegramEventWatcher::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(com.pixelmoneffectiveness.client.telegram.TelegramEventWatcher::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(com.pixelmoneffectiveness.client.telegram.TelegramCommand::onRegisterCommands);

        // Ultra Space flight upward pull fix (counteracts Pixelmon's +0.07d/tick upward acceleration during flight)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, UltraSpaceFlightHandler::onEntityTickPost);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PokeLootRadarClient.init(modEventBus);
            PokemonOverlayClient.init(modEventBus);
            MintAutoFarmClient.init(modEventBus);
            BattleSwitchOverlayClient.init(modEventBus);
            RaidMenuOverlayClient.init(modEventBus);
            BattleMoveOverlayClient.init(modEventBus);
            PokemonDespawnDebugRenderer.init();

            modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> {
                event.register(com.pixelmoneffectiveness.client.telegram.TelegramSettingsScreen.OPEN_TELEGRAM_KEY);
            });
            NeoForge.EVENT_BUS.addListener(com.pixelmoneffectiveness.client.telegram.TelegramCommand::onRegisterClientCommands);
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level == null || mc.player == null) return;
                while (com.pixelmoneffectiveness.client.telegram.TelegramSettingsScreen.OPEN_TELEGRAM_KEY.consumeClick()) {
                    mc.setScreen(new com.pixelmoneffectiveness.client.telegram.TelegramSettingsScreen(null));
                }
            });
        }
    }
}
