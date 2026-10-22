package com.pixelmoneffectiveness.client.telegram;

public enum TelegramTab {
    ALL("⭐ Все", "⭐ All"),
    SPECIAL("🔔 Категории", "🔔 Categories"),
    POKEDEX("📖 Покедекс", "📖 Pokédex"),
    SETTINGS("⚙️ Бот", "⚙️ Bot Setup");

    public final String nameRu;
    public final String nameEn;

    TelegramTab(String nameRu, String nameEn) {
        this.nameRu = nameRu;
        this.nameEn = nameEn;
    }

    public String getDisplayName(boolean isRu) {
        return isRu ? nameRu : nameEn;
    }
}
