package com.pixelmoneffectiveness.client.autofarm;

import com.pixelmonmod.pixelmon.api.moveskills.MoveSkill;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.client.keybindings.SendPokemonKey;
import com.pixelmonmod.pixelmon.client.listener.SendoutListener;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import com.pixelmonmod.pixelmon.comm.packetHandlers.moveskills.UseMoveSkillPacket;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MintAutoFarmClient {

    public static final KeyMapping TOGGLE_AUTOFARM_KEY = new KeyMapping(
        "key.pixelmoneffectiveness.toggle_mint_farm",
        74, // GLFW_KEY_J
        "key.categories.pixelmoneffectiveness"
    );

    public enum State {
        IDLE,
        FIND_GRASS,
        SELECT_POKEMON,
        SPAWN_POKEMON,
        FORAGING,
        WAIT_FOR_LOOT,
        CLEAN_INVENTORY,
        RECALL_POKEMON,
        COOLDOWN_WAIT
    }

    private static boolean active = false;
    private static State state = State.IDLE;
    private static int stateTimer = 0;
    private static BlockPos targetGrassPos = null;

    private static int seedsFound = 0;
    private static int mintsFound = 0;
    private static int junkDropped = 0;
    private static String statusText = "Готов к запуску";

    // Snapshot of player inventory (slots 9 to 44) before each forage
    private static final Map<Integer, ItemStack> inventorySnapshot = new HashMap<>();

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean val) {
        active = val;
        if (!active) {
            state = State.IDLE;
            statusText = "Выключен";
        } else {
            state = State.FIND_GRASS;
            stateTimer = 0;
            statusText = "Поиск блока травы...";
        }
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MintAutoFarmClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(MintAutoFarmClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MintAutoFarmClient::onRenderGui);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTOFARM_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (active) setActive(false);
            return;
        }

        while (TOGGLE_AUTOFARM_KEY.consumeClick()) {
            setActive(!active);
            boolean isRu = isRussianLanguage(mc);
            String msg = active
                ? (isRu ? "§a[Автофарм] §eВКЛЮЧЕН §7(J - выкл). Стойте на блоке травы под открытым небом!" : "§a[AutoFarm] §eENABLED §7(J to toggle). Stand on grass under open sky!")
                : (isRu ? "§e[Автофарм] §cВЫКЛЮЧЕН." : "§e[AutoFarm] §cDISABLED.");
            mc.player.displayClientMessage(Component.literal(msg), true);
        }

        if (!active) return;

        // If player is dead, inside a gui (except chat), or in battle -> pause
        if (!mc.player.isAlive() || (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen))) {
            return;
        }

        if (stateTimer > 0) {
            stateTimer--;
            return;
        }

        handleStateMachine(mc);
    }

    private static void handleStateMachine(Minecraft mc) {
        PlayerPartyStorage party = ClientStorageManager.party();
        if (party == null || party.countPokemon() == 0) {
            statusText = "В команде нет покемонов!";
            stateTimer = 20;
            return;
        }

        MoveSkill forage = MoveSkill.getMoveSkillByID("forage");
        if (forage == null) {
            statusText = "Способность 'forage' не найдена!";
            setActive(false);
            return;
        }

        switch (state) {
            case FIND_GRASS -> {
                targetGrassPos = findTargetGrassBlock(mc);
                if (targetGrassPos == null) {
                    statusText = "§cНет блока травы под открытым небом!";
                    stateTimer = 20; // Recheck in 1 second
                    return;
                }
                statusText = "Трава найдена: " + targetGrassPos.getX() + ", " + targetGrassPos.getY() + ", " + targetGrassPos.getZ();
                state = State.SELECT_POKEMON;
                stateTimer = 2;
            }

            case SELECT_POKEMON -> {
                int partyCount = party.countAll();
                int currentSlot = party.getSelectedSlot();
                int readySlot = -1;
                int minCooldown = Integer.MAX_VALUE;

                // Check current slot first
                Pokemon currentPoke = party.getSelectedPokemon();
                if (currentPoke != null && !currentPoke.isEgg() && !currentPoke.isMoveSkillCoolingDown(forage)) {
                    readySlot = currentSlot;
                } else {
                    // Search party for a ready slot
                    for (int i = 0; i < partyCount; i++) {
                        Pokemon p = party.get(i);
                        if (p == null || p.isEgg()) continue;
                        if (!p.isMoveSkillCoolingDown(forage)) {
                            readySlot = i;
                            break;
                        } else {
                            int cd = p.getMoveSkillCooldownTicks(forage);
                            if (cd < minCooldown) {
                                minCooldown = cd;
                            }
                        }
                    }
                }

                if (readySlot != -1) {
                    if (readySlot != currentSlot) {
                        party.setSelectedSlot(readySlot, true);
                    }
                    Pokemon selected = party.getSelectedPokemon();
                    String pokeName = selected != null ? selected.getDisplayName().getString() : "Покемон";
                    statusText = "Выбран: " + pokeName + " [Готов]";
                    state = State.SPAWN_POKEMON;
                    stateTimer = 4;
                } else {
                    int sec = (minCooldown + 19) / 20;
                    statusText = "Ожидание отката: " + sec + "с (пачка: " + party.countPokemon() + ")";
                    state = State.COOLDOWN_WAIT;
                    stateTimer = Math.min(20, Math.max(5, minCooldown));
                }
            }

            case COOLDOWN_WAIT -> {
                state = State.SELECT_POKEMON;
                stateTimer = 1;
            }

            case SPAWN_POKEMON -> {
                Pokemon p = party.getSelectedPokemon();
                if (p == null || p.isEgg()) {
                    state = State.SELECT_POKEMON;
                    return;
                }

                String pName = p.getDisplayName().getString();
                boolean inWorld = SendoutListener.isInWorld(p.getUUID(), mc.level);
                if (!inWorld) {
                    SendPokemonKey.sendPokemon();
                    statusText = "Выпускаем: " + pName + "...";
                    state = State.FORAGING;
                    stateTimer = 15; // Wait for spawn
                } else {
                    statusText = pName + " в мире. Копаем...";
                    state = State.FORAGING;
                    stateTimer = 2;
                }
            }

            case FORAGING -> {
                Pokemon p = party.getSelectedPokemon();
                if (p == null || p.isEgg()) {
                    state = State.SELECT_POKEMON;
                    return;
                }

                boolean inWorld = SendoutListener.isInWorld(p.getUUID(), mc.level);
                if (!inWorld) {
                    // Try to send out again or wait
                    SendPokemonKey.sendPokemon();
                    stateTimer = 10;
                    return;
                }

                // Verify grass block is still valid
                if (targetGrassPos == null || !isValidGrass(mc.level, targetGrassPos)) {
                    targetGrassPos = findTargetGrassBlock(mc);
                    if (targetGrassPos == null) {
                        state = State.FIND_GRASS;
                        return;
                    }
                }

                // Snapshot inventory before action
                takeInventorySnapshot(mc);

                // Send Forage packet to server
                UseMoveSkillPacket packet = new UseMoveSkillPacket(
                    party.getSelectedSlot(),
                    forage,
                    targetGrassPos,
                    Direction.UP
                );
                NetworkHelper.sendToServer(packet);

                statusText = "Фуражирование: " + p.getDisplayName().getString() + " копает дёрн...";
                state = State.WAIT_FOR_LOOT;
                // Wait 25 ticks (1.25s) for forage animation, item spawn, and pickup
                stateTimer = 25;
            }

            case WAIT_FOR_LOOT -> {
                state = State.CLEAN_INVENTORY;
                stateTimer = 2;
            }

            case CLEAN_INVENTORY -> {
                filterInventory(mc);
                state = State.RECALL_POKEMON;
                stateTimer = 5;
            }

            case RECALL_POKEMON -> {
                Pokemon p = party.getSelectedPokemon();
                if (p != null) {
                    boolean inWorld = SendoutListener.isInWorld(p.getUUID(), mc.level);
                    if (inWorld) {
                        SendPokemonKey.sendPokemon();
                    }
                }

                // Cycle to next pokemon in party
                int nextSlot = (party.getSelectedSlot() + 1) % party.countAll();
                party.setSelectedSlot(nextSlot, true);

                statusText = "Переключение на слот " + (nextSlot + 1) + "...";
                state = State.SELECT_POKEMON;
                stateTimer = 6;
            }

            case IDLE -> {
                // Do nothing
            }
        }
    }

    private static void takeInventorySnapshot(Minecraft mc) {
        inventorySnapshot.clear();
        if (mc.player == null) return;
        for (int slotId = 9; slotId <= 44; slotId++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(slotId).getItem();
            if (!stack.isEmpty()) {
                inventorySnapshot.put(slotId, stack.copy());
            }
        }
    }

    private static void filterInventory(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;

        boolean dropJunk = EffectivenessConfig.CONFIG == null || EffectivenessConfig.CONFIG.autoFarmDropNonMint.get();

        for (int slotId = 9; slotId <= 44; slotId++) {
            ItemStack current = mc.player.inventoryMenu.getSlot(slotId).getItem();
            if (current.isEmpty()) continue;

            ItemStack before = inventorySnapshot.get(slotId);
            boolean isNew = (before == null || before.isEmpty());
            boolean countIncreased = (!isNew && ItemStack.isSameItemSameComponents(current, before) && current.getCount() > before.getCount());

            if (isNew || countIncreased) {
                int addedCount = isNew ? current.getCount() : (current.getCount() - before.getCount());

                if (isMintItem(current)) {
                    // Safe item! Keep it!
                    if (isMintSeed(current)) {
                        seedsFound += addedCount;
                        sendChat(mc, "§a[Автофарм] §e+ Семена Мяты x" + addedCount + " §7(Всего: " + seedsFound + " шт.)");
                    } else {
                        mintsFound += addedCount;
                        sendChat(mc, "§a[Автофарм] §d+ " + current.getHoverName().getString() + " x" + addedCount + " §7(Всего: " + mintsFound + " шт.)");
                    }
                    mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
                } else if (dropJunk) {
                    // Junk item picked up from forage (berries, seeds, pebbles, dirt, sticks, etc.)
                    junkDropped += addedCount;
                    mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId,
                        slotId,
                        1, // 1 = throw entire stack
                        ClickType.THROW,
                        mc.player
                    );
                }
            }
        }
    }

    public static boolean isMintItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String ns = id.getNamespace();
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if ("pixelmon".equals(ns)) {
            return path.contains("mint");
        }
        return false;
    }

    public static boolean isMintSeed(ItemStack stack) {
        if (!isMintItem(stack)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().toLowerCase(Locale.ROOT).contains("seed");
    }

    private static BlockPos findTargetGrassBlock(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        BlockPos playerPos = mc.player.blockPosition();

        // 1. Check block directly below feet
        BlockPos below = playerPos.below();
        if (isValidGrass(mc.level, below)) {
            return below;
        }

        // 2. Check current player block
        if (isValidGrass(mc.level, playerPos)) {
            return playerPos;
        }

        // 3. Search in radius 3
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos check = playerPos.offset(dx, dy, dz);
                    if (isValidGrass(mc.level, check)) {
                        double distSq = check.distSqr(playerPos);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestPos = check;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private static boolean isValidGrass(ClientLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
            return false;
        }
        BlockPos above = pos.above();
        if (!level.canSeeSky(above)) {
            return false;
        }
        BlockState aboveState = level.getBlockState(above);
        return !aboveState.isSolid() || aboveState.canBeReplaced();
    }

    private static void sendChat(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        if (EffectivenessConfig.CONFIG != null && !EffectivenessConfig.CONFIG.autoFarmHudEnabled.get()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        boolean isRu = isRussianLanguage(mc);

        int panelX = 10;
        int panelY = 85;
        int panelW = 205;
        int panelH = 58;

        // Semi-transparent background with emerald green border
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA111A1E);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF2ECC71);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF2ECC71);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF2ECC71);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF2ECC71);

        String title = isRu ? "§a☘ Автофарм Мяты §7(J - стоп)" : "§a☘ Mint Auto-Farm §7(J to stop)";
        graphics.drawString(font, title, panelX + 6, panelY + 5, 0xFFFFFFFF, true);

        String st = "§7" + (isRu ? "Статус: " : "Status: ") + "§f" + statusText;
        if (font.width(st) > panelW - 12) {
            st = font.plainSubstrByWidth(st, panelW - 16) + "...";
        }
        graphics.drawString(font, st, panelX + 6, panelY + 18, 0xFFDDDDDD, true);

        String stats1 = String.format(Locale.ROOT, "§e%s: §f%d §7| §d%s: §f%d",
            (isRu ? "Семена" : "Seeds"), seedsFound,
            (isRu ? "Мята" : "Mints"), mintsFound
        );
        graphics.drawString(font, stats1, panelX + 6, panelY + 31, 0xFFFFFFFF, true);

        String stats2 = String.format(Locale.ROOT, "§c%s: §f%d шт.",
            (isRu ? "Сброшено мусора" : "Junk dropped"), junkDropped
        );
        graphics.drawString(font, stats2, panelX + 6, panelY + 44, 0xFFAAAAAA, true);
    }

    private static boolean isRussianLanguage(Minecraft mc) {
        return mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
