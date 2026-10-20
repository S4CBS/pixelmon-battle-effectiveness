package com.pixelmoneffectiveness.client.autofarm.loot;

public enum ForageLootCategory {
    ALL("Все", "All", 0xFF3B82F6),
    MINTS("Мята", "Mints", 0xFF22C55E),
    BONES_DROPS("Кости и Дроп", "Bones & Drops", 0xFFE2E8F0),
    STONES("Камни эволюции", "Evo Stones", 0xFFF59E0B),
    TREASURES("Ценности", "Treasures", 0xFFA855F7),
    BERRIES("Ягоды", "Berries", 0xFFEC4899),
    APRICORNS("Априкорны", "Apricorns", 0xFF06B6D4),
    MINECRAFT("Ресурсы MC", "Minecraft", 0xFF10B981);

    public final String nameRu;
    public final String nameEn;
    public final int color;

    ForageLootCategory(String nameRu, String nameEn, int color) {
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.color = color;
    }

    public String getDisplayName(boolean isRu) {
        return isRu ? nameRu : nameEn;
    }
}
