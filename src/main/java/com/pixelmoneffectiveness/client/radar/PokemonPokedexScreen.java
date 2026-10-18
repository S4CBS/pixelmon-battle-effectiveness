package com.pixelmoneffectiveness.client.radar;

import com.pixelmonmod.pixelmon.api.pokemon.PokemonBase;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class PokemonPokedexScreen extends Screen {

    private static final int DIALOG_WIDTH = 354;
    private static final int DIALOG_HEIGHT = 276;

    private final Screen parentScreen;
    private EditBox searchBox;
    private int selectedGen = 0; // 0 = all, 1..9 = gen 1..9
    private final Set<String> selectedEnglishNames = new LinkedHashSet<>();
    private List<SpeciesEntry> displayList = new ArrayList<>();
    private double scrollOffset = 0;
    private boolean isDraggingScroll = false;

    private static List<SpeciesEntry> CACHED_ENTRIES = null;

    public PokemonPokedexScreen(Screen parentScreen) {
        super(Component.literal("Pokédex"));
        this.parentScreen = parentScreen;

        // Initialize selected names from existing config filter
        if (EffectivenessConfig.CONFIG != null) {
            String filter = EffectivenessConfig.CONFIG.pokemonSearchFilter.get().trim();
            if (!filter.isEmpty()) {
                String[] tokens = filter.split("[,;]+");
                List<SpeciesEntry> all = getAllSpeciesEntries();
                for (String t : tokens) {
                    String token = t.trim();
                    if (!token.isEmpty()) {
                        for (SpeciesEntry entry : all) {
                            if (entry.englishName.equalsIgnoreCase(token) || entry.displayName.equalsIgnoreCase(token)) {
                                selectedEnglishNames.add(entry.englishName);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static class SpeciesEntry {
        public final Species species;
        public final int dex;
        public final int gen;
        public final String englishName;
        public final String displayName;
        public final String formattedDex;
        public final ResourceLocation sprite;

        public SpeciesEntry(Species species) {
            this.species = species;
            this.dex = species.getDex();
            this.gen = getGenFromDex(this.dex);
            this.englishName = species.getName();

            String trans = "";
            try {
                if (species.getNameTranslation() != null) {
                    trans = species.getNameTranslation().getString();
                }
            } catch (Throwable ignored) {}
            this.displayName = (trans.isEmpty() || trans.equalsIgnoreCase(this.englishName)) ? this.englishName : trans;
            this.formattedDex = species.getFormattedDex();

            ResourceLocation s = null;
            try {
                s = new PokemonBase(species).getSprite();
            } catch (Throwable ignored) {}
            this.sprite = s;
        }

        private static int getGenFromDex(int d) {
            if (d <= 151) return 1;
            if (d <= 251) return 2;
            if (d <= 386) return 3;
            if (d <= 493) return 4;
            if (d <= 649) return 5;
            if (d <= 721) return 6;
            if (d <= 809) return 7;
            if (d <= 905) return 8;
            return 9;
        }
    }

    public static List<SpeciesEntry> getAllSpeciesEntries() {
        if (CACHED_ENTRIES == null) {
            List<SpeciesEntry> list = new ArrayList<>();
            try {
                List<Species> all = PixelmonSpecies.getAll();
                for (Species s : all) {
                    if (s != null && s.getDex() > 0) {
                        list.add(new SpeciesEntry(s));
                    }
                }
                list.sort(Comparator.comparingInt(e -> e.dex));
            } catch (Throwable t) {
                t.printStackTrace();
            }
            CACHED_ENTRIES = list;
        }
        return CACHED_ENTRIES;
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
        searchBox = new EditBox(this.font, left + 10, top + 24, 302, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal(isRu ? "🔍 Поиск: Имя или № (напр. 25, Pika, Дитто)..." : "🔍 Search: Name or # (e.g. 25, Pika...)..."));
        searchBox.setResponder(text -> updateDisplayList());
        this.addRenderableWidget(searchBox);

        // Clear Search button
        Button clearSearchBtn = Button.builder(Component.literal("✕"), btn -> {
            searchBox.setValue("");
        })
        .bounds(left + 316, top + 24, 26, 16)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Очистить строку поиска" : "Clear search")))
        .build();
        this.addRenderableWidget(clearSearchBtn);

        // Generation Buttons (Gen 0 = All, Gen 1..9)
        int genBtnY = top + 44;
        int genBtnW = 31;
        int genBtnSpacing = 3;
        int genStartX = left + 10;

        for (int g = 0; g <= 9; g++) {
            final int genIndex = g;
            String label = (genIndex == 0) ? (isRu ? "Все" : "All") : ("G" + genIndex);
            if (genIndex == selectedGen) {
                label = "§e" + label;
            }

            Button genBtn = Button.builder(Component.literal(label), btn -> {
                this.selectedGen = genIndex;
                this.scrollOffset = 0;
                rebuildScreen();
            })
            .bounds(genStartX + g * (genBtnW + genBtnSpacing), genBtnY, genBtnW, 15)
            .tooltip(Tooltip.create(Component.literal(genIndex == 0 ? (isRu ? "Все поколения" : "All generations") : (isRu ? "Поколение " + genIndex : "Generation " + genIndex))))
            .build();
            this.addRenderableWidget(genBtn);
        }

        // Bottom Bar Buttons
        int bottomY = top + DIALOG_HEIGHT - 28;

        Button clearAllBtn = Button.builder(
            Component.literal(isRu ? "✕ Сброс" : "✕ Clear"),
            btn -> {
                selectedEnglishNames.clear();
            }
        )
        .bounds(left + 10, bottomY, 68, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Снять выбор со всех покемонов" : "Deselect all Pokémon")))
        .build();
        this.addRenderableWidget(clearAllBtn);

        Button rareBtn = Button.builder(
            Component.literal(isRu ? "★ Редкие" : "★ Rare"),
            btn -> {
                try {
                    Set<Integer> leg = PixelmonSpecies.getLegendaries();
                    Set<Integer> myt = PixelmonSpecies.getMythicals();
                    Set<Integer> ub = PixelmonSpecies.getUltraBeasts();
                    for (SpeciesEntry e : getAllSpeciesEntries()) {
                        if ((leg != null && leg.contains(e.dex))
                            || (myt != null && myt.contains(e.dex))
                            || (ub != null && ub.contains(e.dex))) {
                            selectedEnglishNames.add(e.englishName);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        )
        .bounds(left + 82, bottomY, 74, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Добавить всех Легендарных, Мифических и Ультра-чудовищ" : "Select all Legendary, Mythical, and Ultra Beasts")))
        .build();
        this.addRenderableWidget(rareBtn);

        Button cancelBtn = Button.builder(
            Component.literal(isRu ? "Отмена" : "Cancel"),
            btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            }
        )
        .bounds(left + 196, bottomY, 68, 20)
        .build();
        this.addRenderableWidget(cancelBtn);

        Button doneBtn = Button.builder(
            Component.literal(isRu ? "✔ Готово" : "✔ Apply"),
            btn -> saveAndClose()
        )
        .bounds(left + 268, bottomY, 74, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Применить выбор и начать поиск на радаре" : "Apply selection to radar tracking")))
        .build();
        this.addRenderableWidget(doneBtn);

        updateDisplayList();
    }

    private void saveAndClose() {
        if (EffectivenessConfig.CONFIG != null) {
            String joined = String.join(", ", selectedEnglishNames);
            EffectivenessConfig.CONFIG.pokemonSearchFilter.set(joined);
            EffectivenessConfig.SPEC.save();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private void updateDisplayList() {
        String query = (searchBox != null) ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<SpeciesEntry> all = getAllSpeciesEntries();
        List<SpeciesEntry> result = new ArrayList<>();

        for (SpeciesEntry e : all) {
            if (selectedGen > 0 && e.gen != selectedGen) {
                continue;
            }
            if (!query.isEmpty()) {
                String dexStr = String.valueOf(e.dex);
                boolean match = e.englishName.toLowerCase(Locale.ROOT).contains(query)
                    || e.displayName.toLowerCase(Locale.ROOT).contains(query)
                    || dexStr.equals(query)
                    || e.formattedDex.toLowerCase(Locale.ROOT).contains(query);
                if (!match) continue;
            }
            result.add(e);
        }
        this.displayList = result;
        clampScroll();
    }

    private void clampScroll() {
        int totalRows = (displayList.size() + 1) / 2;
        int listHeight = 176;
        int maxScroll = Math.max(0, totalRows * 28 - listHeight);
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
        scrollOffset -= scrollY * 28.0;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;
        int listTop = top + 63;
        int listBottom = top + 239;

        if (button == 0) { // Left-click
            // Check scrollbar click
            int scrollBarX = left + DIALOG_WIDTH - 12;
            int listHeight = listBottom - listTop;
            if (mouseX >= scrollBarX - 4 && mouseX <= scrollBarX + 8 && mouseY >= listTop && mouseY <= listBottom) {
                this.isDraggingScroll = true;
                int totalRows = (displayList.size() + 1) / 2;
                int maxScroll = Math.max(0, totalRows * 28 - listHeight);
                if (maxScroll > 0) {
                    double ratio = (mouseY - listTop) / (double) listHeight;
                    scrollOffset = ratio * maxScroll;
                    clampScroll();
                }
                return true;
            }

            // Check card click
            if (mouseY >= listTop && mouseY <= listBottom) {
                int col = -1;
                if (mouseX >= left + 10 && mouseX <= left + 168) {
                    col = 0;
                } else if (mouseX >= left + 172 && mouseX <= left + 330) {
                    col = 1;
                }
                if (col != -1) {
                    int relY = (int) (mouseY - listTop + scrollOffset);
                    int row = relY / 28;
                    int cardRelY = relY % 28;
                    if (cardRelY <= 26) {
                        int index = row * 2 + col;
                        if (index >= 0 && index < displayList.size()) {
                            SpeciesEntry entry = displayList.get(index);
                            if (selectedEnglishNames.contains(entry.englishName)) {
                                selectedEnglishNames.remove(entry.englishName);
                            } else {
                                selectedEnglishNames.add(entry.englishName);
                            }
                            if (this.minecraft != null) {
                                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            }
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
            int listHeight = 176;
            int maxScroll = Math.max(0, totalRows * 28 - listHeight);
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
        if (keyCode == 256) { // Escape returns to parent
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        // Modal Background
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xEE11151F);
        // Top accent bar
        guiGraphics.fill(left, top, left + DIALOG_WIDTH, top + 2, 0xFF3B82F6);
        // Outer borders
        guiGraphics.fill(left, top + DIALOG_HEIGHT - 1, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF1E293B);
        guiGraphics.fill(left, top, left + 1, top + DIALOG_HEIGHT, 0xFF1E293B);
        guiGraphics.fill(left + DIALOG_WIDTH - 1, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF1E293B);

        // Header Title & Counter
        boolean isRu = isRussianLanguage();
        String title = isRu ? "⚡ Покедекс: Выбор для радара" : "⚡ Pokédex: Radar Picker";
        guiGraphics.drawString(this.font, title, left + 10, top + 9, 0xFFFFFF55, false);

        String selCountStr = (isRu ? "Выбрано: §e" : "Tracking: §e") + selectedEnglishNames.size();
        int countWidth = this.font.width(selCountStr);
        guiGraphics.drawString(this.font, "§a" + selCountStr, left + DIALOG_WIDTH - 12 - countWidth, top + 9, 0xFF22C55E, false);

        // Grid Area Borders & Background
        int listTop = top + 63;
        int listBottom = top + 239;
        int listHeight = listBottom - listTop;
        int listWidth = DIALOG_WIDTH - 24;

        guiGraphics.fill(left + 8, listTop - 1, left + 8 + listWidth, listBottom + 1, 0x660B0E14);
        guiGraphics.fill(left + 8, listTop - 1, left + 8 + listWidth, listTop, 0x33FFFFFF);
        guiGraphics.fill(left + 8, listBottom, left + 8 + listWidth, listBottom + 1, 0x33FFFFFF);

        // Enable Scissor for smooth scrolling
        guiGraphics.enableScissor(left + 8, listTop, left + 8 + listWidth, listBottom);

        int colWidth = 158;
        int cardHeight = 26;

        for (int i = 0; i < displayList.size(); i++) {
            SpeciesEntry entry = displayList.get(i);
            int row = i / 2;
            int col = i % 2;

            int cardX = (col == 0) ? (left + 10) : (left + 172);
            int cardY = (int) (listTop + 2 + row * 28 - scrollOffset);

            // Skip rendering if out of scissor bounds
            if (cardY + cardHeight < listTop || cardY > listBottom) {
                continue;
            }

            boolean isHovered = mouseX >= cardX && mouseX <= cardX + colWidth && mouseY >= cardY && mouseY <= cardY + cardHeight && mouseY >= listTop && mouseY <= listBottom;
            boolean isSelected = selectedEnglishNames.contains(entry.englishName);

            // Card background & border
            int bgColor = isSelected ? 0x4422C55E : (isHovered ? 0x33475569 : 0x221E293B);
            int borderColor = isSelected ? 0xFF22C55E : (isHovered ? 0xFF60A5FA : 0xFF334155);

            guiGraphics.fill(cardX, cardY, cardX + colWidth, cardY + cardHeight, bgColor);
            // 1px Border
            guiGraphics.fill(cardX, cardY, cardX + colWidth, cardY + 1, borderColor);
            guiGraphics.fill(cardX, cardY + cardHeight - 1, cardX + colWidth, cardY + cardHeight, borderColor);
            guiGraphics.fill(cardX, cardY, cardX + 1, cardY + cardHeight, borderColor);
            guiGraphics.fill(cardX + colWidth - 1, cardY, cardX + colWidth, cardY + cardHeight, borderColor);

            // Sprite Icon
            if (entry.sprite != null) {
                guiGraphics.blit(entry.sprite, cardX + 2, cardY + 2, 0.0f, 0.0f, 22, 22, 22, 22);
            }

            // Dex number
            guiGraphics.drawString(this.font, entry.formattedDex, cardX + 26, cardY + 4, 0xFFAAAAAA, false);

            // Pokémon Name
            String nameText = entry.displayName;
            if (this.font.width(nameText) > 85) {
                nameText = this.font.plainSubstrByWidth(nameText, 80) + "…";
            }
            int nameColor = isSelected ? 0xFFFFFFFF : 0xFFE2E8F0;
            guiGraphics.drawString(this.font, nameText, cardX + 54, cardY + 4, nameColor, false);

            // Russian secondary translation if different
            if (!entry.displayName.equalsIgnoreCase(entry.englishName)) {
                String subText = entry.englishName;
                if (this.font.width(subText) > 90) {
                    subText = this.font.plainSubstrByWidth(subText, 85) + "…";
                }
                guiGraphics.drawString(this.font, subText, cardX + 26, cardY + 14, 0xFF64748B, false);
            }

            // Selection Checkmark / Plus Indicator
            String statusBadge = isSelected ? "§a✔" : "§8+";
            guiGraphics.drawString(this.font, statusBadge, cardX + colWidth - 13, cardY + 9, 0xFFFFFFFF, false);
        }

        guiGraphics.disableScissor();

        // Scrollbar
        int totalRows = (displayList.size() + 1) / 2;
        int maxScroll = Math.max(0, totalRows * 28 - listHeight);
        if (maxScroll > 0) {
            int scrollBarX = left + DIALOG_WIDTH - 12;
            guiGraphics.fill(scrollBarX, listTop, scrollBarX + 4, listBottom, 0x44000000);

            float scrollRatio = (float) (scrollOffset / (double) maxScroll);
            int thumbHeight = Math.max(20, (int) ((float) listHeight / (float) (totalRows * 28) * listHeight));
            int thumbY = listTop + (int) (scrollRatio * (listHeight - thumbHeight));

            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, isDraggingScroll ? 0xFF3B82F6 : 0xFF64748B);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
