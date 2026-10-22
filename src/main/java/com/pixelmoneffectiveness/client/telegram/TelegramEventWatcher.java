package com.pixelmoneffectiveness.client.telegram;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.raids.RaidType;
import com.pixelmonmod.pixelmon.battles.raids.data.RaidData;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import com.pixelmonmod.pixelmon.entities.DenEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmoneffectiveness.client.radar.PokeLootTier;
import com.pixelmoneffectiveness.client.radar.PokeStopTier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramEventWatcher {

    private static final Map<String, Long> RECENT_RAID_ANNOUNCEMENTS = new ConcurrentHashMap<>();
    private static final Pattern RAID_MSG_RU = Pattern.compile("Рейд на (.+) был замечен в (.+) биоме!", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAID_MSG_EN = Pattern.compile("(.+) has appeared in a den in a (.+) biome!", Pattern.CASE_INSENSITIVE);

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

    public static boolean isWildPokemon(PixelmonEntity pixelmon) {
        if (pixelmon == null || !pixelmon.isAlive()) {
            return false;
        }

        // 1. Direct entity checks
        try {
            if (pixelmon.hasOwner() || pixelmon.getOwnerUUID() != null || pixelmon.getOwner() != null) {
                return false;
            }
            if (pixelmon.isTamed()) {
                return false;
            }
            if (pixelmon.getPlayerParty() != null) {
                return false;
            }
            if (pixelmon.getPlayerStorage() != null && pixelmon.getPlayerStorage().isPresent()) {
                return false;
            }
        } catch (Throwable ignored) {}

        Pokemon pokemon = pixelmon.getPokemon();
        if (pokemon == null) {
            return false;
        }

        // 2. Pokemon data ownership checks (Trainer / Owner / Storage)
        try {
            if (pokemon.getOwnerPlayerUUID() != null) {
                return false;
            }
            if (pokemon.getOriginalTrainerUUID() != null) {
                return false;
            }
            String otName = pokemon.getOriginalTrainer();
            if (otName != null && !otName.trim().isEmpty()) {
                return false;
            }
            if (pokemon.getStorage() != null) {
                return false;
            }
        } catch (Throwable ignored) {}

        // 3. Local player checks (party & belongsTo)
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (pixelmon.belongsTo(mc.player)) {
                    return false;
                }
                UUID myUuid = mc.player.getUUID();
                if (myUuid != null) {
                    if (myUuid.equals(pokemon.getOwnerPlayerUUID()) || myUuid.equals(pokemon.getOriginalTrainerUUID())) {
                        return false;
                    }
                }
                PlayerPartyStorage party = StorageProxy.getPartyNow(mc.player);
                if (party == null) {
                    party = ClientStorageManager.party();
                }
                if (party != null) {
                    UUID pokeUuid = pokemon.getUUID();
                    if (pokeUuid != null) {
                        if (party.get(pokeUuid) != null || party.getSlot(pokeUuid) >= 0) {
                            return false;
                        }
                    }
                    Pokemon[] all = party.getAll();
                    if (all != null) {
                        for (Pokemon p : all) {
                            if (p != null && (p == pokemon || (pokeUuid != null && pokeUuid.equals(p.getUUID())))) {
                                return false;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return true;
    }

    public static void handlePixelmonSpawn(PixelmonEntity pixelmon, Level level) {
        if (!isWildPokemon(pixelmon)) {
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

        BlockPos pos = pixelmon.blockPosition();
        sb.append(TelegramBotClient.formatLocationBlock(pos, level));

        TelegramBotClient.sendNotification(sb.toString());
    }

    public static boolean isTeraRaid(DenEntity den, RaidData raidData) {
        try {
            if (raidData != null && raidData.getType() != null) {
                if (raidData.getType().is(RaidType.TERA)) return true;
                var optKey = raidData.getType().unwrapKey();
                if (optKey.isPresent() && optKey.get().location().getPath().toLowerCase().contains("tera")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (den != null && den.getRaidType() != null) {
                if (den.getRaidType().is(RaidType.TERA)) return true;
                var optKey = den.getRaidType().unwrapKey();
                if (optKey.isPresent() && optKey.get().location().getPath().toLowerCase().contains("tera")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static void handleDenSpawn(DenEntity den, Level level) {
        if (den == null || den.isRemoved()) return;

        Optional<RaidData> optData = den.getData();
        if (!optData.isPresent()) return;
        RaidData raidData = optData.get();

        Pokemon pokemon = raidData.getPokemon();
        if (pokemon == null) return;
        int stars = raidData.getStars();
        if (stars <= 0) return;

        UUID notifyKey = (pokemon.getUUID() != null) ? pokemon.getUUID() : den.getUUID();
        if (TelegramBotClient.isEntityNotified(notifyKey)) return;

        boolean isTera = isTeraRaid(den, raidData);
        TelegramEventType raidEvent = TelegramEventType.getRaidEventType(isTera, stars);

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
                    : "✨🔴 <b>[Pixelmon] Шайни Макс-Рейд (" + stars + "★)!</b>";
        } else if (isLegendary && TelegramEventType.LEGENDARY.isEnabled()) {
            notify = true;
            header = isTera
                    ? "👑💎 <b>[Pixelmon] Легендарный Тера-Рейд (" + stars + "★)!</b>"
                    : "👑🔴 <b>[Pixelmon] Легендарный Макс-Рейд (" + stars + "★)!</b>";
        } else if (isTracked) {
            notify = true;
            header = isTera
                    ? "🎯💎 <b>[Pixelmon] Тера-Рейд с отслеживаемым покемоном (" + stars + "★)!</b>"
                    : "🎯🔴 <b>[Pixelmon] Макс-Рейд с отслеживаемым покемоном (" + stars + "★)!</b>";
        } else if (raidEvent != null && raidEvent.isEnabled()) {
            notify = true;
            header = isTera
                    ? "💎 <b>[Pixelmon] Обнаружен Тера-Рейд (" + stars + "★)!</b>"
                    : "🔴 <b>[Pixelmon] Обнаружен Макс-Рейд (" + stars + "★)!</b>";
        }

        if (!notify) return;

        TelegramBotClient.markEntityNotified(notifyKey);
        TelegramBotClient.markEntityNotified(den.getUUID());

        String pokeName = pokemon.getDisplayName().getString();
        RECENT_RAID_ANNOUNCEMENTS.put(pokeName.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis());
        if (pokemon.getSpecies() != null) {
            RECENT_RAID_ANNOUNCEMENTS.put(pokemon.getSpecies().getName().toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n\n");
        sb.append("▫️ <b>Покемон:</b> ").append(TelegramBotClient.escapeHtml(pokeName));
        sb.append(" (Ур. ").append(pokemon.getPokemonLevel()).append(")\n");
        sb.append("▫️ <b>Сложность:</b> ").append(stars).append("★\n");
        sb.append("▫️ <b>Тип рейда:</b> ").append(isTera ? "💎 Тера-Рейд" : "🔴 Макс-Рейд (Dynamax)").append("\n");
        if (isTera && pokemon.getTeraType() != null) {
            String typeName = "";
            try {
                typeName = pokemon.getTeraType().value().name().getString();
            } catch (Throwable t) {
                typeName = pokemon.getTeraType().toString();
            }
            sb.append("▫️ <b>Тера-тип:</b> ").append(TelegramBotClient.escapeHtml(typeName)).append("\n");
        }

        BlockPos pos = den.blockPosition();
        sb.append(TelegramBotClient.formatLocationBlock(pos, level));

        TelegramBotClient.sendNotification(sb.toString());
    }

    @SubscribeEvent
    public static void onClientChatReceived(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
        if (!TelegramBotClient.isConfigured()) return;
        Component comp = event.getMessage();
        if (comp == null) return;
        String raw = comp.getString().trim();

        String pokemonName = null;
        String biomeName = null;

        Matcher mRu = RAID_MSG_RU.matcher(raw);
        if (mRu.find()) {
            pokemonName = mRu.group(1).trim();
            biomeName = mRu.group(2).trim();
        } else {
            Matcher mEn = RAID_MSG_EN.matcher(raw);
            if (mEn.find()) {
                pokemonName = mEn.group(1).trim();
                biomeName = mEn.group(2).trim();
            }
        }

        if (pokemonName != null && biomeName != null) {
            handleChatRaidAnnouncement(pokemonName, biomeName);
        }
    }

    private static void handleChatRaidAnnouncement(String pokemonName, String biomeName) {
        long now = System.currentTimeMillis();
        Long lastTime = RECENT_RAID_ANNOUNCEMENTS.get(pokemonName.toLowerCase(java.util.Locale.ROOT));
        if (lastTime != null && (now - lastTime < 30_000L)) {
            // Already handled with full coordinates by DenEntityMixin
            return;
        }

        boolean isLegendaryNotification = TelegramEventType.LEGENDARY.isEnabled();
        boolean isTracked = TelegramBotClient.isPokemonTracked(pokemonName);

        if (!isLegendaryNotification && !isTracked) {
            return;
        }

        RECENT_RAID_ANNOUNCEMENTS.put(pokemonName.toLowerCase(java.util.Locale.ROOT), now);

        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String header = isTracked
                ? "🎯🔴 <b>[Pixelmon] Рейд с отслеживаемым покемоном!</b>"
                : "👑🔴 <b>[Pixelmon] Рейд на легендарного покемона!</b>";

        String msg = header + "\n\n" +
                "▫️ <b>Покемон:</b> " + TelegramBotClient.escapeHtml(pokemonName) + "\n" +
                "▫️ <b>Биом:</b> " + TelegramBotClient.escapeHtml(biomeName) + "\n" +
                "▫️ <b>Время:</b> <code>" + time + "</code>\n" +
                "▫️ <i>(Объявление из чата сервера — точные координаты будут получены при приближении к логову)</i>";

        TelegramBotClient.sendNotification(msg);
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
