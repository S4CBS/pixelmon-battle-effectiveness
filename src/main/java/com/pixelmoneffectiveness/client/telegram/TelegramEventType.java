package com.pixelmoneffectiveness.client.telegram;

import com.pixelmoneffectiveness.config.EffectivenessConfig;

public enum TelegramEventType {
    SHINY("shiny", "✨ Шайни покемоны", "✨ Shiny Pokémon", "Спавн любого дикого шайни покемона", "Spawn of any wild shiny Pokémon"),
    LEGENDARY("legendary", "👑 Легендарные и Мифические", "👑 Legendaries & Mythicals", "Спавн легендарных, мифических и Ultra Beast", "Spawn of legendary, mythical, and ultra beast Pokémon"),
    ULTRA_BOSS("ultra_boss", "👹 Ультра-боссы (Ultimate)", "👹 Ultra Bosses (Ultimate)", "Спавн ультиматных боссов (Ultimate tier, бирюзовый)", "Spawn of Ultimate tier boss Pokémon"),
    LEGENDARY_BOSS("legendary_boss", "👑 Легендарные боссы", "👑 Legendary Bosses", "Спавн боссов легендарного ранга (Legendary tier, оранжевый)", "Spawn of Legendary tier boss Pokémon"),
    EPIC_BOSS("epic_boss", "🔮 Эпические боссы", "🔮 Epic Bosses", "Спавн боссов эпического ранга (Epic tier, фиолетовый)", "Spawn of Epic tier boss Pokémon"),
    ALL_BOSS("all_boss", "⚔️ Все боссы (Любые)", "⚔️ All Other Bosses", "Спавн любых остальных боссов (Rare, Uncommon)", "Spawn of any other boss Pokémon"),
    TERA_RAID("tera_raid", "💎 Тера-рейды (5-7★)", "💎 Tera Raids (5-7★)", "Появление логовищ Тера-рейдов высокой сложности", "High-tier Tera Raid dens"),
    ALL_RAID("all_raid", "⭐ Все рейдовые логовища", "⭐ All Raid Dens", "Появление любых рейдов (1-7★)", "Any raid dens"),
    MASTER_LOOT("master_loot", "🟣 Master Ball PokéLoot", "🟣 Master Ball PokéLoot", "Обнаружение сундука Мастербол", "Master Ball chest discovered"),
    BEAST_LOOT("beast_loot", "🟡 Beast Ball PokéLoot", "🟡 Beast Ball PokéLoot", "Обнаружение сундука Бистбол", "Beast Ball chest discovered"),
    ULTRA_LOOT("ultra_loot", "🔵 Ultra Ball PokéLoot", "🔵 Ultra Ball PokéLoot", "Обнаружение сундука Ультрабол", "Ultra Ball chest discovered"),
    LEGENDARY_POKESTOP("legendary_pokestop", "🏛️ Легендарный ПокеСтоп", "🏛️ Legendary PokéStop", "Обнаружение легендарного ПокеСтопа", "Legendary PokéStop discovered"),
    DITTO("ditto", "🎭 Замаскированный Дитто", "🎭 Disguised Ditto", "Обнаружение замаскированного Дитто", "Disguised Ditto discovered"),
    PLAYER_DEATH("player_death", "💀 Смерть игрока", "💀 Player Death", "Точные координаты и измерение гибели игрока", "Exact coordinates and dimension where player died");

    public final String id;
    public final String nameRu;
    public final String nameEn;
    public final String descRu;
    public final String descEn;

    TelegramEventType(String id, String nameRu, String nameEn, String descRu, String descEn) {
        this.id = id;
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.descRu = descRu;
        this.descEn = descEn;
    }

    public String getDisplayName(boolean isRu) {
        return isRu ? nameRu : nameEn;
    }

    public String getDescription(boolean isRu) {
        return isRu ? descRu : descEn;
    }

    public boolean isEnabled() {
        if (EffectivenessConfig.CONFIG == null) return false;
        return switch (this) {
            case SHINY -> EffectivenessConfig.CONFIG.telegramNotifyShiny.get();
            case LEGENDARY -> EffectivenessConfig.CONFIG.telegramNotifyLegendary.get();
            case ULTRA_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyUltraBoss.get();
            case LEGENDARY_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyLegendaryBoss.get();
            case EPIC_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyEpicBoss.get();
            case ALL_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyBoss.get();
            case TERA_RAID -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid.get();
            case ALL_RAID -> EffectivenessConfig.CONFIG.telegramNotifyAllRaid.get();
            case MASTER_LOOT -> EffectivenessConfig.CONFIG.telegramNotifyMasterLoot.get();
            case BEAST_LOOT -> EffectivenessConfig.CONFIG.telegramNotifyBeastLoot.get();
            case ULTRA_LOOT -> EffectivenessConfig.CONFIG.telegramNotifyUltraLoot.get();
            case LEGENDARY_POKESTOP -> EffectivenessConfig.CONFIG.telegramNotifyLegendaryStop.get();
            case DITTO -> EffectivenessConfig.CONFIG.telegramNotifyDitto.get();
            case PLAYER_DEATH -> EffectivenessConfig.CONFIG.telegramNotifyPlayerDeath.get();
        };
    }

    public void setEnabled(boolean enabled) {
        if (EffectivenessConfig.CONFIG == null) return;
        switch (this) {
            case SHINY -> EffectivenessConfig.CONFIG.telegramNotifyShiny.set(enabled);
            case LEGENDARY -> EffectivenessConfig.CONFIG.telegramNotifyLegendary.set(enabled);
            case ULTRA_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyUltraBoss.set(enabled);
            case LEGENDARY_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyLegendaryBoss.set(enabled);
            case EPIC_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyEpicBoss.set(enabled);
            case ALL_BOSS -> EffectivenessConfig.CONFIG.telegramNotifyBoss.set(enabled);
            case TERA_RAID -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid.set(enabled);
            case ALL_RAID -> EffectivenessConfig.CONFIG.telegramNotifyAllRaid.set(enabled);
            case MASTER_LOOT -> EffectivenessConfig.CONFIG.telegramNotifyMasterLoot.set(enabled);
            case BEAST_LOOT -> EffectivenessConfig.CONFIG.telegramNotifyBeastLoot.set(enabled);
            case ULTRA_LOOT -> EffectivenessConfig.CONFIG.telegramNotifyUltraLoot.set(enabled);
            case LEGENDARY_POKESTOP -> EffectivenessConfig.CONFIG.telegramNotifyLegendaryStop.set(enabled);
            case DITTO -> EffectivenessConfig.CONFIG.telegramNotifyDitto.set(enabled);
            case PLAYER_DEATH -> EffectivenessConfig.CONFIG.telegramNotifyPlayerDeath.set(enabled);
        }
        EffectivenessConfig.SPEC.save();
    }
}
