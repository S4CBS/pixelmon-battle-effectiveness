package com.pixelmoneffectiveness.client.raid;

import com.pixelmoneffectiveness.client.battle.PokemonMatchupRating;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmonmod.api.client.screen.StatefulScreen;
import com.pixelmonmod.api.client.screen.util.ScreenArea;
import com.pixelmonmod.pixelmon.battles.raids.data.RaidData;
import com.pixelmonmod.pixelmon.client.gui.raids.TeraRaidStartScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public class RaidMenuOverlayClient {

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(RaidMenuOverlayClient::onScreenRenderPost);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        boolean isTera = (screen instanceof TeraRaidStartScreen);
        boolean isStandard = (screen instanceof StatefulScreen stateful && "raid_start".equals(stateful.identifier()));

        if (!isTera && !isStandard) {
            return;
        }

        if (EffectivenessConfig.CONFIG != null && !EffectivenessConfig.CONFIG.raidEffectivenessEnabled.get()) {
            return;
        }

        RaidData raid = RaidEffectivenessHelper.getRaidData(screen);
        if (raid == null) return;

        RaidEffectivenessHelper.RaidEvaluationResult result = RaidEffectivenessHelper.evaluate(raid);
        if (result == null || result.best == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        PokemonMatchupRating best = result.best;
        PokemonMatchupRating hoveredRating = null;

        if (isTera) {
            TeraRaidStartScreen teraScreen = (TeraRaidStartScreen) screen;
            ScreenArea area = RaidEffectivenessHelper.getTeraLayout(teraScreen);
            boolean showParty = RaidEffectivenessHelper.isTeraShowingParty(teraScreen);

            // 1. Top recommendation banner above card
            int bannerX = area.x();
            int bannerY = Math.max(3, area.y() - 24);
            int bannerW = area.width();
            int bannerH = 18;

            graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, 0xEE120E22);
            graphics.renderOutline(bannerX, bannerY, bannerW, bannerH, 0xFF9B59B6);

            String multTag = " §a[" + best.getMultiplierString() + "]";
            String bossTypeInfo = (result.isTera && result.boss.getTeraType() != null)
                ? (" §7(Тера: §d" + result.boss.getTeraType().value().name().getString() + "§7)")
                : "";
            String bannerText = "§d[⚔ Тера Рейд] §eСамый эффективный: §6★ " + best.pokemonName
                + " §7(Ур. " + best.level + ")" + multTag + " §7| Атака: §f" + best.bestAttackName + bossTypeInfo;

            graphics.drawString(font, bannerText, bannerX + 6, bannerY + 5, 0xFFFFFFFF, false);

            // 2. If party is shown, render badges and highlight over slots
            if (showParty && result.ratings != null) {
                int partyX = area.x(0.055f);
                int partyY = area.y(0.84f);
                int slotSize = Math.max(18, (int) (area.height() * 0.065f));
                int gap = Math.max(4, slotSize / 5);

                for (int i = 0; i < result.ratings.length; i++) {
                    PokemonMatchupRating rating = result.ratings[i];
                    if (rating == null) continue;

                    int slotLeft = partyX + i * (slotSize + gap);
                    int slotTop = partyY;

                    if (rating.isMostEffective) {
                        // Golden border around the slot
                        graphics.renderOutline(slotLeft - 1, slotTop - 1, slotSize + 2, slotSize + 2, 0xFFFFD700);

                        // Top star badge
                        String topBadge = "§6★ ТОП";
                        int tw = font.width(topBadge);
                        graphics.drawString(font, topBadge, slotLeft + (slotSize - tw) / 2, slotTop - 10, 0xFFFFFFFF, true);
                    }

                    // Multiplier tag at bottom of slot
                    String shortTag = rating.getShortBadge();
                    if (!rating.isMostEffective) {
                        int bw = font.width(shortTag);
                        graphics.drawString(font, shortTag, slotLeft + (slotSize - bw) / 2, slotTop + slotSize + 2, 0xFFFFFFFF, true);
                    }

                    // Hover check
                    if (mouseX >= slotLeft && mouseX <= slotLeft + slotSize && mouseY >= slotTop && mouseY <= slotTop + slotSize) {
                        hoveredRating = rating;
                    }
                }
            }
        } else {
            // Standard Dynamax / Regular Raid
            int bannerW = Math.min(380, screen.width - 20);
            int bannerX = (screen.width - bannerW) / 2;
            int bannerY = 6;
            int bannerH = 18;

            graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, 0xEE221111);
            graphics.renderOutline(bannerX, bannerY, bannerW, bannerH, 0xFFFF5555);

            String multTag = " §a[" + best.getMultiplierString() + "]";
            String bannerText = "§c[⚔ Рейд] §eСамый эффективный: §6★ " + best.pokemonName
                + " §7(Ур. " + best.level + ")" + multTag + " §7| Атака: §f" + best.bestAttackName;

            graphics.drawString(font, bannerText, bannerX + 6, bannerY + 5, 0xFFFFFFFF, false);
        }

        // 3. Render detailed hover tooltip if hovering over a party slot
        if (hoveredRating != null) {
            String bossName = result.boss.getDisplayName().getString();
            graphics.renderComponentTooltip(font, hoveredRating.buildTooltip("Выбор для рейда", bossName), mouseX, mouseY);
        }
    }
}
