package com.pixelmoneffectiveness.client.overlay;

import com.pixelmoneffectiveness.client.gui.GuiSlider;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PokemonOverlaySettingsScreen extends Screen {

    private static final int DIALOG_WIDTH = 450;
    private static final int DIALOG_HEIGHT = 295;

    // Color presets for background
    private static final int[] BG_COLOR_PRESETS = {
        0x141726, // Cobblemon dark slate navy (default)
        0x0A0A0A, // Deep charcoal black
        0x0A192F, // Midnight blue
        0x1A0B2E, // Royal purple
        0x0B2016, // Forest dark
        0x20232A, // Graphite gray
        0x261414  // Deep crimson
    };
    private static final String[] BG_COLOR_NAMES_RU = {
        "Cobblemon (Сланцевый)", "Чёрный", "Тёмно-синий", "Фиолетовый", "Изумрудный", "Графитовый", "Тёмно-красный"
    };
    private static final String[] BG_COLOR_NAMES_EN = {
        "Cobblemon Slate", "Black", "Midnight Blue", "Purple", "Forest Dark", "Graphite", "Crimson"
    };

    // Color presets for text
    private static final int[] TEXT_COLOR_PRESETS = {
        0xFFFFFF, // Pure White
        0xF1F5F9, // Soft White
        0xFDE047, // Light Gold
        0x38BDF8, // Cyan / Sky
        0x4ADE80, // Soft Green
        0xCBD5E1  // Slate Gray
    };
    private static final String[] TEXT_COLOR_NAMES_RU = {
        "Белый", "Мягкий белый", "Золотой", "Голубой", "Зелёный", "Светло-серый"
    };
    private static final String[] TEXT_COLOR_NAMES_EN = {
        "White", "Soft White", "Gold", "Cyan", "Green", "Light Gray"
    };

    private static final String[] ANCHORS = {
        "TOP_CENTER", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "CUSTOM"
    };
    private static final String[] ANCHOR_NAMES_RU = {
        "Вверху по центру", "Вверху слева", "Вверху справа", "Внизу слева", "Внизу справа", "Свободное"
    };
    private static final String[] ANCHOR_NAMES_EN = {
        "Top Center", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Custom"
    };

    // Screen temporary state
    private boolean enabled;
    private boolean alwaysExpanded;
    private String anchor;
    private int offsetX;
    private int offsetY;
    private double scale;
    private int alpha;
    private int bgColor;
    private int textColor;
    private boolean previewSneak = false;

    public PokemonOverlaySettingsScreen() {
        super(Component.literal("Pokémon Overlay Settings"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        if (EffectivenessConfig.CONFIG != null) {
            enabled = EffectivenessConfig.CONFIG.overlayEnabled.get();
            alwaysExpanded = EffectivenessConfig.CONFIG.overlayAlwaysExpanded.get();
            anchor = EffectivenessConfig.CONFIG.overlayAnchor.get();
            offsetX = EffectivenessConfig.CONFIG.overlayOffsetX.get();
            offsetY = EffectivenessConfig.CONFIG.overlayOffsetY.get();
            scale = EffectivenessConfig.CONFIG.overlayScale.get();
            alpha = EffectivenessConfig.CONFIG.overlayBackgroundAlpha.get();
            bgColor = EffectivenessConfig.CONFIG.overlayBackgroundColor.get();
            textColor = EffectivenessConfig.CONFIG.overlayTextColor.get();
        } else {
            enabled = true;
            alwaysExpanded = false;
            anchor = "TOP_CENTER";
            offsetX = 0;
            offsetY = 10;
            scale = 1.0;
            alpha = 200;
            bgColor = 0x141726;
            textColor = 0xFFFFFF;
        }

        previewSneak = alwaysExpanded;

        rebuildControls();
    }

    private void rebuildControls() {
        this.clearWidgets();

        boolean isRu = isRussian();
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        int col1X = left + 12;
        int colW = 195;
        int rowH = 22;
        int curY = top + 34;

        // 1. Enable / Disable toggle
        String statusStr = enabled ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§cВЫКЛ" : "§cOFF");
        Button toggleBtn = Button.builder(
            Component.literal((isRu ? "Оверлей: " : "Overlay: ") + statusStr),
            btn -> {
                enabled = !enabled;
                saveConfig();
                rebuildControls();
            }
        )
        .bounds(col1X, curY, colW, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Включить или отключить окно информации о покемоне" : "Enable or disable Pokémon info overlay")))
        .build();
        this.addRenderableWidget(toggleBtn);
        curY += rowH;

        // 2. Always Expanded Details toggle
        String expandStatus = alwaysExpanded ? (isRu ? "§aСразу" : "§aAlways") : (isRu ? "§eПо приседу" : "§eOn Sneak");
        Button expandBtn = Button.builder(
            Component.literal((isRu ? "Доп. инфо: " : "Details: ") + expandStatus),
            btn -> {
                alwaysExpanded = !alwaysExpanded;
                previewSneak = alwaysExpanded;
                saveConfig();
                rebuildControls();
            }
        )
        .bounds(col1X, curY, colW, 20)
        .tooltip(Tooltip.create(Component.literal(
            isRu ? "Показывать подробную информацию (IVs, характер, способность) сразу или только при приседе (Shift)"
                 : "Show detailed Pokémon information (IVs, nature, ability) immediately or only while sneaking (Shift)"
        )))
        .build();
        this.addRenderableWidget(expandBtn);
        curY += rowH;

        // 3. Anchor Position Button
        String anchorDisplayName = getAnchorDisplayName(anchor, isRu);
        Button anchorBtn = Button.builder(
            Component.literal((isRu ? "Якорь: " : "Anchor: ") + "§e" + anchorDisplayName),
            btn -> {
                int nextIdx = 0;
                for (int i = 0; i < ANCHORS.length; i++) {
                    if (ANCHORS[i].equalsIgnoreCase(anchor)) {
                        nextIdx = (i + 1) % ANCHORS.length;
                        break;
                    }
                }
                anchor = ANCHORS[nextIdx];
                saveConfig();
                rebuildControls();
            }
        )
        .bounds(col1X, curY, colW, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Базовое положение окна на экране" : "Base screen position preset")))
        .build();
        this.addRenderableWidget(anchorBtn);
        curY += rowH;

        // 3. Offset X Slider
        GuiSlider sliderX = new GuiSlider(
            col1X, curY, colW, 20,
            isRu ? "Смещение X" : "Offset X", " px",
            -250, 250, offsetX, 5,
            val -> {
                offsetX = val.intValue();
                saveConfig();
            }
        );
        this.addRenderableWidget(sliderX);
        curY += rowH;

        // 4. Offset Y Slider
        GuiSlider sliderY = new GuiSlider(
            col1X, curY, colW, 20,
            isRu ? "Смещение Y" : "Offset Y", " px",
            0, 250, offsetY, 5,
            val -> {
                offsetY = val.intValue();
                saveConfig();
            }
        );
        this.addRenderableWidget(sliderY);
        curY += rowH;

        // 5. Scale Slider
        GuiSlider sliderScale = new GuiSlider(
            col1X, curY, colW, 20,
            isRu ? "Размер" : "Scale", "x",
            0.6, 1.6, scale, 0.05,
            val -> {
                scale = Math.round(val * 100.0) / 100.0;
                saveConfig();
            }
        );
        this.addRenderableWidget(sliderScale);
        curY += rowH;

        // 6. Alpha Slider
        int currentAlphaPct = (int) Math.round((alpha / 255.0) * 100);
        GuiSlider sliderAlpha = new GuiSlider(
            col1X, curY, colW, 20,
            isRu ? "Прозрачность" : "Opacity", "%",
            10, 100, currentAlphaPct, 5,
            val -> {
                alpha = (int) Math.round(val * 2.55);
                saveConfig();
            }
        );
        this.addRenderableWidget(sliderAlpha);
        curY += rowH;

        // 7. Background Color cycle
        String bgName = getBgColorName(bgColor, isRu);
        Button bgBtn = Button.builder(
            Component.literal((isRu ? "Цвет окна: " : "Window BG: ") + "§b" + bgName),
            btn -> {
                int nextIdx = 0;
                for (int i = 0; i < BG_COLOR_PRESETS.length; i++) {
                    if (BG_COLOR_PRESETS[i] == bgColor) {
                        nextIdx = (i + 1) % BG_COLOR_PRESETS.length;
                        break;
                    }
                }
                bgColor = BG_COLOR_PRESETS[nextIdx];
                saveConfig();
                rebuildControls();
            }
        )
        .bounds(col1X, curY, colW, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Выбрать цвет фона окна" : "Cycle window background color")))
        .build();
        this.addRenderableWidget(bgBtn);
        curY += rowH;

        // 8. Text Color cycle
        String textColName = getTextColorName(textColor, isRu);
        Button textColBtn = Button.builder(
            Component.literal((isRu ? "Цвет текста: " : "Text Color: ") + "§6" + textColName),
            btn -> {
                int nextIdx = 0;
                for (int i = 0; i < TEXT_COLOR_PRESETS.length; i++) {
                    if (TEXT_COLOR_PRESETS[i] == textColor) {
                        nextIdx = (i + 1) % TEXT_COLOR_PRESETS.length;
                        break;
                    }
                }
                textColor = TEXT_COLOR_PRESETS[nextIdx];
                saveConfig();
                rebuildControls();
            }
        )
        .bounds(col1X, curY, colW, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Выбрать цвет текста" : "Cycle text color")))
        .build();
        this.addRenderableWidget(textColBtn);
        curY += rowH;

        // Bottom: Reset & Done buttons
        Button resetBtn = Button.builder(
            Component.literal(isRu ? "↺ Сброс" : "↺ Reset"),
            btn -> {
                enabled = true;
                alwaysExpanded = false;
                previewSneak = false;
                anchor = "TOP_CENTER";
                offsetX = 0;
                offsetY = 10;
                scale = 1.0;
                alpha = 200;
                bgColor = 0x141726;
                textColor = 0xFFFFFF;
                saveConfig();
                rebuildControls();
            }
        )
        .bounds(col1X, top + DIALOG_HEIGHT - 28, 92, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Сбросить все параметры окна по умолчанию" : "Reset all settings to default")))
        .build();
        this.addRenderableWidget(resetBtn);

        Button doneBtn = Button.builder(
            Component.literal(isRu ? "✔ Готово" : "✔ Done"),
            btn -> {
                saveConfig();
                this.onClose();
            }
        )
        .bounds(col1X + 98, top + DIALOG_HEIGHT - 28, 97, 20)
        .build();
        this.addRenderableWidget(doneBtn);

        // Preview toggle button (placed above preview on right column)
        int previewX = left + 225;
        String previewModeStr = previewSneak ? (isRu ? "§dПрисед (Shift)" : "§dSneak (Shift)") : (isRu ? "§aОбычный" : "§aNormal");
        Button previewToggle = Button.builder(
            Component.literal((isRu ? "Вид: " : "View: ") + previewModeStr),
            btn -> {
                previewSneak = !previewSneak;
                rebuildControls();
            }
        )
        .bounds(previewX, top + 34, 210, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Переключить вид предпросмотра (обычный / расширенный при приседе)" : "Toggle preview mode (normal vs sneak)")))
        .build();
        this.addRenderableWidget(previewToggle);
    }

    private void saveConfig() {
        if (EffectivenessConfig.CONFIG != null) {
            EffectivenessConfig.CONFIG.overlayEnabled.set(enabled);
            EffectivenessConfig.CONFIG.overlayAlwaysExpanded.set(alwaysExpanded);
            EffectivenessConfig.CONFIG.overlayAnchor.set(anchor);
            EffectivenessConfig.CONFIG.overlayOffsetX.set(offsetX);
            EffectivenessConfig.CONFIG.overlayOffsetY.set(offsetY);
            EffectivenessConfig.CONFIG.overlayScale.set(scale);
            EffectivenessConfig.CONFIG.overlayBackgroundAlpha.set(alpha);
            EffectivenessConfig.CONFIG.overlayBackgroundColor.set(bgColor);
            EffectivenessConfig.CONFIG.overlayTextColor.set(textColor);
            EffectivenessConfig.SPEC.save();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean isRu = isRussian();
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        // Render main dialog background panel
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xEE111520);
        guiGraphics.renderOutline(left, top, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF38435C);
        guiGraphics.renderOutline(left + 1, top + 1, DIALOG_WIDTH - 2, DIALOG_HEIGHT - 2, 0x4438435C);

        // Header Title
        String title = isRu ? "Настройки инфо-панели покемона (Cobblemon HUD)" : "Pokémon Info Overlay Settings (Cobblemon HUD)";
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, top + 10, 0xFDE047);

        // Separator between controls and preview
        guiGraphics.fill(left + 215, top + 30, left + 216, top + DIALOG_HEIGHT - 10, 0x444B5563);

        // Live Preview Box Rendering on the right side
        renderLivePreview(guiGraphics, left + 225, top + 60, isRu);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderLivePreview(GuiGraphics guiGraphics, int previewX, int previewY, boolean isRu) {
        List<Component> previewLines = new ArrayList<>();

        // Line 1: Checkmark, Archeops, Male, Lvl 100 (exactly as user screenshot)
        MutableComponent line1 = Component.empty();
        line1.append(Component.literal("✔ ").withStyle(s -> s.withColor(0x22C55E)));
        line1.append(Component.literal("Археопс ").withStyle(s -> s.withColor(0xFFFFFF).withBold(true)));
        line1.append(Component.literal("♂ ").withStyle(s -> s.withColor(0x3B82F6)));
        line1.append(Component.literal("Ур.100").withStyle(s -> s.withColor(0xFFFFFF)));
        previewLines.add(line1);

        // Line 2: Hearts Health Bar
        MutableComponent hearts = Component.empty();
        for (int i = 0; i < 10; i++) {
            hearts.append(Component.literal("❤").withStyle(s -> s.withColor(0xEF4444)));
        }
        if (previewSneak) {
            hearts.append(Component.literal(" (290/290 HP)").withStyle(s -> s.withColor(0x94A3B8)));
        }
        previewLines.add(hearts);

        // Line 3: Trainer
        MutableComponent line3 = Component.literal("ТРЕНЕР: ").withStyle(s -> s.withColor(0xCBD5E1));
        line3.append(Component.literal("aRT7MA").withStyle(s -> s.withColor(0xFFFFFF)));
        previewLines.add(line3);

        // Line 4: Types (Rock / Flying colored)
        MutableComponent line4 = Component.literal("Типы: ").withStyle(s -> s.withColor(0xCBD5E1));
        line4.append(Component.literal("Камен.").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xB8A038))));
        line4.append(Component.literal("/").withStyle(s -> s.withColor(0x94A3B8)));
        line4.append(Component.literal("Летающ.").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA890F0))));
        previewLines.add(line4);

        // Line 5: EV Yield
        MutableComponent line5 = Component.literal("EV: ").withStyle(s -> s.withColor(0xCBD5E1));
        line5.append(Component.literal("2 Атака").withStyle(s -> s.withColor(0xFFFFFF)));
        previewLines.add(line5);

        if (!previewSneak) {
            // Line 6: Sneak Prompt
            previewLines.add(Component.literal("<Присядьте для дополнительной информации>").withStyle(s -> s.withColor(0x64748B).withItalic(true)));
        } else {
            // Sneak mode lines: IVs, Nature, Ability
            MutableComponent ivComp = Component.literal("IVs: ").withStyle(s -> s.withColor(0xCBD5E1));
            ivComp.append(Component.literal("31/31/31/31/31/31 ").withStyle(s -> s.withColor(0xFFFFFF)));
            ivComp.append(Component.literal("(100%)").withStyle(s -> s.withColor(0x22C55E)));
            previewLines.add(ivComp);

            MutableComponent ivDetail = Component.literal("HP §a31 §7| Атк §a31 §7| Защ §a31 §7| СпА §a31 §7| Скор §a31").withStyle(s -> s.withColor(0x94A3B8));
            previewLines.add(ivDetail);

            MutableComponent natComp = Component.literal("Характер: ").withStyle(s -> s.withColor(0xCBD5E1));
            natComp.append(Component.literal("Непреклонный ").withStyle(s -> s.withColor(0xFFFFFF)));
            natComp.append(Component.literal("(+Атк, -СпА)").withStyle(s -> s.withColor(0x4ADE80)));
            previewLines.add(natComp);

            MutableComponent abComp = Component.literal("Способность: ").withStyle(s -> s.withColor(0xCBD5E1));
            abComp.append(Component.literal("Пораженец").withStyle(s -> s.withColor(0xFFFFFF)));
            previewLines.add(abComp);
        }

        // Brand
        previewLines.add(Component.literal("Pixelmon").withStyle(s -> s.withColor(0x3B82F6).withItalic(true)));

        // Preview box container
        int maxW = 0;
        for (Component c : previewLines) {
            int w = this.font.width(c);
            if (w > maxW) maxW = w;
        }
        int pBoxW = Math.max(175, maxW + 16);
        int pBoxH = previewLines.size() * 11 + 14;

        float previewScale = Math.min(1.0F, (float) scale);
        int a = Math.max(0, Math.min(255, alpha));
        int argbBg = (a << 24) | (bgColor & 0xFFFFFF);
        int argbBorder = (Math.min(255, a + 45) << 24) | (0x303650);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(previewX, previewY, 0);
        pose.scale(previewScale, previewScale, 1.0F);

        guiGraphics.fill(0, 0, pBoxW, pBoxH, argbBg);
        guiGraphics.renderOutline(0, 0, pBoxW, pBoxH, argbBorder);
        guiGraphics.renderOutline(1, 1, pBoxW - 2, pBoxH - 2, (Math.min(255, a / 2) << 24) | 0x303650);

        int curY = 8;
        for (Component c : previewLines) {
            guiGraphics.drawString(this.font, c, 8, curY, textColor, false);
            curY += 11;
        }

        pose.popPose();
    }

    private String getAnchorDisplayName(String a, boolean isRu) {
        for (int i = 0; i < ANCHORS.length; i++) {
            if (ANCHORS[i].equalsIgnoreCase(a)) {
                return isRu ? ANCHOR_NAMES_RU[i] : ANCHOR_NAMES_EN[i];
            }
        }
        return a;
    }

    private String getBgColorName(int color, boolean isRu) {
        for (int i = 0; i < BG_COLOR_PRESETS.length; i++) {
            if (BG_COLOR_PRESETS[i] == color) {
                return isRu ? BG_COLOR_NAMES_RU[i] : BG_COLOR_NAMES_EN[i];
            }
        }
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private String getTextColorName(int color, boolean isRu) {
        for (int i = 0; i < TEXT_COLOR_PRESETS.length; i++) {
            if (TEXT_COLOR_PRESETS[i] == color) {
                return isRu ? TEXT_COLOR_NAMES_RU[i] : TEXT_COLOR_NAMES_EN[i];
            }
        }
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private boolean isRussian() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getLanguageManager() == null) return false;
        return mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
