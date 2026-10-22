package com.pixelmoneffectiveness.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pixelmoneffectiveness.client.telegram.TelegramEventWatcher;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

/**
 * In-world nametag renderer displaying live Pokemon despawn timers above their models.
 * Uses RenderLevelStageEvent (AFTER_TRANSLUCENT_BLOCKS) so it works regardless of custom Pixelmon renderers.
 * Toggled via /nbtPixelmonDebug command.
 */
public class PokemonDespawnDebugRenderer {

    public static boolean debugEnabled = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PokemonDespawnDebugRenderer::onRenderLevelStage);
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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!debugEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        long now = System.currentTimeMillis();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PixelmonEntity pixelmon) || !pixelmon.isAlive()) {
                continue;
            }

            double distSq = pixelmon.distanceToSqr(mc.player);
            // Render within 96 blocks for clean, high performance visibility
            if (distSq > 96.0 * 96.0) {
                continue;
            }

            CompoundTag data = getPokemonData(pixelmon, mc);

            String statusText;
            int textColor;

            // Check exemptions
            if (!TelegramEventWatcher.isWildPokemon(pixelmon)) {
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
                long sec = Math.max(0L, 240L - (pixelmon.tickCount / 20L));
                long m = sec / 60;
                long s = sec % 60;
                statusText = String.format("⏳ %d:%02d [Активен]", m, s);
                if (sec > 120) {
                    textColor = 0x55FF55;
                } else if (sec > 30) {
                    textColor = 0xFFFF55;
                } else {
                    textColor = 0xFF5555;
                }
            }

            // Billboard in-world position above model
            double lx = pixelmon.getX() - camPos.x;
            double ly = pixelmon.getY() + pixelmon.getBbHeight() + 0.85 - camPos.y;
            double lz = pixelmon.getZ() - camPos.z;

            poseStack.pushPose();
            poseStack.translate(lx, ly, lz);

            poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

            double dist = Math.sqrt(distSq);
            float scale = (float) Math.max(0.02f, Math.min(0.06f, dist * 0.0025f));
            poseStack.scale(-scale, -scale, scale);

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
                    15728880
            );

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }

    private static CompoundTag getPokemonData(PixelmonEntity pixelmon, Minecraft mc) {
        if (mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && mc.level != null) {
                ServerLevel sLevel = server.getLevel(mc.level.dimension());
                if (sLevel != null) {
                    Entity sEntity = sLevel.getEntity(pixelmon.getId());
                    if (sEntity == null && pixelmon.getUUID() != null) {
                        sEntity = sLevel.getEntity(pixelmon.getUUID());
                    }
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
