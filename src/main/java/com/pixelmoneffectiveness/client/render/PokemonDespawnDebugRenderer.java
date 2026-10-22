package com.pixelmoneffectiveness.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pixelmoneffectiveness.handler.PokemonFreezeDespawnHandler;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

/**
 * In-world nametag renderer displaying live Pokemon despawn timers above their models.
 * Toggled via /nbtPixelmonDebug command.
 */
public class PokemonDespawnDebugRenderer {

    public static boolean debugEnabled = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PokemonDespawnDebugRenderer::onRenderLivingPost);
        NeoForge.EVENT_BUS.addListener(PokemonDespawnDebugRenderer::onRegisterClientCommands);
    }

    public static boolean toggleDebug() {
        debugEnabled = !debugEnabled;
        return debugEnabled;
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var exec = (com.mojang.brigadier.Command<net.minecraft.commands.CommandSourceStack>) ctx -> {
            boolean enabled = toggleDebug();
            sendFeedback(enabled);
            return 1;
        };

        dispatcher.register(Commands.literal("nbtPixelmonDebug").executes(exec));
        dispatcher.register(Commands.literal("nbtpixelmondebug").executes(exec));
        dispatcher.register(Commands.literal("nbtdebug").executes(exec));
    }

    public static void sendFeedback(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (enabled) {
                mc.player.displayClientMessage(Component.literal("§6[PixelmonDebug] §fОтображение деспавна над покемонами: §aВКЛЮЧЕНО"), false);
                mc.player.displayClientMessage(Component.literal("§7(Зелёный/Жёлтый/Красный = активный таймер 4 мин, Голубой = заморожен 10 мин, Золотой/Сияющий = босс/легендарный)"), false);
            } else {
                mc.player.displayClientMessage(Component.literal("§6[PixelmonDebug] §fОтображение деспавна над покемонами: §cВЫКЛЮЧЕНО"), false);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!debugEnabled) {
            return;
        }

        LivingEntity living = event.getEntity();
        if (!(living instanceof PixelmonEntity pixelmon) || !pixelmon.isAlive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        double distSq = pixelmon.distanceToSqr(mc.player);
        // Render within 120 blocks for clear visibility without screen clutter
        if (distSq > 120.0 * 120.0) {
            return;
        }

        CompoundTag data = getPokemonData(pixelmon, mc);
        long now = System.currentTimeMillis();

        String statusText;
        int textColor;

        // Check exemptions
        if (pixelmon.hasOwner() || pixelmon.getOwnerUUID() != null) {
            statusText = "⭐ [Приручен: ∞]";
            textColor = 0x55FF55;
        } else if (pixelmon.battleController != null) {
            statusText = "⚔ [В бою: ⏸]";
            textColor = 0xFFAAAA;
        } else if (pixelmon.hasCustomName() || pixelmon.isPersistenceRequired()) {
            statusText = "🏷 [Бирка: ∞]";
            textColor = 0xFFFFAA;
        } else if (isBoss(pixelmon)) {
            statusText = "👑 [Босс: ∞]";
            textColor = 0xFFFF55;
        } else if (isLegendary(pixelmon)) {
            statusText = "✨ [Легендарный: ∞]";
            textColor = 0x55FFFF;
        } else if (data != null && data.contains("pe_global_despawn_time")) {
            long globalDespawn = data.getLong("pe_global_despawn_time");
            long remainingMs = Math.max(0L, globalDespawn - now);
            long sec = remainingMs / 1000L;
            long m = sec / 60;
            long s = sec % 60;
            statusText = String.format("⏳ %d:%02d [Активен]", m, s);
            if (sec > 120) {
                textColor = 0x55FF55; // green
            } else if (sec > 30) {
                textColor = 0xFFFF55; // yellow
            } else {
                textColor = 0xFF5555; // red
            }
        } else if (data != null && data.contains("pe_last_seen_time")) {
            long lastSeen = data.getLong("pe_last_seen_time");
            long elapsed = now - lastSeen;
            long remainingMs = Math.max(0L, PokemonFreezeDespawnHandler.FAR_DESPAWN_TIMEOUT_MS - elapsed);
            long sec = remainingMs / 1000L;
            long m = sec / 60;
            long s = sec % 60;
            statusText = String.format("❄ %d:%02d [Заморожен]", m, s);
            textColor = 0x55FFFF; // cyan
        } else {
            statusText = "⏳ 4:00 [Активен]";
            textColor = 0x55FF55;
        }

        // Render above model
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        Camera camera = mc.gameRenderer.getMainCamera();

        poseStack.pushPose();
        float height = pixelmon.getBbHeight() + 0.65F;
        poseStack.translate(0.0D, height, 0.0D);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Font font = mc.font;
        float halfWidth = -font.width(statusText) / 2.0F;

        font.drawInBatch(
                statusText,
                halfWidth,
                0,
                textColor,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0x88000000,
                0xF000F0
        );

        poseStack.popPose();
    }

    private static CompoundTag getPokemonData(PixelmonEntity pixelmon, Minecraft mc) {
        if (mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && mc.level != null) {
                ServerLevel sLevel = server.getLevel(mc.level.dimension());
                if (sLevel != null) {
                    Entity sEntity = sLevel.getEntity(pixelmon.getId());
                    if (sEntity instanceof PixelmonEntity sPixelmon && (Object) sPixelmon instanceof IEntityExtension ext) {
                        return ext.getPersistentData();
                    }
                }
            }
        }
        if ((Object) pixelmon instanceof IEntityExtension ext) {
            return ext.getPersistentData();
        }
        return null;
    }

    private static boolean isBoss(PixelmonEntity pixelmon) {
        try {
            if (pixelmon.isBossPokemon()) return true;
            BossTier tier = pixelmon.getBossTier();
            if (tier != null && tier.isBoss()) return true;
        } catch (Throwable ignored) {}
        Pokemon p = pixelmon.getPokemon();
        return p != null && p.isBoss();
    }

    private static boolean isLegendary(PixelmonEntity pixelmon) {
        Pokemon p = pixelmon.getPokemon();
        return p != null && (p.isLegendary() || p.isMythical() || p.isUltraBeast());
    }
}
