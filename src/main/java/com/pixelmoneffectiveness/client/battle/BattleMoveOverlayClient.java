package com.pixelmoneffectiveness.client.battle;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import com.pixelmoneffectiveness.util.PixelmonEffectivenessHelper;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.Effectiveness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Handles visual enhancement of move selection buttons in Pixelmon battles:
 * - Shows calculated damage and enemy HP% directly on the move button
 * - Highlights the most effective attack with a golden frame and a "★ ТОП" badge
 * - Renders a detailed hover tooltip explaining damage breakdown, STAB, multipliers, and accuracy
 */
public class BattleMoveOverlayClient {

    private static BattleDamageCalculator.BattleDamageResult hoveredResult = null;

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(BattleMoveOverlayClient::onScreenRenderPost);
    }

    /**
     * Called by ChooseAttackMoveButtonMixin when formatting the text on the move button.
     */
    public static Component getButtonDisplayName(Effectiveness eff, Attack attack) {
        if (EffectivenessConfig.CONFIG == null || !EffectivenessConfig.CONFIG.enabled.get()) {
            return eff.displayName();
        }

        if (attack == null || attack.getMove() == null) {
            return PixelmonEffectivenessHelper.formatDisplayName(eff, attack);
        }

        if (!EffectivenessConfig.CONFIG.battleShowAttackDamage.get()) {
            // If damage display is disabled, still fix status moves so they don't say 2x
            if (attack.getAttackCategory() == com.pixelmonmod.pixelmon.api.battles.AttackCategory.STATUS) {
                return Component.literal("§7Статус (0)");
            }
            return PixelmonEffectivenessHelper.formatDisplayName(eff, attack);
        }

        BattleDamageCalculator.BattleDamageResult res = BattleDamageCalculator.getDamageResult(attack);
        if (res == null) {
            return PixelmonEffectivenessHelper.formatDisplayName(eff, attack);
        }

        if (res.isStatus) {
            return Component.literal("§7Статус (0)");
        }

        if (res.isImmune) {
            return Component.literal("§c0x §7(Иммун)");
        }

        String multStr = formatMultiplier(res.effectiveness);
        boolean highlightTop = EffectivenessConfig.CONFIG.battleHighlightBestAttack.get();

        String dmgStr;
        if (res.isGuaranteedKO) {
            dmgStr = "§e~" + res.damageAvg + " §c💥КО";
        } else if (res.isOneHitKO) {
            dmgStr = "§e~" + res.damageAvg + " §e💥~КО";
        } else {
            dmgStr = "§e~" + res.damageAvg + " §7(" + res.percentAvg + "%)";
        }

        if (res.isTop && highlightTop) {
            return Component.literal("§6★ ТОП " + multStr + " " + dmgStr);
        } else {
            return Component.literal(multStr + " " + dmgStr.replace("§e~", "§7~"));
        }
    }

    private static String formatMultiplier(double eff) {
        if (eff >= 4.0) return "§a4x";
        if (eff >= 2.0) return "§a2x";
        if (eff >= 1.0) return "§f1x";
        if (eff >= 0.5) return "§c0.5x";
        if (eff > 0.0) return "§c0.25x";
        return "§c0x";
    }

    /**
     * Called at TAIL of ChooseAttack$MoveButton.renderWidget.
     */
    public static void onRenderMoveButton(GuiGraphics graphics, Button button, Attack attack, int mouseX, int mouseY) {
        if (button == null || !button.visible || attack == null) return;
        if (EffectivenessConfig.CONFIG == null || !EffectivenessConfig.CONFIG.enabled.get()) return;

        BattleDamageCalculator.BattleDamageResult res = BattleDamageCalculator.getDamageResult(attack);
        if (res == null) return;

        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();

        // 1. Highlight Top Choice Attack with golden glow outline
        if (res.isTop && EffectivenessConfig.CONFIG.battleHighlightBestAttack.get()) {
            graphics.renderOutline(x, y, w, h, 0xFFFFD700);
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xAAFFAA00);
            graphics.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x55FFD700);
        }

        // 2. Track hover for tooltip rendering in ScreenEvent.Render.Post
        if (button.isHovered() && EffectivenessConfig.CONFIG.battleShowAttackTooltip.get()) {
            hoveredResult = res;
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (hoveredResult == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && hoveredResult != null) {
            event.getGuiGraphics().renderComponentTooltip(
                mc.font,
                hoveredResult.buildTooltip(),
                event.getMouseX(),
                event.getMouseY()
            );
        }
        hoveredResult = null;
    }
}
