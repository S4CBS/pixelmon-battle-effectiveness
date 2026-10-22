package com.pixelmoneffectiveness.client.telegram;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.battles.raids.RaidType;
import com.pixelmonmod.pixelmon.battles.raids.data.RaidData;
import com.pixelmonmod.pixelmon.entities.DenEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmoneffectiveness.client.radar.DittoDisguiseHelper;
import com.pixelmoneffectiveness.client.radar.PokeLootTier;
import com.pixelmoneffectiveness.client.radar.PokeStopTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

public class TelegramEventWatcher {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!TelegramBotClient.isConfigured()) {
            return;
        }

        Level level = event.getLevel();
        if (event.getEntity() instanceof PixelmonEntity pixelmon) {
            handlePixelmonSpawn(pixelmon, level);
        } else if (event.getEntity() instanceof DenEntity den) {
            handleDenSpawn(den, level);
        }
    }

    public static void handlePixelmonSpawn(PixelmonEntity pixelmon, Level level) {
        if (pixelmon == null || !pixelmon.isAlive() || pixelmon.getOwner() != null) {
            return; // Wild alive only
        }

        if (TelegramBotClient.isEntityNotified(pixelmon.getUUID())) {
            return;
        }

        Pokemon pokemon = pixelmon.getPokemon();
        if (pokemon == null) return;

        boolean isShiny = pokemon.isShiny();
        boolean isLegendary = pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
        boolean isBoss = false;
        boolean isUltraBoss = false;
        boolean isLegendaryBoss = false;
        boolean isEpicBoss = false;
        String bossTierTitle = "";

        try {
            if (pixelmon.isBossPokemon()) {
                isBoss = true;
            }
            BossTier tier = pixelmon.getBossTier();
            if (tier != null && tier.isBoss()) {
                isBoss = true;
                bossTierTitle = (tier.getName() != null)
                        ? Component.translatable(tier.getName()).getString()
                        : tier.getID();

                String tierId = (tier.getID() != null) ? tier.getID().toLowerCase() : "";
                if (tierId.equals("ultimate") || tierId.contains("ultra")) {
                    isUltraBoss = true;
                } else if (tierId.equals("legendary")) {
                    isLegendaryBoss = true;
                } else if (tierId.equals("epic")) {
                    isEpicBoss = true;
                }
            }
        } catch (Throwable ignored) {}

        if (!isBoss && pokemon.isBoss()) {
            isBoss = true;
        }

        boolean isConfirmedDitto = DittoDisguiseHelper.isConfirmedDitto(pixelmon);
        String speciesName = (pokemon.getSpecies() != null) ? pokemon.getSpecies().getName() : "";
        boolean isCustomTracked = TelegramBotClient.isPokemonTracked(speciesName);

        // Determine if this event should trigger a notification
        boolean notify = false;
        String eventHeader = "";

        if (isShiny && TelegramEventType.SHINY.isEnabled()) {
            notify = true;
            eventHeader = "✨ <b>[Pixelmon] Спавн Шайни покемона!</b>";
        } else if (isLegendary && TelegramEventType.LEGENDARY.isEnabled()) {
            notify = true;
            eventHeader = "👑 <b>[Pixelmon] Спавн Легендарного покемона!</b>";
        } else if (isUltraBoss && TelegramEventType.ULTRA_BOSS.isEnabled()) {
            notify = true;
            eventHeader = "👹 <b>[Pixelmon] Спавн Ультра-босса!</b>";
        } else if (isLegendaryBoss && TelegramEventType.LEGENDARY_BOSS.isEnabled()) {
            notify = true;
            eventHeader = "👑 <b>[Pixelmon] Спавн Легендарного босса!</b>";
        } else if (isEpicBoss && TelegramEventType.EPIC_BOSS.isEnabled()) {
            notify = true;
            eventHeader = "🔮 <b>[Pixelmon] Спавн Эпического босса!</b>";
        } else if (isBoss && TelegramEventType.ALL_BOSS.isEnabled()) {
            notify = true;
            eventHeader = "⚔️ <b>[Pixelmon] Спавн Босса!</b>";
        } else if (isConfirmedDitto && TelegramEventType.DITTO.isEnabled()) {
            notify = true;
            eventHeader = "🎭 <b>[Pixelmon] Обнаружен замаскированный Дитто!</b>";
        } else if (isCustomTracked) {
            notify = true;
            eventHeader = "🎯 <b>[Pixelmon] Спавн отслеживаемого покемона!</b>";
        }

        if (!notify) {
            return;
        }

        TelegramBotClient.markEntityNotified(pixelmon.getUUID());

        String displayName = pokemon.getDisplayName().getString();
        String translatedName = (pokemon.getSpecies() != null && pokemon.getSpecies().getNameTranslation() != null)
                ? pokemon.getSpecies().getNameTranslation().getString() : "";
        String pokeNameStr = TelegramBotClient.escapeHtml(displayName);
        if (!translatedName.isEmpty() && !translatedName.equalsIgnoreCase(speciesName) && !translatedName.equalsIgnoreCase(displayName)) {
            pokeNameStr += " (" + TelegramBotClient.escapeHtml(translatedName) + ")";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(eventHeader).append("\n\n");
        sb.append("▫️ <b>Покемон:</b> ").append(pokeNameStr);
        if (isShiny) sb.append(" ✨");
        sb.append(" (Ур. ").append(pokemon.getPokemonLevel()).append(")\n");

        if (isBoss && !bossTierTitle.isEmpty()) {
            sb.append("▫️ <b>Ранг босса:</b> ").append(TelegramBotClient.escapeHtml(bossTierTitle)).append("\n");
        }
        if (isConfirmedDitto) {
            sb.append("▫️ <b>Маскировка:</b> Замаскирован под ").append(pokeNameStr).append("\n");
        }

        BlockPos pos = pixelmon.blockPosition();
        sb.append(TelegramBotClient.formatLocationBlock(pos, level));

        TelegramBotClient.sendNotification(sb.toString());
    }

    public static void handleDenSpawn(DenEntity den, Level level) {
        if (den == null || !den.isAlive()) return;
        if (TelegramBotClient.isEntityNotified(den.getUUID())) return;

        Optional<RaidData> optData = den.getData();
        if (!optData.isPresent()) return;
        RaidData raidData = optData.get();

        Pokemon pokemon = raidData.getPokemon();
        if (pokemon == null) return;
        int stars = raidData.getStars();
        if (stars <= 0) return;

        boolean isTera = false;
        try {
            if (raidData.getType() != null && raidData.getType().is(RaidType.TERA)) {
                isTera = true;
            }
        } catch (Throwable ignored) {}

        boolean isLegendary = pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
        boolean isShiny = pokemon.isShiny();
        String speciesName = (pokemon.getSpecies() != null) ? pokemon.getSpecies().getName() : "";
        boolean isTracked = TelegramBotClient.isPokemonTracked(speciesName);

        boolean notify = false;
        String header = "";

        if (isShiny && TelegramEventType.SHINY.isEnabled()) {
            notify = true;
            header = isTera
                    ? "✨💎 <b>[Pixelmon] Шайни Тера-Рейд (" + stars + "★)!</b>"
                    : "✨⭐ <b>[Pixelmon] Шайни Рейд (" + stars + "★)!</b>";
        } else if (isLegendary && TelegramEventType.LEGENDARY.isEnabled()) {
            notify = true;
            header = isTera
                    ? "✨💎 <b>[Pixelmon] Легендарный Тера-Рейд (" + stars + "★)!</b>"
                    : "✨⭐ <b>[Pixelmon] Легендарный Рейд (" + stars + "★)!</b>";
        } else if (isTracked) {
            notify = true;
            header = isTera
                    ? "📖💎 <b>[Pixelmon] Тера-Рейд с отслеживаемым покемоном (" + stars + "★)!</b>"
                    : "📖⭐ <b>[Pixelmon] Рейд с отслеживаемым покемоном (" + stars + "★)!</b>";
        } else if (stars >= 5 && TelegramEventType.TERA_RAID.isEnabled()) {
            notify = true;
            header = isTera
                    ? "💎 <b>[Pixelmon] Обнаружен Тера-Рейд (" + stars + "★)!</b>"
                    : "⭐ <b>[Pixelmon] Обнаружен Высокоуровневый Рейд (" + stars + "★)!</b>";
        } else if (TelegramEventType.ALL_RAID.isEnabled()) {
            notify = true;
            header = isTera
                    ? "💎 <b>[Pixelmon] Обнаружен Тера-Рейд (" + stars + "★)!</b>"
                    : "⭐ <b>[Pixelmon] Обнаружен Рейд (" + stars + "★)!</b>";
        }

        if (!notify) return;

        TelegramBotClient.markEntityNotified(den.getUUID());

        String pokeName = pokemon.getDisplayName().getString();
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n\n");
        sb.append("▫️ <b>Покемон:</b> ").append(TelegramBotClient.escapeHtml(pokeName));
        sb.append(" (").append(stars).append("★");
        if (isTera && pokemon.getTeraType() != null) {
            String typeName = "";
            try {
                typeName = pokemon.getTeraType().value().name().getString();
            } catch (Throwable t) {
                typeName = pokemon.getTeraType().toString();
            }
            sb.append(" Тера: ").append(TelegramBotClient.escapeHtml(typeName));
        }
        sb.append(")\n");

        BlockPos pos = den.blockPosition();
        sb.append(TelegramBotClient.formatLocationBlock(pos, level));

        TelegramBotClient.sendNotification(sb.toString());
    }

    public static void onLootChestDiscovered(BlockPos pos, PokeLootTier tier, boolean isHidden, Level level) {
        if (pos == null || !TelegramBotClient.isConfigured()) return;
        if (TelegramBotClient.isPositionNotified(pos)) return;

        boolean notify = false;
        String header = "";
        String tierName = "";

        if (tier == PokeLootTier.MASTER && TelegramEventType.MASTER_LOOT.isEnabled()) {
            notify = true;
            header = "🟣 <b>[Pixelmon] Найден Master Ball PokéLoot!</b>";
            tierName = "Master Ball";
        } else if (tier == PokeLootTier.BEAST && TelegramEventType.BEAST_LOOT.isEnabled()) {
            notify = true;
            header = "🟡 <b>[Pixelmon] Найден Beast Ball PokéLoot!</b>";
            tierName = "Beast Ball";
        } else if (tier == PokeLootTier.ULTRA && TelegramEventType.ULTRA_LOOT.isEnabled()) {
            notify = true;
            header = "🔵 <b>[Pixelmon] Найден Ultra Ball PokéLoot!</b>";
            tierName = "Ultra Ball";
        }

        if (!notify) return;

        TelegramBotClient.markPositionNotified(pos);

        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n\n");
        sb.append("▫️ <b>Сундук:</b> ").append(tierName);
        if (isHidden) sb.append(" <i>(Скрытый)</i>");
        sb.append("\n");
        sb.append(TelegramBotClient.formatLocationBlock(pos, level));

        TelegramBotClient.sendNotification(sb.toString());
    }

    public static void onPokeStopDiscovered(BlockPos pos, PokeStopTier tier, Level level) {
        if (pos == null || !TelegramBotClient.isConfigured()) return;
        if (TelegramBotClient.isPositionNotified(pos)) return;

        if (tier == PokeStopTier.LEGENDARY && TelegramEventType.LEGENDARY_POKESTOP.isEnabled()) {
            TelegramBotClient.markPositionNotified(pos);
            String sb = "🏛️ <b>[Pixelmon] Найден Легендарный ПокеСтоп!</b>\n\n" +
                    "▫️ <b>Тип:</b> Золотой Легендарный ПокеСтоп\n" +
                    TelegramBotClient.formatLocationBlock(pos, level);
            TelegramBotClient.sendNotification(sb);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!TelegramBotClient.isConfigured() || !TelegramEventType.PLAYER_DEATH.isEnabled()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            String playerName = player.getName().getString();
            String deathMessage = event.getSource().getLocalizedDeathMessage(player).getString();
            BlockPos pos = player.blockPosition();
            Level level = player.level();

            String sb = "💀 <b>[Pixelmon] Смерть игрока!</b>\n\n" +
                    "▫️ <b>Игрок:</b> " + TelegramBotClient.escapeHtml(playerName) + "\n" +
                    "▫️ <b>Причина:</b> " + TelegramBotClient.escapeHtml(deathMessage) + "\n" +
                    TelegramBotClient.formatLocationBlock(pos, level);

            TelegramBotClient.sendNotification(sb);
        }
    }
}
