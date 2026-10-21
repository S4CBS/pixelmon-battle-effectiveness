package com.pixelmoneffectiveness.client.battle;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import com.pixelmonmod.pixelmon.client.gui.battles.battleScreens.PokemonMenuButton;
import com.pixelmonmod.pixelmon.client.gui.battles.battleScreens.screens.pokemon.ChoosePokemon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BattleSwitchOverlayClient {

    private static UUID lastChatEnemyUUID = null;
    private static long lastChatTime = 0;

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(BattleSwitchOverlayClient::onScreenRenderPost);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChoosePokemon chooseScreen)) {
            return;
        }

        if (EffectivenessConfig.CONFIG != null && !EffectivenessConfig.CONFIG.battleSwitchEffectivenessEnabled.get()) {
            return;
        }

        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm == null) return;

        List<PokemonMenuButton> buttons = chooseScreen.getButtons();
        BattleSwitchEffectivenessHelper.SwitchEvaluationResult result =
            BattleSwitchEffectivenessHelper.evaluateSwitchOptions(bm, buttons);

        if (result == null || result.enemy == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        PokemonMatchupRating best = result.best;

        // 1. Send chat notification once when switch screen opens
        if (best != null && EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.battleSwitchChatNotification.get()) {
            checkSendChat(mc, result.enemy, best);
        }

        // 2. Render Header banner above the switch menu
        if (best != null) {
            int bannerX = 5;
            int bannerY = Math.max(4, chooseScreen.height - 256);
            int bannerW = 240;
            int bannerH = 15;

            // Semi-transparent background & border
            graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, 0xEE111622);
            graphics.renderOutline(bannerX, bannerY, bannerW, bannerH, 0xFF4488EE);

            String multTag = " §a[" + best.getMultiplierString() + "]";
            String bannerText = "§e⚔ Самый эффективный: §6★ " + best.pokemonName + multTag;
            graphics.drawString(font, bannerText, bannerX + 5, bannerY + 4, 0xFFFFFFFF, false);
        }

        // 3. Render badges next to each PokemonMenuButton
        PokemonMatchupRating hoveredRating = null;

        if (buttons != null) {
            for (PokemonMenuButton btn : buttons) {
                if (!btn.visible) continue;
                PixelmonClientData pig = btn.getPig();
                if (pig == null || pig.pokemonUUID == null) continue;

                PokemonMatchupRating rating = result.getRating(pig.pokemonUUID);
                if (rating == null) continue;

                int badgeX = btn.getX() + btn.getWidth() + 4;
                int badgeY = btn.getY() + 3;
                int badgeW = 74;
                int badgeH = 20;

                int bgColor;
                int borderColor;
                String line1;
                String line2;

                if (rating.isMostEffective) {
                    // Golden highlight outline around the button itself
                    graphics.renderOutline(btn.getX() - 1, btn.getY() - 1, btn.getWidth() + 2, btn.getHeight() + 2, 0xFFFFD700);

                    bgColor = 0xDD2A2200;
                    borderColor = 0xFFFFD700;
                    line1 = "§6★ ТОП ВЫБОР";
                    line2 = "§a" + rating.getMultiplierString();
                } else if (rating.bestMultiplier >= 2.0) {
                    bgColor = 0xDD002211;
                    borderColor = 0xFF00AA55;
                    line1 = "§a✔ Супер-эффект";
                    line2 = "§a" + rating.getMultiplierString();
                } else if (rating.bestMultiplier == 1.0) {
                    bgColor = 0xDD1A1A1A;
                    borderColor = 0xFF555555;
                    line1 = "§7Нейтрально";
                    line2 = "§f1.0x Норм";
                } else if (rating.bestMultiplier > 0.0 && rating.bestMultiplier < 1.0) {
                    bgColor = 0xDD220000;
                    borderColor = 0xFFAA3333;
                    line1 = "§cСлабо";
                    line2 = "§c" + rating.getMultiplierString();
                } else {
                    bgColor = 0xDD110011;
                    borderColor = 0xFF550055;
                    line1 = "§4Иммунитет";
                    line2 = "§40x урона";
                }

                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, bgColor);
                graphics.renderOutline(badgeX, badgeY, badgeW, badgeH, borderColor);

                graphics.drawString(font, line1, badgeX + 4, badgeY + 2, 0xFFFFFFFF, false);
                graphics.drawString(font, line2, badgeX + 4, badgeY + 11, 0xFFFFFFFF, false);

                // Check mouse hover for tooltip
                if (mouseX >= btn.getX() && mouseX <= badgeX + badgeW && mouseY >= btn.getY() && mouseY <= btn.getY() + btn.getHeight()) {
                    hoveredRating = rating;
                }
            }
        }

        // 4. Render tooltip on top of all buttons if hovering
        if (hoveredRating != null) {
            String enemyName = result.enemy.getDisplayName().getString();
            graphics.renderComponentTooltip(font, hoveredRating.buildTooltip("Смена покемона", enemyName), mouseX, mouseY);
        }
    }

    private static void checkSendChat(Minecraft mc, PixelmonClientData enemy, PokemonMatchupRating best) {
        if (mc.player == null || enemy == null || best == null) return;
        UUID enemyUuid = enemy.pokemonUUID;
        long now = System.currentTimeMillis();

        if (enemyUuid != null && enemyUuid.equals(lastChatEnemyUUID) && (now - lastChatTime < 5000)) {
            return;
        }

        lastChatEnemyUUID = enemyUuid;
        lastChatTime = now;

        String enemyName = enemy.getDisplayName().getString();
        String multStr = String.format(Locale.ROOT, "%.1fx", best.bestMultiplier);
        mc.player.sendSystemMessage(Component.literal(
            "§b[Pixelmon] §eСамый эффективный покемон против §c" + enemyName + "§e: §6★ " + best.pokemonName
            + " §7(лучшая атака: §f" + best.bestAttackName + " §a" + multStr + "§7)"
        ));
    }
}
