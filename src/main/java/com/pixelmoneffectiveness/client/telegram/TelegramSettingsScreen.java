package com.pixelmoneffectiveness.client.telegram;

import com.pixelmoneffectiveness.client.radar.PokemonPokedexScreen;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.KeyMapping;
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

public class TelegramSettingsScreen extends Screen {

    public static final KeyMapping OPEN_TELEGRAM_KEY = new KeyMapping(
            "key.pixelmoneffectiveness.open_telegram_settings",
            85, // GLFW_KEY_U
            "key.categories.pixelmoneffectiveness"
    );

    private static final int DIALOG_WIDTH = 380;
    private static final int DIALOG_HEIGHT = 312;

    public enum Tab {
        ALL("⭐ Все", "⭐ All"),
        SPECIAL("🔔 Категории", "🔔 Categories"),
        POKEDEX("📖 Покедекс", "📖 Pokédex"),
        SETTINGS("⚙️ Бот", "⚙️ Bot Setup");

        public final String nameRu;
        public final String nameEn;

        Tab(String nameRu, String nameEn) {
            this.nameRu = nameRu;
            this.nameEn = nameEn;
        }

        public String getDisplayName(boolean isRu) {
            return isRu ? nameRu : nameEn;
        }
    }

    private final Screen parentScreen;
    private Tab activeTab = Tab.SPECIAL;

    private EditBox searchBox;
    private EditBox tokenBox;
    private EditBox chatIdBox;

    private String testStatus = null;
    private boolean testStatusSuccess = false;

    private final Set<String> selectedPokemonNames = new LinkedHashSet<>();
    private List<TelegramEventType> displayedSpecialEvents = new ArrayList<>();
    private List<PokemonPokedexScreen.SpeciesEntry> displayedSpecies = new ArrayList<>();

    private double scrollOffset = 0;
    private boolean isDraggingScroll = false;

    public TelegramSettingsScreen(Screen parentScreen) {
        super(Component.literal("Telegram Notifications"));
        this.parentScreen = parentScreen;
        this.selectedPokemonNames.addAll(TelegramBotClient.getTrackedPokemonSet());
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

        // Master toggle button on top right
        boolean botOn = EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.telegramEnabled.get();
        String botStatusLabel = botOn ? (isRu ? "§aВКЛ" : "§aON") : (isRu ? "§7ВЫКЛ" : "§7OFF");
        Button masterToggleBtn = Button.builder(
                Component.literal((isRu ? "📱 Бот: " : "📱 Bot: ") + botStatusLabel),
                btn -> {
                    TelegramBotClient.toggleEnabled();
                    rebuildScreen();
                }
        )
        .bounds(left + DIALOG_WIDTH - 96, top + 6, 86, 18)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Включить или выключить отправку уведомлений в Telegram" : "Enable or disable Telegram notifications")))
        .build();
        this.addRenderableWidget(masterToggleBtn);

        // Tab buttons (Row: top + 26)
        Tab[] tabs = Tab.values();
        int tabY = top + 26;
        int tabH = 16;
        int tabW = (DIALOG_WIDTH - 20 - (tabs.length - 1) * 2) / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            boolean isSel = (tab == activeTab);
            String title = isSel ? ("§e" + tab.getDisplayName(isRu)) : tab.getDisplayName(isRu);
            int tabX = left + 10 + i * (tabW + 2);
            int curW = (i == tabs.length - 1) ? (left + DIALOG_WIDTH - 10 - tabX) : tabW;

            Button tabBtn = Button.builder(Component.literal(title), btn -> {
                this.activeTab = tab;
                this.scrollOffset = 0;
                rebuildScreen();
            })
            .bounds(tabX, tabY, curW, tabH)
            .build();
            this.addRenderableWidget(tabBtn);
        }

        if (activeTab == Tab.SETTINGS) {
            initSettingsTabWidgets(left, top, isRu);
        } else {
            initListTabWidgets(left, top, isRu);
        }
    }

    private void initSettingsTabWidgets(int left, int top, boolean isRu) {
        String curToken = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramBotToken.get() : "";
        String curChatId = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramChatId.get() : "";

        // Bot Token Box
        tokenBox = new EditBox(this.font, left + 14, top + 64, 276, 18, Component.literal("Bot Token"));
        tokenBox.setMaxLength(128);
        tokenBox.setValue(curToken);
        tokenBox.setHint(Component.literal(isRu ? "Токен бота (123456789:ABCdef...)" : "Bot token (123456789:ABCdef...)"));
        this.addRenderableWidget(tokenBox);

        Button pasteTokenBtn = Button.builder(Component.literal(isRu ? "Вставить" : "Paste"), btn -> {
            String clip = this.minecraft != null ? this.minecraft.keyboardHandler.getClipboard() : "";
            if (clip != null && !clip.isEmpty()) {
                tokenBox.setValue(clip.trim());
            }
        })
        .bounds(left + 294, top + 64, 72, 18)
        .build();
        this.addRenderableWidget(pasteTokenBtn);

        // Chat ID Box
        chatIdBox = new EditBox(this.font, left + 14, top + 104, 276, 18, Component.literal("Chat ID"));
        chatIdBox.setMaxLength(64);
        chatIdBox.setValue(curChatId);
        chatIdBox.setHint(Component.literal(isRu ? "ID чата (например, 123456789 или -100...)" : "Chat ID (e.g. 123456789 or -100...)"));
        this.addRenderableWidget(chatIdBox);

        Button pasteChatIdBtn = Button.builder(Component.literal(isRu ? "Вставить" : "Paste"), btn -> {
            String clip = this.minecraft != null ? this.minecraft.keyboardHandler.getClipboard() : "";
            if (clip != null && !clip.isEmpty()) {
                chatIdBox.setValue(clip.trim());
            }
        })
        .bounds(left + 294, top + 104, 72, 18)
        .build();
        this.addRenderableWidget(pasteChatIdBtn);

        // Test notification button
        Button testBtn = Button.builder(
                Component.literal(isRu ? "🔔 Отправить тестовое сообщение" : "🔔 Send Test Notification"),
                btn -> runTestNotification(isRu)
        )
        .bounds(left + 14, top + 128, 352, 20)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Проверить отправку сообщения в указанный чат" : "Send a test message to verify bot credentials")))
        .build();
        this.addRenderableWidget(testBtn);

        // Save & Done button
        Button doneBtn = Button.builder(Component.literal(isRu ? "✔ Сохранить и закрыть" : "✔ Save & Close"), btn -> saveAndClose())
                .bounds(left + 14, top + DIALOG_HEIGHT - 28, 352, 20)
                .build();
        this.addRenderableWidget(doneBtn);
    }

    private void initListTabWidgets(int left, int top, boolean isRu) {
        // Search Input Box
        searchBox = new EditBox(this.font, left + 10, top + 46, DIALOG_WIDTH - 48, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal(isRu ? "🔍 Поиск: шайни, босс, rayquaza, beldum, лут..." : "🔍 Search: shiny, boss, raid, beldum..."));
        searchBox.setResponder(text -> updateDisplayList());
        this.addRenderableWidget(searchBox);

        Button clearBtn = Button.builder(Component.literal("✕"), btn -> searchBox.setValue(""))
                .bounds(left + DIALOG_WIDTH - 34, top + 46, 24, 16)
                .tooltip(Tooltip.create(Component.literal(isRu ? "Очистить поиск" : "Clear search")))
                .build();
        this.addRenderableWidget(clearBtn);

        // Bottom action buttons
        int bottomY = top + DIALOG_HEIGHT - 26;
        int btnW = 86;

        Button selectAllBtn = Button.builder(Component.literal(isRu ? "✓ Все" : "✓ All"), btn -> {
            if (activeTab == Tab.SPECIAL || activeTab == Tab.ALL) {
                for (TelegramEventType type : TelegramEventType.values()) {
                    type.setEnabled(true);
                }
            }
            if (activeTab == Tab.POKEDEX || activeTab == Tab.ALL) {
                for (PokemonPokedexScreen.SpeciesEntry e : PokemonPokedexScreen.getAllSpeciesEntries()) {
                    selectedPokemonNames.add(e.englishName);
                }
            }
            rebuildScreen();
        })
        .bounds(left + 10, bottomY, btnW, 18)
        .build();
        this.addRenderableWidget(selectAllBtn);

        Button selectNoneBtn = Button.builder(Component.literal(isRu ? "✕ Сброс" : "✕ Clear"), btn -> {
            if (activeTab == Tab.SPECIAL || activeTab == Tab.ALL) {
                for (TelegramEventType type : TelegramEventType.values()) {
                    type.setEnabled(false);
                }
            }
            if (activeTab == Tab.POKEDEX || activeTab == Tab.ALL) {
                selectedPokemonNames.clear();
            }
            rebuildScreen();
        })
        .bounds(left + 100, bottomY, btnW, 18)
        .build();
        this.addRenderableWidget(selectNoneBtn);

        Button testQuickBtn = Button.builder(Component.literal(isRu ? "🔔 Тест" : "🔔 Test"), btn -> {
            String token = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramBotToken.get() : "";
            String chatId = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramChatId.get() : "";
            TelegramBotClient.sendTestNotification(token, chatId, (success, msg) -> {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal(success ? ("§a[Telegram] " + msg) : ("§c[Telegram] Ошибка: " + msg)),
                            false
                    );
                }
            });
        })
        .bounds(left + 190, bottomY, btnW, 18)
        .tooltip(Tooltip.create(Component.literal(isRu ? "Отправить быстрое тестовое уведомление" : "Send quick test notification")))
        .build();
        this.addRenderableWidget(testQuickBtn);

        Button doneBtn = Button.builder(Component.literal(isRu ? "✔ Готово" : "✔ Done"), btn -> saveAndClose())
                .bounds(left + 280, bottomY, btnW + 4, 18)
                .build();
        this.addRenderableWidget(doneBtn);

        updateDisplayList();
    }

    private void runTestNotification(boolean isRu) {
        String token = tokenBox != null ? tokenBox.getValue().trim() : "";
        String chatId = chatIdBox != null ? chatIdBox.getValue().trim() : "";
        saveConfigCredentials();

        testStatus = isRu ? "§eОтправка тестового сообщения..." : "§eSending test message...";
        testStatusSuccess = false;

        TelegramBotClient.sendTestNotification(token, chatId, (success, msg) -> {
            testStatusSuccess = success;
            if (success) {
                testStatus = isRu ? "§a✓ Сообщение успешно доставлено в Telegram!" : "§a✓ Message delivered to Telegram!";
            } else {
                testStatus = (isRu ? "§c✕ Ошибка отправки: " : "§c✕ Send failed: ") + msg;
            }
        });
    }

    private void saveConfigCredentials() {
        if (EffectivenessConfig.CONFIG != null) {
            if (tokenBox != null) {
                EffectivenessConfig.CONFIG.telegramBotToken.set(tokenBox.getValue().trim());
            }
            if (chatIdBox != null) {
                EffectivenessConfig.CONFIG.telegramChatId.set(chatIdBox.getValue().trim());
            }
            EffectivenessConfig.SPEC.save();
        }
    }

    private void saveAndClose() {
        saveConfigCredentials();
        TelegramBotClient.setTrackedPokemonSet(selectedPokemonNames);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private void updateDisplayList() {
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";

        // Filter Special Events
        List<TelegramEventType> spResult = new ArrayList<>();
        for (TelegramEventType type : TelegramEventType.values()) {
            if (activeTab == Tab.ALL && !type.isEnabled() && !query.isEmpty()) {
                // In ALL tab, if searching, allow matching
            } else if (activeTab == Tab.ALL && !type.isEnabled()) {
                continue; // Only show enabled in ALL tab if not searching
            }
            if (!query.isEmpty()) {
                boolean match = type.nameRu.toLowerCase(Locale.ROOT).contains(query)
                        || type.nameEn.toLowerCase(Locale.ROOT).contains(query)
                        || type.descRu.toLowerCase(Locale.ROOT).contains(query)
                        || type.descEn.toLowerCase(Locale.ROOT).contains(query);
                if (!match) continue;
            }
            spResult.add(type);
        }
        this.displayedSpecialEvents = spResult;

        // Filter Pokédex entries
        List<PokemonPokedexScreen.SpeciesEntry> pkResult = new ArrayList<>();
        if (activeTab == Tab.POKEDEX || activeTab == Tab.ALL) {
            for (PokemonPokedexScreen.SpeciesEntry entry : PokemonPokedexScreen.getAllSpeciesEntries()) {
                boolean isSelected = selectedPokemonNames.contains(entry.englishName);
                if (activeTab == Tab.ALL && !isSelected) {
                    continue; // Show only selected in ALL tab
                }
                if (!query.isEmpty()) {
                    boolean match = entry.englishName.toLowerCase(Locale.ROOT).contains(query)
                            || entry.displayName.toLowerCase(Locale.ROOT).contains(query)
                            || entry.formattedDex.toLowerCase(Locale.ROOT).contains(query)
                            || String.valueOf(entry.dex).equals(query);
                    if (!match) continue;
                }
                pkResult.add(entry);
            }
        }
        this.displayedSpecies = pkResult;

        clampScroll();
    }

    private void clampScroll() {
        int listHeight = 216;
        int totalContentHeight = 0;

        if (activeTab == Tab.SPECIAL) {
            totalContentHeight = displayedSpecialEvents.size() * 26;
        } else if (activeTab == Tab.POKEDEX) {
            totalContentHeight = ((displayedSpecies.size() + 1) / 2) * 28;
        } else if (activeTab == Tab.ALL) {
            totalContentHeight = displayedSpecialEvents.size() * 26 + ((displayedSpecies.size() + 1) / 2) * 28 + 20;
        }

        int maxScroll = Math.max(0, totalContentHeight - listHeight);
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
        if (activeTab != Tab.SETTINGS) {
            scrollOffset -= scrollY * 26.0;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && activeTab != Tab.SETTINGS) {
            int left = (this.width - DIALOG_WIDTH) / 2;
            int top = (this.height - DIALOG_HEIGHT) / 2;
            int listTop = top + 66;
            int listBottom = top + 282;

            if (mouseX >= left + 10 && mouseX <= left + DIALOG_WIDTH - 10 && mouseY >= listTop && mouseY <= listBottom) {
                int relY = (int) (mouseY - listTop + scrollOffset);

                if (activeTab == Tab.SPECIAL) {
                    int index = relY / 26;
                    if (index >= 0 && index < displayedSpecialEvents.size()) {
                        TelegramEventType type = displayedSpecialEvents.get(index);
                        type.setEnabled(!type.isEnabled());
                        playClickSound();
                        return true;
                    }
                } else if (activeTab == Tab.POKEDEX) {
                    int col = (mouseX < left + 188) ? 0 : 1;
                    int row = relY / 28;
                    int index = row * 2 + col;
                    if (index >= 0 && index < displayedSpecies.size()) {
                        PokemonPokedexScreen.SpeciesEntry entry = displayedSpecies.get(index);
                        if (selectedPokemonNames.contains(entry.englishName)) {
                            selectedPokemonNames.remove(entry.englishName);
                        } else {
                            selectedPokemonNames.add(entry.englishName);
                        }
                        playClickSound();
                        return true;
                    }
                } else if (activeTab == Tab.ALL) {
                    int spHeight = displayedSpecialEvents.size() * 26;
                    if (relY < spHeight) {
                        int index = relY / 26;
                        if (index >= 0 && index < displayedSpecialEvents.size()) {
                            TelegramEventType type = displayedSpecialEvents.get(index);
                            type.setEnabled(!type.isEnabled());
                            playClickSound();
                            return true;
                        }
                    } else {
                        int pkRelY = relY - spHeight - 16;
                        if (pkRelY >= 0) {
                            int col = (mouseX < left + 188) ? 0 : 1;
                            int row = pkRelY / 28;
                            int index = row * 2 + col;
                            if (index >= 0 && index < displayedSpecies.size()) {
                                PokemonPokedexScreen.SpeciesEntry entry = displayedSpecies.get(index);
                                if (selectedPokemonNames.contains(entry.englishName)) {
                                    selectedPokemonNames.remove(entry.englishName);
                                } else {
                                    selectedPokemonNames.add(entry.englishName);
                                }
                                playClickSound();
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean isRu = isRussianLanguage();
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        // Background card
        g.fill(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xF010141E);
        // Border outline
        g.renderOutline(left, top, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF35425E);

        // Header Title
        g.drawString(this.font, isRu ? "📱 Telegram Уведомления" : "📱 Telegram Notifications", left + 12, top + 10, 0xFFE0E0, false);

        if (activeTab == Tab.SETTINGS) {
            renderSettingsTab(g, left, top, isRu);
        } else {
            renderListTab(g, left, top, mouseX, mouseY, isRu);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSettingsTab(GuiGraphics g, int left, int top, boolean isRu) {
        g.drawString(this.font, isRu ? "Токен бота (Bot Token):" : "Bot Token:", left + 14, top + 52, 0xFDE047, false);
        g.drawString(this.font, isRu ? "ID чата (Chat ID):" : "Chat ID:", left + 14, top + 92, 0xFDE047, false);

        if (testStatus != null) {
            g.drawString(this.font, testStatus, left + 14, top + 152, testStatusSuccess ? 0x4ADE80 : 0xF87171, false);
        }

        // Instructions card
        int guideTop = top + 170;
        int guideH = 108;
        g.fill(left + 14, guideTop, left + DIALOG_WIDTH - 14, guideTop + guideH, 0x60000000);
        g.renderOutline(left + 14, guideTop, DIALOG_WIDTH - 28, guideH, 0x40FFFFFF);

        g.drawString(this.font, isRu ? "§6💡 Быстрая настройка за 1 минуту:" : "§6💡 Fast 1-minute setup:", left + 20, guideTop + 6, 0xFFFFFF, false);
        g.drawString(this.font, isRu ? "§71. Откройте Telegram и напишите §e@BotFather" : "§71. Open Telegram and message §e@BotFather", left + 20, guideTop + 20, 0xCBD5E1, false);
        g.drawString(this.font, isRu ? "§72. Отправьте §e/newbot§7, задайте имя и скопируйте §eAPI Token" : "§72. Send §e/newbot§7, name it, copy the §eAPI Token", left + 20, guideTop + 34, 0xCBD5E1, false);
        g.drawString(this.font, isRu ? "§73. Напишите §e@userinfobot§7 чтобы узнать свой §eId (Chat ID)" : "§73. Message §e@userinfobot§7 to get your §eChat ID", left + 20, guideTop + 48, 0xCBD5E1, false);
        g.drawString(this.font, isRu ? "§74. Напишите своему новому боту любое сообщение (например, §e/start§7)" : "§74. Send any message to your new bot (e.g. §e/start§7)", left + 20, guideTop + 62, 0xCBD5E1, false);
        g.drawString(this.font, isRu ? "§75. Вставьте Token и Chat ID выше и нажмите §e'Тест'" : "§75. Paste credentials above and click §e'Send Test'", left + 20, guideTop + 76, 0xCBD5E1, false);
        g.drawString(this.font, isRu ? "§a✓ Точные координаты и команда /tp будут приходить прямо в чат!" : "§a✓ Exact coordinates & /tp command will arrive in chat!", left + 20, guideTop + 92, 0x4ADE80, false);
    }

    private void renderListTab(GuiGraphics g, int left, int top, int mouseX, int mouseY, boolean isRu) {
        int listTop = top + 66;
        int listBottom = top + 282;
        int listH = listBottom - listTop;

        g.enableScissor(left + 8, listTop, left + DIALOG_WIDTH - 8, listBottom);

        int curY = listTop - (int) scrollOffset;

        if (activeTab == Tab.SPECIAL || activeTab == Tab.ALL) {
            for (TelegramEventType type : displayedSpecialEvents) {
                if (curY + 24 >= listTop && curY <= listBottom) {
                    boolean hovered = (mouseX >= left + 10 && mouseX <= left + DIALOG_WIDTH - 14 && mouseY >= Math.max(listTop, curY) && mouseY <= Math.min(listBottom, curY + 24));
                    boolean enabled = type.isEnabled();

                    int bgColor = hovered ? 0x9028354D : 0x50182236;
                    int outlineColor = enabled ? 0xFF4ADE80 : (hovered ? 0xFF64748B : 0xFF334155);

                    g.fill(left + 10, curY, left + DIALOG_WIDTH - 14, curY + 24, bgColor);
                    g.renderOutline(left + 10, curY, DIALOG_WIDTH - 24, 24, outlineColor);

                    // Title
                    g.drawString(this.font, type.getDisplayName(isRu), left + 16, curY + 4, enabled ? 0xFFFFFF : 0x94A3B8, false);
                    // Desc
                    g.drawString(this.font, type.getDescription(isRu), left + 16, curY + 14, 0x64748B, false);

                    // Toggle Pill
                    int pillX = left + DIALOG_WIDTH - 58;
                    int pillY = curY + 4;
                    int pillBg = enabled ? 0xCC16A34A : 0x88334155;
                    g.fill(pillX, pillY, pillX + 40, pillY + 16, pillBg);
                    g.renderOutline(pillX, pillY, 40, 16, enabled ? 0xFF4ADE80 : 0xFF475569);
                    String pillText = enabled ? (isRu ? "ВКЛ" : "ON") : (isRu ? "ВЫКЛ" : "OFF");
                    int textW = this.font.width(pillText);
                    g.drawString(this.font, pillText, pillX + (40 - textW) / 2, pillY + 4, enabled ? 0xFFFFFF : 0x94A3B8, false);
                }
                curY += 26;
            }
        }

        if (activeTab == Tab.ALL && !displayedSpecies.isEmpty()) {
            g.drawString(this.font, isRu ? "§6Отслеживаемые покемоны из Покедекса:" : "§6Tracked Pokédex Pokémon:", left + 12, curY + 4, 0xFDE047, false);
            curY += 16;
        }

        if (activeTab == Tab.POKEDEX || activeTab == Tab.ALL) {
            int cardW = 174;
            int cardH = 26;

            for (int i = 0; i < displayedSpecies.size(); i++) {
                int col = i % 2;
                int row = i / 2;
                int cardX = left + 10 + col * (cardW + 4);
                int cardY = curY + row * 28;

                if (cardY + cardH >= listTop && cardY <= listBottom) {
                    PokemonPokedexScreen.SpeciesEntry entry = displayedSpecies.get(i);
                    boolean isSelected = selectedPokemonNames.contains(entry.englishName);
                    boolean hovered = (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= Math.max(listTop, cardY) && mouseY <= Math.min(listBottom, cardY + cardH));

                    int bg = isSelected ? 0x8015803D : (hovered ? 0x8028354D : 0x50182236);
                    int border = isSelected ? 0xFF4ADE80 : (hovered ? 0xFF64748B : 0xFF334155);

                    g.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
                    g.renderOutline(cardX, cardY, cardW, cardH, border);

                    // Sprite
                    if (entry.sprite != null) {
                        try {
                            g.blit(entry.sprite, cardX + 3, cardY + 3, 0, 0, 20, 20, 20, 20);
                        } catch (Throwable ignored) {}
                    }

                    // Dex + Name
                    String nameStr = entry.displayName;
                    if (!entry.displayName.equalsIgnoreCase(entry.englishName)) {
                        nameStr += " (" + entry.englishName + ")";
                    }
                    g.drawString(this.font, entry.formattedDex + " " + nameStr, cardX + 26, cardY + 4, isSelected ? 0xFFFFFF : 0xE2E8F0, false);

                    // Status mark
                    String mark = isSelected ? "§a✔" : "§7+";
                    g.drawString(this.font, mark, cardX + cardW - 14, cardY + 8, isSelected ? 0x4ADE80 : 0x64748B, false);
                }
            }
        }

        g.disableScissor();

        // Scrollbar
        int totalContentHeight = 0;
        if (activeTab == Tab.SPECIAL) {
            totalContentHeight = displayedSpecialEvents.size() * 26;
        } else if (activeTab == Tab.POKEDEX) {
            totalContentHeight = ((displayedSpecies.size() + 1) / 2) * 28;
        } else if (activeTab == Tab.ALL) {
            totalContentHeight = displayedSpecialEvents.size() * 26 + ((displayedSpecies.size() + 1) / 2) * 28 + 20;
        }

        int maxScroll = Math.max(0, totalContentHeight - listH);
        if (maxScroll > 0) {
            int scrollBarX = left + DIALOG_WIDTH - 10;
            int thumbH = Math.max(16, (int) ((listH / (double) totalContentHeight) * listH));
            int thumbY = listTop + (int) ((scrollOffset / (double) maxScroll) * (listH - thumbH));

            g.fill(scrollBarX, listTop, scrollBarX + 4, listBottom, 0x40000000);
            g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF64748B);
        }
    }
}
