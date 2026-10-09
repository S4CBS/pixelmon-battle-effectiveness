package com.pixelmoneffectiveness.client.radar;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class PokeLootRadarScreen extends Screen {

    private static final int DIALOG_WIDTH = 270;
    private static final int DIALOG_HEIGHT = 252;

    public PokeLootRadarScreen() {
        super(Component.literal("PokéLoot Radar"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        boolean isRu = isRussianLanguage();
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        int col1X = left + 10;
        int col2X = left + 138;
        int btnWidth = 122;

        // Row 1: Master & Beast
        this.addRenderableWidget(createTierButton(PokeLootTier.MASTER, col1X, top + 38, btnWidth, 20, isRu));
        this.addRenderableWidget(createTierButton(PokeLootTier.BEAST, col2X, top + 38, btnWidth, 20, isRu));

        // Row 2: Ultra & Special
        this.addRenderableWidget(createTierButton(PokeLootTier.ULTRA, col1X, top + 61, btnWidth, 20, isRu));
        this.addRenderableWidget(createTierButton(PokeLootTier.SPECIAL, col2X, top + 61, btnWidth, 20, isRu));

        // Row 3: Poké & Hidden
        this.addRenderableWidget(createTierButton(PokeLootTier.POKE, col1X, top + 84, btnWidth, 20, isRu));
        this.addRenderableWidget(createHiddenButton(col2X, top + 84, btnWidth, 20, isRu));

        // Row 4 (Presets): All ON & Rare Only
        Button allOnBtn = Button.builder(
            Component.literal(isRu ? "✓ Все типы" : "✓ Enable All"),
            btn -> {
                for (PokeLootTier tier : PokeLootTier.values()) {
                    tier.setSearchEnabled(true);
                }
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.searchHidden.set(true);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col1X, top + 107, btnWidth, 18)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Включить поиск всех типов сундуков" : "Search for all chest types")))
        .build();
        this.addRenderableWidget(allOnBtn);

        Button rareOnlyBtn = Button.builder(
            Component.literal(isRu ? "★ Только редкие" : "★ Rare Only"),
            btn -> {
                PokeLootTier.MASTER.setSearchEnabled(true);
                PokeLootTier.BEAST.setSearchEnabled(true);
                PokeLootTier.ULTRA.setSearchEnabled(true);
                PokeLootTier.SPECIAL.setSearchEnabled(false);
                PokeLootTier.POKE.setSearchEnabled(false);
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.searchHidden.set(true);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col2X, top + 107, btnWidth, 18)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Искать только Мастер, Бист и Ультра" : "Search only Master, Beast, and Ultra")))
        .build();
        this.addRenderableWidget(rareOnlyBtn);

        // Row 5: Master Radar toggle & HUD toggle
        boolean rActive = PokeLootRadarClient.isRadarActive();
        String rStatus = rActive ? (isRu ? "§aВКЛ" : "§cВЫКЛ") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String rLabel = (isRu ? "Радар: " : "Radar: ") + rStatus;
        Button radarToggle = Button.builder(
            Component.literal(rLabel),
            btn -> {
                boolean newState = !PokeLootRadarClient.isRadarActive();
                PokeLootRadarClient.setRadarActive(newState);
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.radarEnabled.set(newState);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col1X, top + 146, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Главное включение/отключение радара (клавиша O)" : "Master radar toggle (key O)")))
        .build();
        this.addRenderableWidget(radarToggle);

        boolean hudOn = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.radarHudEnabled.get();
        String hudStatus = hudOn ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String hudLabel = (isRu ? "HUD компас: " : "HUD panel: ") + hudStatus;
        Button hudToggle = Button.builder(
            Component.literal(hudLabel),
            btn -> {
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.radarHudEnabled.set(!hudOn);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col2X, top + 146, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Экранная панель со стрелками и дистанцией" : "On-screen direction and distance HUD panel")))
        .build();
        this.addRenderableWidget(hudToggle);

        // Row 6: 3D ESP & Sound Alert
        boolean espOn = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.radarEsp3dEnabled.get();
        String espStatus = espOn ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String espLabel = (isRu ? "3D метки: " : "3D ESP: ") + espStatus;
        Button espToggle = Button.builder(
            Component.literal(espLabel),
            btn -> {
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.radarEsp3dEnabled.set(!espOn);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col1X, top + 169, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "3D метки лута сквозь блоки прямо в мире" : "In-world 3D markers through blocks")))
        .build();
        this.addRenderableWidget(espToggle);

        boolean sndOn = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.radarSoundAlert.get();
        String sndStatus = sndOn ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String sndLabel = (isRu ? "Звук редких: " : "Sound alert: ") + sndStatus;
        Button sndToggle = Button.builder(
            Component.literal(sndLabel),
            btn -> {
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.radarSoundAlert.set(!sndOn);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col2X, top + 169, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Звуковой сигнал при нахождении Мастер или Бист лута" : "Audio chime when Master or Beast loot is found")))
        .build();
        this.addRenderableWidget(sndToggle);

        // Row 7: Hide on Cooldown toggle
        boolean hideCd = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.radarHideOnCooldown.get();
        String cdStatus = hideCd ? (isRu ? "§aВКЛ (Скрывать)" : "§aON (Hide)") : (isRu ? "§cВЫКЛ (Показывать)" : "§cOFF (Show)");
        String cdLabel = (isRu ? "Сундуки на КД: " : "Chests on CD: ") + cdStatus;
        Button cdToggle = Button.builder(
            Component.literal(cdLabel),
            btn -> {
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.radarHideOnCooldown.set(!hideCd);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(left + 10, top + 192, 250, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Автоматически скрывать с радара залутанные сундуки на перезарядке" : "Automatically hide looted chests on cooldown from radar")))
        .build();
        this.addRenderableWidget(cdToggle);

        // Row 8: Overlay Settings & Done buttons
        Button overlayBtn = Button.builder(
            Component.literal(isRu ? "⚙ Инфо-панель" : "⚙ Overlay"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new com.pixelmoneffectiveness.client.overlay.PokemonOverlaySettingsScreen());
                }
            }
        )
        .bounds(left + 10, top + 218, 122, 22)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Настройки инфо-панели покемона при наведении (Cobblemon HUD)" : "Configure Pokémon info overlay tooltip (Cobblemon HUD)")))
        .build();
        this.addRenderableWidget(overlayBtn);

        Button doneBtn = Button.builder(
            Component.literal(isRu ? "✔ Готово" : "✔ Done"),
            btn -> this.onClose()
        )
        .bounds(left + 138, top + 218, 122, 22)
        .build();
        this.addRenderableWidget(doneBtn);
    }

    private Button createTierButton(PokeLootTier tier, int x, int y, int width, int height, boolean isRu) {
        boolean enabled = tier.isSearchEnabled();
        String tierName = tier.getDisplayName(isRu);
        String status = enabled ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String prefix = enabled ? tier.getIconPrefix() : "§8[•]";
        String label = prefix + " " + tierName + ": " + status;

        String tooltipText = isRu
            ? (enabled ? "Нажмите, чтобы скрыть " + tierName : "Нажмите, чтобы искать " + tierName)
            : (enabled ? "Click to hide " + tierName : "Click to search for " + tierName);

        return Button.builder(Component.literal(label), btn -> {
            boolean newState = !tier.isSearchEnabled();
            tier.setSearchEnabled(newState);
            rebuildScreen();
        })
        .bounds(x, y, width, height)
        .tooltip(Tooltip.create(Component.literal(tooltipText)))
        .build();
    }

    private Button createHiddenButton(int x, int y, int width, int height, boolean isRu) {
        boolean enabled = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.searchHidden.get();
        String name = isRu ? "Скрытый" : "Hidden";
        String status = enabled ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String prefix = enabled ? "§7[?]" : "§8[?]";
        String label = prefix + " " + name + ": " + status;

        String tooltipText = isRu
            ? (enabled ? "Нажмите, чтобы скрыть скрытые сундуки" : "Нажмите, чтобы искать скрытые сундуки")
            : (enabled ? "Click to hide invisible hidden chests" : "Click to search for invisible hidden chests");

        return Button.builder(Component.literal(label), btn -> {
            if (EffectivenessConfig.CONFIG != null) {
                EffectivenessConfig.CONFIG.searchHidden.set(!enabled);
                EffectivenessConfig.SPEC.save();
            }
            rebuildScreen();
        })
        .bounds(x, y, width, height)
        .tooltip(Tooltip.create(Component.literal(tooltipText)))
        .build();
    }

    private void rebuildScreen() {
        this.clearWidgets();
        this.init();
    }

    private boolean isRussianLanguage() {
        return this.minecraft != null
            && this.minecraft.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        // Dark modal dialog box
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xEE11151F);

        // Accent top border
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + 2, 0xFF3B82F6);
        // Outer borders
        guiGraphics.fill(left, top + DIALOG_HEIGHT - 1, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF1E293B);
        guiGraphics.fill(left, top, left + 1, top + DIALOG_HEIGHT, 0xFF1E293B);
        guiGraphics.fill(left + DIALOG_WIDTH - 1, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF1E293B);

        // Horizontal dividers
        guiGraphics.fill(left + 8, top + 24, left + DIALOG_WIDTH - 8, top + 25, 0x33FFFFFF);
        guiGraphics.fill(left + 8, top + 132, left + DIALOG_WIDTH - 8, top + 133, 0x33FFFFFF);

        boolean isRu = isRussianLanguage();

        // Main Title
        String titleText = isRu ? "⚡ Радар PokéLoot: Настройки" : "⚡ PokéLoot Radar Settings";
        int titleWidth = this.font.width(titleText);
        guiGraphics.drawString(this.font, titleText, left + (DIALOG_WIDTH - titleWidth) / 2, top + 9, 0xFFFFFF55, false);

        // Section labels
        String tiersLabel = isRu ? "§7Типы лута для поиска:" : "§7Loot tiers to search:";
        guiGraphics.drawString(this.font, tiersLabel, left + 10, top + 27, 0xFFAAAAAA, false);

        String optionsLabel = isRu ? "§7Опции отображения:" : "§7Display options:";
        guiGraphics.drawString(this.font, optionsLabel, left + 10, top + 135, 0xFFAAAAAA, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
