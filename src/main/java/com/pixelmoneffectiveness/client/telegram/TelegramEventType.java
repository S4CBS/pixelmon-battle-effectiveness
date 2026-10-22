package com.pixelmoneffectiveness.client.telegram;

import com.pixelmoneffectiveness.config.EffectivenessConfig;

public enum TelegramEventType {
    SHINY("shiny", "✨ Шайни покемоны", "✨ Shiny Pokémon", "Спавн любого дикого шайни покемона", "Spawn of any wild shiny Pokémon"),
    LEGENDARY("legendary", "👑 Легендарные и Мифические", "👑 Legendaries & Mythicals", "Спавн легендарных, мифических и Ultra Beast", "Spawn of legendary, mythical, and ultra beast Pokémon"),
    ULTRA_BOSS("ultra_boss", "👹 Ультра-боссы (Ultimate)", "👹 Ultra Bosses (Ultimate)", "Спавн ультиматных боссов (Ultimate tier, бирюзовый)", "Spawn of Ultimate tier boss Pokémon"),
    LEGENDARY_BOSS("legendary_boss", "👑 Легендарные боссы", "👑 Legendary Bosses", "Спавн боссов легендарного ранга (Legendary tier, оранжевый)", "Spawn of Legendary tier boss Pokémon"),
    EPIC_BOSS("epic_boss", "🔮 Эпические боссы", "🔮 Epic Bosses", "Спавн боссов эпического ранга (Epic tier, фиолетовый)", "Spawn of Epic tier boss Pokémon"),
    ALL_BOSS("all_boss", "⚔️ Все боссы (Любые)", "⚔️ All Other Bosses", "Спавн любых остальных боссов (Rare, Uncommon)", "Spawn of any other boss Pokémon"),

    TERA_RAID_1("tera_raid_1", "💎 Тера-рейд 1★", "💎 Tera Raid 1★", "Логово Тера-рейда 1★ сложности", "1★ Tera Raid den"),
    TERA_RAID_2("tera_raid_2", "💎 Тера-рейд 2★", "💎 Tera Raid 2★", "Логово Тера-рейда 2★ сложности", "2★ Tera Raid den"),
    TERA_RAID_3("tera_raid_3", "💎 Тера-рейд 3★", "💎 Tera Raid 3★", "Логово Тера-рейда 3★ сложности", "3★ Tera Raid den"),
    TERA_RAID_4("tera_raid_4", "💎 Тера-рейд 4★", "💎 Tera Raid 4★", "Логово Тера-рейда 4★ сложности", "4★ Tera Raid den"),
    TERA_RAID_5("tera_raid_5", "💎 Тера-рейд 5★", "💎 Tera Raid 5★", "Логово Тера-рейда 5★ сложности", "5★ Tera Raid den"),
    TERA_RAID_6("tera_raid_6", "💎 Тера-рейд 6★", "💎 Tera Raid 6★", "Логово Тера-рейда 6★ сложности", "6★ Tera Raid den"),
    TERA_RAID_7("tera_raid_7", "💎 Тера-рейд 7★", "💎 Tera Raid 7★", "Логово Тера-рейда 7★ сложности", "7★ Tera Raid den"),

    MAX_RAID_1("max_raid_1", "🔴 Макс-рейд 1★", "🔴 Max Raid 1★", "Логово Макс-рейда 1★ сложности (Dynamax)", "1★ Max Raid den (Dynamax)"),
    MAX_RAID_2("max_raid_2", "🔴 Макс-рейд 2★", "🔴 Max Raid 2★", "Логово Макс-рейда 2★ сложности (Dynamax)", "2★ Max Raid den (Dynamax)"),
    MAX_RAID_3("max_raid_3", "🔴 Макс-рейд 3★", "🔴 Max Raid 3★", "Логово Макс-рейда 3★ сложности (Dynamax)", "3★ Max Raid den (Dynamax)"),
    MAX_RAID_4("max_raid_4", "🔴 Макс-рейд 4★", "🔴 Max Raid 4★", "Логово Макс-рейда 4★ сложности (Dynamax)", "4★ Max Raid den (Dynamax)"),
    MAX_RAID_5("max_raid_5", "🔴 Макс-рейд 5★", "🔴 Max Raid 5★", "Логово Макс-рейда 5★ сложности (Dynamax)", "5★ Max Raid den (Dynamax)"),
    MAX_RAID_6("max_raid_6", "🔴 Макс-рейд 6★", "🔴 Max Raid 6★", "Логово Макс-рейда 6★ сложности (Dynamax)", "6★ Max Raid den (Dynamax)"),
    MAX_RAID_7("max_raid_7", "🔴 Макс-рейд 7★", "🔴 Max Raid 7★", "Логово Макс-рейда 7★ сложности (Dynamax)", "7★ Max Raid den (Dynamax)"),

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

    public static TelegramEventType getRaidEventType(boolean isTera, int stars) {
        if (stars < 1 || stars > 7) return null;
        if (isTera) {
            return switch (stars) {
                case 1 -> TERA_RAID_1;
                case 2 -> TERA_RAID_2;
                case 3 -> TERA_RAID_3;
                case 4 -> TERA_RAID_4;
                case 5 -> TERA_RAID_5;
                case 6 -> TERA_RAID_6;
                case 7 -> TERA_RAID_7;
                default -> null;
            };
        } else {
            return switch (stars) {
                case 1 -> MAX_RAID_1;
                case 2 -> MAX_RAID_2;
                case 3 -> MAX_RAID_3;
                case 4 -> MAX_RAID_4;
                case 5 -> MAX_RAID_5;
                case 6 -> MAX_RAID_6;
                case 7 -> MAX_RAID_7;
                default -> null;
            };
        }
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

            case TERA_RAID_1 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid1.get();
            case TERA_RAID_2 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid2.get();
            case TERA_RAID_3 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid3.get();
            case TERA_RAID_4 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid4.get();
            case TERA_RAID_5 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid5.get();
            case TERA_RAID_6 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid6.get();
            case TERA_RAID_7 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid7.get();

            case MAX_RAID_1 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid1.get();
            case MAX_RAID_2 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid2.get();
            case MAX_RAID_3 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid3.get();
            case MAX_RAID_4 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid4.get();
            case MAX_RAID_5 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid5.get();
            case MAX_RAID_6 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid6.get();
            case MAX_RAID_7 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid7.get();

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

            case TERA_RAID_1 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid1.set(enabled);
            case TERA_RAID_2 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid2.set(enabled);
            case TERA_RAID_3 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid3.set(enabled);
            case TERA_RAID_4 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid4.set(enabled);
            case TERA_RAID_5 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid5.set(enabled);
            case TERA_RAID_6 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid6.set(enabled);
            case TERA_RAID_7 -> EffectivenessConfig.CONFIG.telegramNotifyTeraRaid7.set(enabled);

            case MAX_RAID_1 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid1.set(enabled);
            case MAX_RAID_2 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid2.set(enabled);
            case MAX_RAID_3 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid3.set(enabled);
            case MAX_RAID_4 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid4.set(enabled);
            case MAX_RAID_5 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid5.set(enabled);
            case MAX_RAID_6 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid6.set(enabled);
            case MAX_RAID_7 -> EffectivenessConfig.CONFIG.telegramNotifyMaxRaid7.set(enabled);

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
