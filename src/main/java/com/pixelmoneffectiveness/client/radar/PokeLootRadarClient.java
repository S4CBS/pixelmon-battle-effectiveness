package com.pixelmoneffectiveness.client.radar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pixelmonmod.pixelmon.blocks.enums.EnumPokeChestType;
import com.pixelmonmod.pixelmon.blocks.enums.EnumPokechestVisibility;
import com.pixelmonmod.pixelmon.blocks.tileentity.PokeChestTileEntity;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

public class PokeLootRadarClient {

    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
        "key.pixelmoneffectiveness.toggle_radar",
        79, // GLFW_KEY_O
        "key.categories.pixelmoneffectiveness"
    );

    private static boolean radarActive = true;
    private static int scanCooldown = 0;
    private static List<PokeLootEntry> cachedEntries = new ArrayList<>();
    private static final Set<BlockPos> knownHighTierPositions = new HashSet<>();

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(PokeLootRadarClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(PokeLootRadarClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PokeLootRadarClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(PokeLootRadarClient::onRenderLevelStage);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cachedEntries.clear();
            return;
        }

        while (TOGGLE_KEY.consumeClick()) {
            radarActive = !radarActive;
            boolean isRu = mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
            String text = radarActive
                ? (isRu ? "§e[Радар покелутов] §aВключен" : "§e[PokéLoot Radar] §aEnabled")
                : (isRu ? "§e[Радар покелутов] §cВыключен" : "§e[PokéLoot Radar] §cDisabled");
            mc.player.displayClientMessage(Component.literal(text), true);
        }

        if (!radarActive || (EffectivenessConfig.CONFIG != null && !EffectivenessConfig.CONFIG.radarEnabled.get())) {
            cachedEntries.clear();
            return;
        }

        if (scanCooldown-- <= 0) {
            scanCooldown = 10; // Scan twice per second (every 10 ticks)
            scanWorld(mc);
        } else {
            updateDistancesAndAngles(mc);
        }
    }

    private static void scanWorld(Minecraft mc) {
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;

        int pChunkX = player.getBlockX() >> 4;
        int pChunkZ = player.getBlockZ() >> 4;
        int chunkRadius = 12;
        if (EffectivenessConfig.CONFIG != null) {
            chunkRadius = Math.max(2, Math.min(24, EffectivenessConfig.CONFIG.radarScanRadius.get()));
        }

        List<PokeLootEntry> found = new ArrayList<>();
        boolean foundNewHighTier = false;

        for (int cx = pChunkX - chunkRadius; cx <= pChunkX + chunkRadius; cx++) {
            for (int cz = pChunkZ - chunkRadius; cz <= pChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                if (blockEntities.isEmpty()) continue;

                for (BlockEntity be : blockEntities.values()) {
                    if (be instanceof PokeChestTileEntity chest) {
                        BlockPos pos = chest.getBlockPos();
                        EnumPokeChestType type = chest.getChestType();
                        PokeLootTier tier = PokeLootTier.fromChestType(type);

                        if (EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.radarFilterNormalPoke.get()) {
                            if (tier == PokeLootTier.POKE) {
                                continue;
                            }
                        }

                        boolean isHidden = (chest.getVisibility() == EnumPokechestVisibility.Hidden);
                        double dx = pos.getX() + 0.5 - player.getX();
                        double dy = pos.getY() + 0.5 - player.getY();
                        double dz = pos.getZ() + 0.5 - player.getZ();
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        double relAngle = calculateRelativeAngle(dx, dz, player.getYRot());

                        found.add(new PokeLootEntry(pos, tier, isHidden, dist, relAngle));

                        if ((tier == PokeLootTier.MASTER || tier == PokeLootTier.BEAST) && !knownHighTierPositions.contains(pos)) {
                            knownHighTierPositions.add(pos);
                            foundNewHighTier = true;
                        }
                    }
                }
            }
        }

        Collections.sort(found);
        cachedEntries = found;

        if (foundNewHighTier && EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.radarSoundAlert.get()) {
            level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    private static void updateDistancesAndAngles(Minecraft mc) {
        Player player = mc.player;
        if (player == null) return;

        for (PokeLootEntry entry : cachedEntries) {
            double dx = entry.pos.getX() + 0.5 - player.getX();
            double dy = entry.pos.getY() + 0.5 - player.getY();
            double dz = entry.pos.getZ() + 0.5 - player.getZ();
            entry.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            entry.relativeAngle = calculateRelativeAngle(dx, dz, player.getYRot());
        }
    }

    private static double calculateRelativeAngle(double dx, double dz, float playerYaw) {
        double angleRad = Math.atan2(dz, dx);
        double angleDeg = Math.toDegrees(angleRad) - 90.0;
        double diff = (angleDeg - playerYaw) % 360.0;
        if (diff < -180.0) diff += 360.0;
        if (diff > 180.0) diff -= 360.0;
        return diff;
    }

    private static String getDirectionArrow(double diff) {
        if (diff >= -22.5 && diff < 22.5) return "▲";
        if (diff >= 22.5 && diff < 67.5) return "↗";
        if (diff >= 67.5 && diff < 112.5) return "▶";
        if (diff >= 112.5 && diff < 157.5) return "↘";
        if (diff >= -67.5 && diff < -22.5) return "↖";
        if (diff >= -112.5 && diff < -67.5) return "◀";
        if (diff >= -157.5 && diff < -112.5) return "↙";
        return "▼";
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!radarActive) return;
        if (EffectivenessConfig.CONFIG != null && (!EffectivenessConfig.CONFIG.radarEnabled.get() || !EffectivenessConfig.CONFIG.radarHudEnabled.get())) {
            return;
        }
        if (cachedEntries.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = guiGraphics.guiWidth();
        int padding = 6;
        int boxWidth = 185;
        int maxRows = Math.min(6, cachedEntries.size());
        int boxHeight = 16 + maxRows * 11;

        int startX = screenWidth - boxWidth - 10;
        int startY = 10;

        // Background box with neat border
        guiGraphics.fill(startX, startY, startX + boxWidth, startY + boxHeight, 0x88000000);
        guiGraphics.fill(startX, startY, startX + boxWidth, startY + 1, 0x55FFFFFF);
        guiGraphics.fill(startX, startY + boxHeight - 1, startX + boxWidth, startY + boxHeight, 0x55FFFFFF);
        guiGraphics.fill(startX, startY, startX + 1, startY + boxHeight, 0x55FFFFFF);
        guiGraphics.fill(startX + boxWidth - 1, startY, startX + boxWidth, startY + boxHeight, 0x55FFFFFF);

        boolean isRu = mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");

        // Header
        String title = (isRu ? "§e⚡ Радар PokéLoot §7(" : "§e⚡ PokéLoot Radar §7(") + cachedEntries.size() + ")";
        guiGraphics.drawString(font, title, startX + padding, startY + 4, 0xFFFFFFFF, false);

        int rowY = startY + 16;
        for (int i = 0; i < maxRows; i++) {
            PokeLootEntry entry = cachedEntries.get(i);
            String arrow = getDirectionArrow(entry.relativeAngle);
            String name = entry.tier.getDisplayName(isRu);
            String hiddenTag = entry.isHidden ? (isRu ? " §7[Скрыт]" : " §7[H]") : "";
            String distStr = (int) Math.round(entry.distance) + "m";

            int yDiff = entry.pos.getY() - mc.player.getBlockY();
            String yTag = (yDiff > 2) ? " §7↑" : (yDiff < -2) ? " §7↓" : "";

            String leftText = entry.tier.getIconPrefix() + " " + name + hiddenTag;
            String rightText = "§f" + distStr + yTag + " §b" + arrow;

            guiGraphics.drawString(font, leftText, startX + padding, rowY, entry.tier.getHudColor(), false);
            int rightWidth = font.width(rightText);
            guiGraphics.drawString(font, rightText, startX + boxWidth - padding - rightWidth, rowY, 0xFFFFFFFF, false);

            rowY += 11;
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!radarActive) return;
        if (EffectivenessConfig.CONFIG != null && (!EffectivenessConfig.CONFIG.radarEnabled.get() || !EffectivenessConfig.CONFIG.radarEsp3dEnabled.get())) {
            return;
        }
        if (cachedEntries.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        boolean isRu = mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");

        for (PokeLootEntry entry : cachedEntries) {
            if (entry.distance > 128.0) {
                continue;
            }

            double lx = entry.pos.getX() + 0.5 - camPos.x;
            double ly = entry.pos.getY() + 1.2 - camPos.y;
            double lz = entry.pos.getZ() + 0.5 - camPos.z;

            poseStack.pushPose();
            poseStack.translate(lx, ly, lz);

            poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

            float scale = (float) Math.max(0.02f, Math.min(0.06f, entry.distance * 0.0025f));
            poseStack.scale(-scale, -scale, scale);

            String name = entry.tier.getDisplayName(isRu);
            String hiddenTag = entry.isHidden ? (isRu ? " [Скрытый]" : " [Hidden]") : "";
            String label = entry.tier.getIconPrefix() + " " + name + " " + (int) Math.round(entry.distance) + "m" + hiddenTag;
            float textWidth = font.width(label);

            font.drawInBatch(
                label,
                -textWidth / 2f,
                0,
                entry.tier.getColorRgb(),
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
}
