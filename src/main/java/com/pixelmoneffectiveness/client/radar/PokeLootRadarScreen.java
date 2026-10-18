package com.pixelmoneffectiveness.client.radar;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class PokeLootRadarScreen extends Screen {

    private static final int DIALOG_WIDTH = 270;
    private static final int DIALOG_HEIGHT = 302;

    private EditBox searchBox;

    public PokeLootRadarScreen() {
        super(Component.literal("Radar Settings"));
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

        // --- Section 1: PokéLoot Tiers ---
        // Row 1: Master & Beast
        this.addRenderableWidget(createTierButton(PokeLootTier.MASTER, col1X, top + 37, btnWidth, 19, isRu));
        this.addRenderableWidget(createTierButton(PokeLootTier.BEAST, col2X, top + 37, btnWidth, 19, isRu));

        // Row 2: Ultra & Special
        this.addRenderableWidget(createTierButton(PokeLootTier.ULTRA, col1X, top + 58, btnWidth, 19, isRu));
        this.addRenderableWidget(createTierButton(PokeLootTier.SPECIAL, col2X, top + 58, btnWidth, 19, isRu));

        // Row 3: Poké & Hidden
        this.addRenderableWidget(createTierButton(PokeLootTier.POKE, col1X, top + 79, btnWidth, 19, isRu));
        this.addRenderableWidget(createHiddenButton(col2X, top + 79, btnWidth, 19, isRu));

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
        .bounds(col1X, top + 100, btnWidth, 18)
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
        .bounds(col2X, top + 100, btnWidth, 18)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Искать только Мастер, Бист и Ультра" : "Search only Master, Beast, and Ultra")))
        .build();
        this.addRenderableWidget(rareOnlyBtn);

        // --- Section 2: Pokémon Search ---
        searchBox = new EditBox(this.font, col1X, top + 137, 220, 18, Component.literal("Search"));
        searchBox.setMaxLength(64);
        String currentFilter = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.pokemonSearchFilter.get() : "";
        searchBox.setValue(currentFilter);
        searchBox.setHint(Component.literal(isRu ? "Имя: Pikachu, Mew, Пикачу..." : "Name: Pikachu, Mew..."));
        searchBox.setResponder(text -> {
            if (EffectivenessConfig.CONFIG != null) {
                EffectivenessConfig.CONFIG.pokemonSearchFilter.set(text);
                EffectivenessConfig.SPEC.save();
            }
        });
        this.addRenderableWidget(searchBox);

        Button clearBtn = Button.builder(Component.literal("✕"), btn -> {
            searchBox.setValue("");
        })
        .bounds(col1X + 224, top + 137, 26, 18)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Очистить поиск" : "Clear search")))
        .build();
        this.addRenderableWidget(clearBtn);

        // Pokémon Radar toggle & Rare Without Filter toggle
        boolean pokeActive = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.pokemonRadarEnabled.get();
        String pokeStatus = pokeActive ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String pokeLabel = (isRu ? "Радар пок.: " : "Poke radar: ") + pokeStatus;
        Button pokeToggle = Button.builder(
            Component.literal(pokeLabel),
            btn -> {
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.pokemonRadarEnabled.set(!pokeActive);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col1X, top + 158, btnWidth, 19)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Включить или отключить поиск покемонов на радаре" : "Toggle Pokémon radar tracking")))
        .build();
        this.addRenderableWidget(pokeToggle);

        boolean rareNoFilter = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.pokemonShowRareWithoutFilter.get();
        String rareStatus = rareNoFilter ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        String rareLabel = (isRu ? "Редкие б/фильтра: " : "Rare w/o filter: ") + rareStatus;
        Button rareToggle = Button.builder(
            Component.literal(rareLabel),
            btn -> {
                if (EffectivenessConfig.CONFIG != null) {
                    EffectivenessConfig.CONFIG.pokemonShowRareWithoutFilter.set(!rareNoFilter);
                    EffectivenessConfig.SPEC.save();
                }
                rebuildScreen();
            }
        )
        .bounds(col2X, top + 158, btnWidth, 19)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Показывать Шайни, Легендарных и Боссов, даже если поиск пуст" : "Show Shiny, Legendary, and Bosses when search is empty")))
        .build();
        this.addRenderableWidget(rareToggle);

        // --- Section 3: Radar Display Options ---
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
        .bounds(col1X, top + 195, btnWidth, 19)
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
        .bounds(col2X, top + 195, btnWidth, 19)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Экранная панель со стрелками и дистанцией" : "On-screen direction and distance HUD panel")))
        .build();
        this.addRenderableWidget(hudToggle);

        // Row 7: 3D ESP & Sound Alert
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
        .bounds(col1X, top + 216, btnWidth, 19)
        .tooltip(Tooltip.create(Component.literal(isRu ? "3D метки лута и покемонов сквозь блоки прямо в мире" : "In-world 3D markers through blocks")))
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
        .bounds(col2X, top + 216, btnWidth, 19)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Звуковой сигнал при нахождении редких покемонов и лута" : "Audio chime when rare pokemon or loot is found")))
        .build();
        this.addRenderableWidget(sndToggle);

        // Row 8: Hide on Cooldown toggle
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
        .bounds(left + 10, top + 237, 250, 19)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Автоматически скрывать с радара залутанные сундуки на перезарядке" : "Automatically hide looted chests on cooldown from radar")))
        .build();
        this.addRenderableWidget(cdToggle);

        // --- Bottom Actions ---
        Button overlayBtn = Button.builder(
            Component.literal(isRu ? "⚙ Инфо-панель" : "⚙ Overlay"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new com.pixelmoneffectiveness.client.overlay.PokemonOverlaySettingsScreen());
                }
            }
        )
        .bounds(left + 10, top + 270, 122, 22)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Настройки инфо-панели покемона при наведении (Cobblemon HUD)" : "Configure Pokémon info overlay tooltip (Cobblemon HUD)")))
        .build();
        this.addRenderableWidget(overlayBtn);

        Button doneBtn = Button.builder(
            Component.literal(isRu ? "✔ Готово" : "✔ Done"),
            btn -> this.onClose()
        )
        .bounds(left + 138, top + 270, 122, 22)
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 256) { // ESCAPE unfocuses
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
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
        guiGraphics.fill(left + 8, top + 23, left + DIALOG_WIDTH - 8, top + 24, 0x33FFFFFF);
        guiGraphics.fill(left + 8, top + 122, left + DIALOG_WIDTH - 8, top + 123, 0x33FFFFFF);
        guiGraphics.fill(left + 8, top + 181, left + DIALOG_WIDTH - 8, top + 182, 0x33FFFFFF);
        guiGraphics.fill(left + 8, top + 261, left + DIALOG_WIDTH - 8, top + 262, 0x33FFFFFF);

        boolean isRu = isRussianLanguage();

        // Main Title
        String titleText = isRu ? "⚡ Радар: Настройки" : "⚡ Radar Settings";
        int titleWidth = this.font.width(titleText);
        guiGraphics.drawString(this.font, titleText, left + (DIALOG_WIDTH - titleWidth) / 2, top + 9, 0xFFFFFF55, false);

        // Section labels
        String tiersLabel = isRu ? "§7Типы лута для поиска:" : "§7Loot tiers to search:";
        guiGraphics.drawString(this.font, tiersLabel, left + 10, top + 26, 0xFFAAAAAA, false);

        String pokemonLabel = isRu ? "§7Поиск покемонов по имени:" : "§7Pokémon search by name:";
        guiGraphics.drawString(this.font, pokemonLabel, left + 10, top + 125, 0xFFAAAAAA, false);

        String optionsLabel = isRu ? "§7Опции радара:" : "§7Radar display options:";
        guiGraphics.drawString(this.font, optionsLabel, left + 10, top + 184, 0xFFAAAAAA, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
