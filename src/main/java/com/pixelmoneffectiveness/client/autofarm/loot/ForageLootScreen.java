package com.pixelmoneffectiveness.client.autofarm.loot;

import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class ForageLootScreen extends Screen {

    private static final int DIALOG_WIDTH = 380;
    private static final int DIALOG_HEIGHT = 282;

    private final Screen parentScreen;
    private EditBox searchBox;
    private ForageLootCategory selectedCategory = ForageLootCategory.ALL;
    private final Set<String> selectedItemIds = new LinkedHashSet<>();
    private List<ForageLootEntry> displayList = new ArrayList<>();
    private double scrollOffset = 0;
    private boolean isDraggingScroll = false;

    public ForageLootScreen(Screen parentScreen) {
        super(Component.literal("Forage Loot Filter"));
        this.parentScreen = parentScreen;

        if (EffectivenessConfig.CONFIG != null) {
            String filter = EffectivenessConfig.CONFIG.autoFarmKeepItemFilter.get().trim();
            this.selectedItemIds.addAll(ForageLootRegistry.parseFilterString(filter));
        } else {
            this.selectedItemIds.addAll(ForageLootRegistry.getDefaultMintSet());
        }
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

        // Search Input Box
        searchBox = new EditBox(this.font, left + 10, top + 24, 326, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal(isRu ? "🔍 Поиск: кости, мята, shard, bone, ягода..." : "🔍 Search: bone, mint, shard, berry..."));
        searchBox.setResponder(text -> updateDisplayList());
        this.addRenderableWidget(searchBox);

        // Clear Search button
        Button clearSearchBtn = Button.builder(Component.literal("✕"), btn -> {
            searchBox.setValue("");
        })
        .bounds(left + 342, top + 24, 28, 16)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Очистить строку поиска" : "Clear search")))
        .build();
        this.addRenderableWidget(clearSearchBtn);

        // Category Tab Buttons
        int tabY = top + 44;
        int tabH = 15;
        ForageLootCategory[] categories = ForageLootCategory.values();
        int tabCount = categories.length;
        int totalSpacing = (tabCount - 1) * 2;
        int totalW = DIALOG_WIDTH - 20;
        int tabW = (totalW - totalSpacing) / tabCount;

        for (int i = 0; i < tabCount; i++) {
            final ForageLootCategory cat = categories[i];
            boolean isSel = (cat == selectedCategory);
            String label = isSel ? ("§e" + cat.getDisplayName(isRu)) : cat.getDisplayName(isRu);

            int btnX = left + 10 + i * (tabW + 2);
            int curW = (i == tabCount - 1) ? (left + 10 + totalW - btnX) : tabW;

            Button catBtn = Button.builder(Component.literal(label), btn -> {
                this.selectedCategory = cat;
                this.scrollOffset = 0;
                rebuildScreen();
            })
            .bounds(btnX, tabY, curW, tabH)
            .tooltip(Tooltip.create(Component.literal(cat.getDisplayName(isRu))))
            .build();
            this.addRenderableWidget(catBtn);
        }

        // Bottom Action Bar Buttons
        int bottomY = top + DIALOG_HEIGHT - 28;

        Button clearAllBtn = Button.builder(
            Component.literal(isRu ? "✕ Сброс" : "✕ Clear"),
            btn -> {
                selectedItemIds.clear();
                playClickSound();
            }
        )
        .bounds(left + 10, bottomY, 60, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Снять выбор со всех предметов" : "Deselect all items")))
        .build();
        this.addRenderableWidget(clearAllBtn);

        Button mintsOnlyBtn = Button.builder(
            Component.literal(isRu ? "☘ Мята" : "☘ Mints"),
            btn -> {
                selectedItemIds.clear();
                selectedItemIds.addAll(ForageLootRegistry.getDefaultMintSet());
                playClickSound();
            }
        )
        .bounds(left + 74, bottomY, 62, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Выбрать все виды мяты и семена" : "Select all mint types & seeds")))
        .build();
        this.addRenderableWidget(mintsOnlyBtn);

        Button selectTabBtn = Button.builder(
            Component.literal(isRu ? "✔ Вкладка" : "✔ Tab"),
            btn -> {
                for (ForageLootEntry e : displayList) {
                    selectedItemIds.add(e.id);
                }
                playClickSound();
            }
        )
        .bounds(left + 140, bottomY, 70, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Выбрать все видимые предметы в текущей категории" : "Select all visible items in tab")))
        .build();
        this.addRenderableWidget(selectTabBtn);

        Button cancelBtn = Button.builder(
            Component.literal(isRu ? "Отмена" : "Cancel"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            }
        )
        .bounds(left + 220, bottomY, 68, 20)
        .build();
        this.addRenderableWidget(cancelBtn);

        Button applyBtn = Button.builder(
            Component.literal(isRu ? "✔ Готово" : "✔ Apply"),
            btn -> saveAndClose()
        )
        .bounds(left + 292, bottomY, 78, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Сохранить фильтр для автофарма" : "Save filter for auto-farm")))
        .build();
        this.addRenderableWidget(applyBtn);

        updateDisplayList();
    }

    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void saveAndClose() {
        if (EffectivenessConfig.CONFIG != null) {
            String joined = String.join(", ", selectedItemIds);
            EffectivenessConfig.CONFIG.autoFarmKeepItemFilter.set(joined);
            EffectivenessConfig.SPEC.save();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private void updateDisplayList() {
        String query = (searchBox != null) ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<ForageLootEntry> all = ForageLootRegistry.getAllEntries();
        List<ForageLootEntry> result = new ArrayList<>();

        for (ForageLootEntry e : all) {
            if (selectedCategory != ForageLootCategory.ALL && e.category != selectedCategory) {
                continue;
            }
            if (!query.isEmpty()) {
                boolean match = e.localizedName.toLowerCase(Locale.ROOT).contains(query)
                    || e.englishName.toLowerCase(Locale.ROOT).contains(query)
                    || e.id.toLowerCase(Locale.ROOT).contains(query);
                if (!match) continue;
            }
            result.add(e);
        }

        this.displayList = result;
        clampScroll();
    }

    private void clampScroll() {
        int totalRows = (displayList.size() + 1) / 2;
        int listHeight = 180;
        int maxScroll = Math.max(0, totalRows * 30 - listHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= scrollY * 30.0;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;
        int listTop = top + 63;
        int listBottom = top + 245;

        if (button == 0) { // Left-click
            int scrollBarX = left + DIALOG_WIDTH - 12;
            int listHeight = listBottom - listTop;

            // Scrollbar click
            if (mouseX >= scrollBarX - 4 && mouseX <= scrollBarX + 8 && mouseY >= listTop && mouseY <= listBottom) {
                this.isDraggingScroll = true;
                int totalRows = (displayList.size() + 1) / 2;
                int maxScroll = Math.max(0, totalRows * 30 - listHeight);
                if (maxScroll > 0) {
                    double ratio = (mouseY - listTop) / (double) listHeight;
                    scrollOffset = ratio * maxScroll;
                    clampScroll();
                }
                return true;
            }

            // Card click
            if (mouseY >= listTop && mouseY <= listBottom) {
                int col = -1;
                int colW = 172;
                if (mouseX >= left + 10 && mouseX <= left + 10 + colW) {
                    col = 0;
                } else if (mouseX >= left + 186 && mouseX <= left + 186 + colW) {
                    col = 1;
                }

                if (col != -1) {
                    int relY = (int) (mouseY - listTop + scrollOffset);
                    int row = relY / 30;
                    int cardRelY = relY % 30;
                    if (cardRelY <= 28) {
                        int index = row * 2 + col;
                        if (index >= 0 && index < displayList.size()) {
                            ForageLootEntry entry = displayList.get(index);
                            if (isEntrySelected(entry)) {
                                selectedItemIds.remove(entry.id);
                                // Also remove wildcard mints if mint deselected
                                if (entry.id.contains("mint")) {
                                    selectedItemIds.remove("pixelmon:*mint*");
                                    selectedItemIds.remove("*mint*");
                                }
                            } else {
                                selectedItemIds.add(entry.id);
                            }
                            playClickSound();
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isEntrySelected(ForageLootEntry entry) {
        if (selectedItemIds.contains(entry.id)) {
            return true;
        }
        if (entry.id.contains("mint") && (selectedItemIds.contains("pixelmon:*mint*") || selectedItemIds.contains("*mint*"))) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScroll) {
            int totalRows = (displayList.size() + 1) / 2;
            int listHeight = 180;
            int maxScroll = Math.max(0, totalRows * 30 - listHeight);
            if (maxScroll > 0) {
                double ratio = dragY / (double) (listHeight - 20);
                scrollOffset += ratio * maxScroll;
                clampScroll();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 256) {
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (keyCode == 256) { // Escape
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
            return true;
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
    protected void renderBlurredBackground(float partialTick) {
        // Disabled for crisp UI
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x88000000, 0x88000000);

        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        // Modal Background
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xF511151F);
        // Top accent bar (Emerald green)
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + 2, 0xFF2ECC71);
        // Outer borders
        guiGraphics.fill(left, top + DIALOG_HEIGHT - 1, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF1E293B);
        guiGraphics.fill(left, top, left + 1, top + DIALOG_HEIGHT, 0xFF1E293B);
        guiGraphics.fill(left + DIALOG_WIDTH - 1, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF1E293B);

        // Header Title & Counter
        boolean isRu = isRussianLanguage();
        String title = isRu ? "☘ Фильтр лута Forage (Автофарм)" : "☘ Forage Loot Filter (AutoFarm)";
        guiGraphics.drawString(this.font, title, left + 10, top + 9, 0xFF55FF55, true);

        // Calculate selected count
        int selCount = 0;
        for (ForageLootEntry e : ForageLootRegistry.getAllEntries()) {
            if (isEntrySelected(e)) selCount++;
        }

        String selCountStr = (isRu ? "Выбрано: §e" : "Keeping: §e") + selCount + " §7/ §f" + ForageLootRegistry.getAllEntries().size();
        int countWidth = this.font.width(selCountStr);
        guiGraphics.drawString(this.font, "§a" + selCountStr, left + DIALOG_WIDTH - 12 - countWidth, top + 9, 0xFF22C55E, true);

        // Grid Area Borders & Background
        int listTop = top + 63;
        int listBottom = top + 245;
        int listHeight = listBottom - listTop;
        int listWidth = DIALOG_WIDTH - 24;

        guiGraphics.fill(left + 8, listTop - 1, left + 8 + listWidth, listBottom + 1, 0xFF0D1117);
        guiGraphics.fill(left + 8, listTop - 1, left + 8 + listWidth, listTop, 0x33FFFFFF);
        guiGraphics.fill(left + 8, listBottom, left + 8 + listWidth, listBottom + 1, 0x33FFFFFF);

        // Enable Scissor for smooth scrolling
        guiGraphics.enableScissor(left + 8, listTop, left + 8 + listWidth, listBottom);

        int colWidth = 172;
        int cardHeight = 27;
        ForageLootEntry hoveredEntry = null;

        for (int i = 0; i < displayList.size(); i++) {
            ForageLootEntry entry = displayList.get(i);
            int row = i / 2;
            int col = i % 2;

            int cardX = (col == 0) ? (left + 10) : (left + 186);
            int cardY = (int) (listTop + 2 + row * 30 - scrollOffset);

            if (cardY + cardHeight < listTop || cardY > listBottom) {
                continue;
            }

            boolean isHovered = mouseX >= cardX && mouseX <= cardX + colWidth && mouseY >= cardY && mouseY <= cardY + cardHeight && mouseY >= listTop && mouseY <= listBottom;
            if (isHovered) {
                hoveredEntry = entry;
            }

            boolean isSelected = isEntrySelected(entry);

            // Card background & borders
            int bgColor = isSelected ? 0xFF143823 : (isHovered ? 0xFF232D42 : 0xFF181F2E);
            int borderColor = isSelected ? 0xFF2ECC71 : (isHovered ? 0xFF60A5FA : 0xFF334155);

            guiGraphics.fill(cardX, cardY, cardX + colWidth, cardY + cardHeight, bgColor);
            guiGraphics.fill(cardX, cardY, cardX + colWidth, cardY + 1, borderColor);
            guiGraphics.fill(cardX, cardY + cardHeight - 1, cardX + colWidth, cardY + cardHeight, borderColor);
            guiGraphics.fill(cardX, cardY + 1, cardX + 1, cardY + cardHeight - 1, borderColor);
            guiGraphics.fill(cardX + colWidth - 1, cardY + 1, cardX + colWidth, cardY + cardHeight - 1, borderColor);

            // Render Item Sprite
            if (!entry.stack.isEmpty()) {
                guiGraphics.renderItem(entry.stack, cardX + 4, cardY + 5);
            }

            // Localized Name
            String nameText = entry.localizedName;
            if (this.font.width(nameText) > 115) {
                nameText = this.font.plainSubstrByWidth(nameText, 110) + "…";
            }
            int nameColor = isSelected ? 0xFF86EFAC : 0xFFFFFFFF;
            guiGraphics.drawString(this.font, nameText, cardX + 24, cardY + 4, nameColor, true);

            // Item ID in soft muted gray
            String subText = entry.id;
            if (this.font.width(subText) > 120) {
                subText = this.font.plainSubstrByWidth(subText, 115) + "…";
            }
            guiGraphics.drawString(this.font, subText, cardX + 24, cardY + 15, 0xFF94A3B8, true);

            // Status Badge
            String badge = isSelected ? "§a✔" : "§7+";
            guiGraphics.drawString(this.font, badge, cardX + colWidth - 14, cardY + 9, 0xFFFFFFFF, true);
        }

        guiGraphics.disableScissor();

        // Scrollbar
        int totalRows = (displayList.size() + 1) / 2;
        int maxScroll = Math.max(0, totalRows * 30 - listHeight);
        if (maxScroll > 0) {
            int scrollBarX = left + DIALOG_WIDTH - 12;
            guiGraphics.fill(scrollBarX, listTop, scrollBarX + 4, listBottom, 0x44000000);

            float scrollRatio = (float) (scrollOffset / (double) maxScroll);
            int thumbHeight = Math.max(20, (int) ((float) listHeight / (float) (totalRows * 30) * listHeight));
            int thumbY = listTop + (int) (scrollRatio * (listHeight - thumbHeight));

            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, isDraggingScroll ? 0xFF2ECC71 : 0xFF64748B);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Tooltip rendered above everything else
        if (hoveredEntry != null && !hoveredEntry.stack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoveredEntry.stack, mouseX, mouseY);
        }
    }
}
