package com.pixelmoneffectiveness.client.autofarm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pixelmonmod.pixelmon.api.moveskills.MoveSkill;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.api.battles.AttackCategory;
import com.pixelmonmod.pixelmon.api.battles.BattleMode;
import com.pixelmonmod.pixelmon.api.battles.action.type.SwitchBattleAction;
import com.pixelmonmod.pixelmon.api.context.ContextKeys;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Moveset;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.BattleScreen;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import com.pixelmonmod.pixelmon.client.keybindings.SendPokemonKey;
import com.pixelmonmod.pixelmon.client.listener.SendoutListener;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import com.pixelmonmod.pixelmon.comm.packetHandlers.battles.BattleActionPacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.battles.FleePacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.moveskills.UseMoveSkillPacket;
import com.pixelmoneffectiveness.util.PixelmonEffectivenessHelper;
import com.pixelmonmod.pixelmon.comm.packetHandlers.storage.ServerboundSetSelectedSlotPacket;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.entities.pokeballs.OccupiedPokeBallEntity;
import com.pixelmonmod.pixelmon.entities.pokeballs.PokeBallEntity;
import com.pixelmoneffectiveness.client.autofarm.loot.ForageLootRegistry;
import com.pixelmoneffectiveness.client.autofarm.loot.ForageLootScreen;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MintAutoFarmClient {

    public static final KeyMapping TOGGLE_AUTOFARM_KEY = new KeyMapping(
        "key.pixelmoneffectiveness.toggle_mint_farm",
        74, // GLFW_KEY_J
        "key.categories.pixelmoneffectiveness"
    );

    public static final KeyMapping OPEN_AUTOFARM_FILTER_KEY = new KeyMapping(
        "key.pixelmoneffectiveness.forage_loot_filter",
        75, // GLFW_KEY_K
        "key.categories.pixelmoneffectiveness"
    );

    public enum State {
        IDLE,
        FIND_BEST_BLOCK,
        SELECT_POKEMON,
        SPAWN_POKEMON,
        WAIT_FOR_SPAWN,
        READY_TO_FORAGE,
        FORAGING,
        WAIT_FOR_LOOT,
        CLEAN_INVENTORY,
        RECALL_POKEMON,
        WAIT_FOR_RECALL,
        CYCLE_SLOT,
        COOLDOWN_WAIT
    }

    private static boolean active = false;
    private static State state = State.IDLE;
    private static int stateTimer = 0;
    private static BlockPos targetBlockPos = null;
    private static String targetBlockName = "Нет";
    private static int targetBlockScore = 0;

    private static int seedsFound = 0;
    private static int mintsFound = 0;
    private static int otherKeptFound = 0;
    private static int junkDropped = 0;
    private static String statusText = "Готов к запуску";
    private static boolean biomeWarning = false;

    private static long lastThrowTime = 0;
    private static int spawnWaitTicks = 0;
    private static int recallWaitTicks = 0;
    private static boolean wasInBattle = false;
    private static int battleTicks = 0;
    private static int fleeAttempts = 0;
    private static int switchCooldownTicks = 0;
    private static long lastHealTime = 0;

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
            targetBlockPos = null;
            spawnWaitTicks = 0;
            recallWaitTicks = 0;
            wasInBattle = false;
            battleTicks = 0;
            fleeAttempts = 0;
            switchCooldownTicks = 0;
        } else {
            state = State.FIND_BEST_BLOCK;
            stateTimer = 0;
            statusText = "Поиск блока...";
            lastThrowTime = 0;
            spawnWaitTicks = 0;
            recallWaitTicks = 0;
            wasInBattle = false;
            battleTicks = 0;
            fleeAttempts = 0;
            switchCooldownTicks = 0;
        }
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MintAutoFarmClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(MintAutoFarmClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MintAutoFarmClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(MintAutoFarmClient::onRenderLevelStage);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTOFARM_KEY);
        event.register(OPEN_AUTOFARM_FILTER_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (active) setActive(false);
            return;
        }

        while (OPEN_AUTOFARM_FILTER_KEY.consumeClick()) {
            mc.setScreen(new ForageLootScreen(mc.screen));
        }

        while (TOGGLE_AUTOFARM_KEY.consumeClick()) {
            if (Screen.hasShiftDown()) {
                mc.setScreen(new ForageLootScreen(mc.screen));
                continue;
            }
            setActive(!active);
            boolean isRu = isRussianLanguage(mc);
            String msg = active
                ? (isRu ? "§a[Автофарм] §eВКЛЮЧЕН §7(J - выкл, K - лут). Поиск блока..." : "§a[AutoFarm] §eENABLED §7(J to toggle, K for loot). Searching block...")
                : (isRu ? "§e[Автофарм] §cВЫКЛЮЧЕН." : "§e[AutoFarm] §cDISABLED.");
            mc.player.displayClientMessage(Component.literal(msg), true);
        }

        if (!active) return;

        if (switchCooldownTicks > 0) {
            switchCooldownTicks--;
        }

        if (!mc.player.isAlive()) return;

        // Check if player is pulled into a battle (e.g. from wild Forage encounter)
        boolean isBattling = ClientProxy.battleManager != null && ClientProxy.battleManager.isBattling();
        if (isBattling) {
            handleBattleState(mc);
            return;
        } else if (wasInBattle) {
            handleBattleEnded(mc);
            return;
        }

        // If player is inside a container screen (chest, crafting table, inventory, etc.) -> pause
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
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

        // Informational check only
        biomeWarning = isBiomeAridForMint(mc.level, mc.player.blockPosition());

        switch (state) {
            case FIND_BEST_BLOCK -> {
                targetBlockPos = findBestForageBlock(mc);
                if (targetBlockPos == null) {
                    statusText = "§cНет подходящего блока рядом!";
                    stateTimer = 20; // Recheck in 1 second
                    return;
                }
                BlockState bState = mc.level.getBlockState(targetBlockPos);
                targetBlockScore = getBlockScore(bState, targetBlockPos, mc.player.blockPosition(), mc.level);
                targetBlockName = bState.getBlock().getName().getString();
                statusText = "Выбран: " + targetBlockName + " (" + targetBlockScore + "★)";
                state = State.SELECT_POKEMON;
                stateTimer = 2;
            }

            case SELECT_POKEMON -> {
                // If any party pokemon is injured or fainted, try auto-healing
                tryAutoHeal(mc, false);

                // Verify block is still valid or find an even better one
                targetBlockPos = findBestForageBlock(mc);
                if (targetBlockPos == null) {
                    state = State.FIND_BEST_BLOCK;
                    return;
                }
                BlockState bState = mc.level.getBlockState(targetBlockPos);
                targetBlockScore = getBlockScore(bState, targetBlockPos, mc.player.blockPosition(), mc.level);
                targetBlockName = bState.getBlock().getName().getString();

                int partyCount = party.countAll();
                int currentSlot = party.getSelectedSlot();
                int readySlot = -1;
                int minCooldown = Integer.MAX_VALUE;

                // Check if any Pokémon from player is already in world
                PixelmonEntity anyInWorld = getAnyPartyPokemonInWorld(mc, party);

                // Check current slot first
                Pokemon currentPoke = party.getSelectedPokemon();
                if (currentPoke != null && !currentPoke.isEgg() && !currentPoke.isFainted() && !currentPoke.isMoveSkillCoolingDown(forage)) {
                    readySlot = currentSlot;
                } else {
                    // Search party for a ready, healthy slot
                    for (int i = 0; i < partyCount; i++) {
                        Pokemon p = party.get(i);
                        if (p == null || p.isEgg() || p.isFainted()) continue;
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

                if (readySlot == -1) {
                    boolean allFainted = true;
                    for (int i = 0; i < partyCount; i++) {
                        Pokemon p = party.get(i);
                        if (p != null && !p.isEgg() && !p.isFainted()) {
                            allFainted = false;
                            break;
                        }
                    }
                    if (allFainted) {
                        statusText = "§cВсе покемоны без сознания! Нужен /pokeheal";
                        tryAutoHeal(mc, true);
                        stateTimer = 30;
                        return;
                    }
                }

                if (readySlot != -1) {
                    if (readySlot != currentSlot) {
                        // If another pokemon is out in the world, recall it first!
                        if (anyInWorld != null) {
                            state = State.RECALL_POKEMON;
                            stateTimer = 2;
                            return;
                        }
                        selectPartySlot(party, readySlot);
                    }

                    Pokemon selected = party.getSelectedPokemon();
                    if (selected == null) {
                        stateTimer = 5;
                        return;
                    }

                    // Check if selected pokemon is ALREADY summoned in world
                    PixelmonEntity selectedEntity = getPlayerPokemonEntity(mc, selected);
                    if (selectedEntity != null && selectedEntity.isAlive()) {
                        statusText = selected.getDisplayName().getString() + " уже в мире. Готов!";
                        state = State.READY_TO_FORAGE;
                        stateTimer = 2;
                    } else if (anyInWorld != null) {
                        // Some other pokemon is out, recall it first
                        state = State.RECALL_POKEMON;
                        stateTimer = 2;
                    } else {
                        statusText = "Выбран: " + selected.getDisplayName().getString() + " -> " + targetBlockName;
                        state = State.SPAWN_POKEMON;
                        stateTimer = 2;
                    }
                } else {
                    int sec = (minCooldown + 19) / 20;
                    statusText = "Откат пачки: " + sec + "с (покемонов: " + party.countPokemon() + ")";
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

                // Check if already in world
                PixelmonEntity existing = getPlayerPokemonEntity(mc, p);
                if (existing != null && existing.isAlive()) {
                    state = State.READY_TO_FORAGE;
                    stateTimer = 2;
                    return;
                }

                // Check if pokeball is already flying
                if (isPokeballInFlight(mc, p)) {
                    statusText = "Покебол уже в воздухе, ждем...";
                    state = State.WAIT_FOR_SPAWN;
                    spawnWaitTicks = 0;
                    stateTimer = 5;
                    return;
                }

                // Anti-spam cooldown check (minimum 2.5 seconds between throws)
                long now = System.currentTimeMillis();
                if (now - lastThrowTime < 2500) {
                    statusText = "Ожидание перед броском...";
                    stateTimer = 5;
                    return;
                }

                // Throw Pokéball exactly ONCE
                SendPokemonKey.sendPokemon();
                lastThrowTime = now;
                spawnWaitTicks = 0;
                String pName = p.getDisplayName().getString();
                statusText = "Бросок покебола: " + pName + "...";
                state = State.WAIT_FOR_SPAWN;
                stateTimer = 5;
            }

            case WAIT_FOR_SPAWN -> {
                Pokemon p = party.getSelectedPokemon();
                if (p == null || p.isEgg()) {
                    state = State.SELECT_POKEMON;
                    return;
                }

                spawnWaitTicks++;

                // Check if entity is now present in world
                PixelmonEntity pe = getPlayerPokemonEntity(mc, p);
                if (pe != null && pe.isAlive()) {
                    statusText = pe.getDisplayName().getString() + " появился! Подготовка...";
                    state = State.READY_TO_FORAGE;
                    stateTimer = 8; // 0.4s for landing and animation settling
                    return;
                }

                boolean flying = isPokeballInFlight(mc, p);
                if (flying) {
                    statusText = "Покебол в полете... (" + String.format(Locale.ROOT, "%.1f", spawnWaitTicks / 20.0) + "с)";
                } else {
                    statusText = "Ожидание спавна: " + p.getDisplayName().getString() + "... (" + spawnWaitTicks + ")";
                }

                // Timeout after 100 ticks (5 seconds)
                if (spawnWaitTicks > 100) {
                    statusText = "§cТаймаут спавна! Перепроверка...";
                    state = State.SELECT_POKEMON;
                    stateTimer = 20; // Wait 1 second before retrying
                    return;
                }

                stateTimer = 1; // Recheck every tick
            }

            case READY_TO_FORAGE -> {
                Pokemon p = party.getSelectedPokemon();
                if (p == null || p.isEgg()) {
                    state = State.SELECT_POKEMON;
                    return;
                }

                PixelmonEntity pe = getPlayerPokemonEntity(mc, p);
                if (pe == null || !pe.isAlive()) {
                    statusText = "Покемон не обнаружен!";
                    state = State.SELECT_POKEMON;
                    stateTimer = 10;
                    return;
                }

                statusText = p.getDisplayName().getString() + " готов. Цель: " + targetBlockName;
                state = State.FORAGING;
                stateTimer = 2;
            }

            case FORAGING -> {
                Pokemon p = party.getSelectedPokemon();
                if (p == null || p.isEgg()) {
                    state = State.SELECT_POKEMON;
                    return;
                }

                PixelmonEntity pe = getPlayerPokemonEntity(mc, p);
                if (pe == null || !pe.isAlive()) {
                    // DO NOT call sendPokemon()!
                    statusText = "Покемон пропал перед добычей!";
                    state = State.SELECT_POKEMON;
                    stateTimer = 10;
                    return;
                }

                // Verify block is still valid
                if (targetBlockPos == null || !isValidForageBlock(mc.level, targetBlockPos)) {
                    targetBlockPos = findBestForageBlock(mc);
                    if (targetBlockPos == null) {
                        state = State.FIND_BEST_BLOCK;
                        return;
                    }
                }

                // Snapshot inventory before action
                takeInventorySnapshot(mc);

                // Send Forage packet to server targeting the BEST block
                UseMoveSkillPacket packet = new UseMoveSkillPacket(
                    party.getSelectedSlot(),
                    forage,
                    targetBlockPos,
                    Direction.UP
                );
                NetworkHelper.sendToServer(packet);

                statusText = p.getDisplayName().getString() + " копает [" + targetBlockName + "]...";
                state = State.WAIT_FOR_LOOT;
                // Wait 35 ticks (1.75s) for forage animation, item drop and player pickup
                stateTimer = 35;
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
                PixelmonEntity pe = getAnyPartyPokemonInWorld(mc, party);
                if (pe != null && pe.isAlive()) {
                    SendPokemonKey.sendPokemon();
                    statusText = "Отзываем " + pe.getDisplayName().getString() + "...";
                    state = State.WAIT_FOR_RECALL;
                    recallWaitTicks = 0;
                    stateTimer = 5;
                } else {
                    state = State.CYCLE_SLOT;
                    stateTimer = 2;
                }
            }

            case WAIT_FOR_RECALL -> {
                recallWaitTicks++;
                PixelmonEntity pe = getAnyPartyPokemonInWorld(mc, party);
                if (pe == null || !pe.isAlive()) {
                    statusText = "Покемон отозван.";
                    state = State.CYCLE_SLOT;
                    stateTimer = 4;
                    return;
                }

                if (recallWaitTicks > 40) { // 2 seconds timeout
                    state = State.CYCLE_SLOT;
                    stateTimer = 2;
                    return;
                }

                stateTimer = 1;
            }

            case CYCLE_SLOT -> {
                if (party.countAll() > 1) {
                    int nextSlot = (party.getSelectedSlot() + 1) % party.countAll();
                    selectPartySlot(party, nextSlot);
                    statusText = "Смена на слот " + (nextSlot + 1) + "...";
                }
                state = State.SELECT_POKEMON;
                stateTimer = 4;
            }

            case IDLE -> {
                // Do nothing
            }
        }
    }

    /**
     * Safely changes the selected party slot on the client.
     * Note: Calling party.setSelectedSlot(slot, true) in Pixelmon 9.4.1 crashes with NoSuchMethodError
     * because Pixelmon internally calls sendClientUpdateSelectedPacket() which is annotated with @OnlyIn(Dist.DEDICATED_SERVER)
     * and stripped on the client by NeoForge runtimedistcleaner.
     * Passing false sets the slot without invoking the server-only method, and we then notify the server via packet.
     */
    public static void selectPartySlot(PlayerPartyStorage party, int slot) {
        if (party == null) return;
        party.setSelectedSlot(slot, false);
        try {
            NetworkHelper.sendToServer(new ServerboundSetSelectedSlotPacket(slot));
        } catch (Throwable t) {
            try {
                party.sendClientUpdatePacket();
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Resolves the Minecraft entity for the given Pokemon in the client level.
     * Uses both SendoutListener and direct client entity scanning.
     */
    public static PixelmonEntity getPlayerPokemonEntity(Minecraft mc, Pokemon p) {
        if (mc.level == null || p == null) return null;
        UUID uuid = p.getUUID();

        // 1. Check SendoutListener map
        PixelmonEntity listenerEntity = SendoutListener.getEntityInWorld(uuid, mc.level);
        if (listenerEntity != null && listenerEntity.isAlive() && !listenerEntity.isRemoved()) {
            return listenerEntity;
        }

        // 2. Direct client level iteration
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PixelmonEntity pe && pe.isAlive() && !pe.isRemoved()) {
                if (uuid.equals(pe.getUUID())) {
                    return pe;
                }
                if (pe.getOwner() == mc.player && pe.getPokemon() != null && uuid.equals(pe.getPokemon().getUUID())) {
                    return pe;
                }
            }
        }
        return null;
    }

    /**
     * Finds any Pokémon from player's party that is currently summoned in the client level.
     */
    public static PixelmonEntity getAnyPartyPokemonInWorld(Minecraft mc, PlayerPartyStorage party) {
        if (mc.level == null || party == null) return null;
        for (int i = 0; i < party.countAll(); i++) {
            Pokemon p = party.get(i);
            if (p == null) continue;
            PixelmonEntity pe = getPlayerPokemonEntity(mc, p);
            if (pe != null && pe.isAlive() && !pe.isRemoved()) {
                return pe;
            }
        }
        return null;
    }

    /**
     * Checks if a Pokéball thrown by the player is currently in flight or animating in the world.
     */
    public static boolean isPokeballInFlight(Minecraft mc, Pokemon p) {
        if (mc.level == null || mc.player == null) return false;
        UUID pUuid = (p != null) ? p.getUUID() : null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isAlive() || entity.isRemoved()) continue;

            if (entity instanceof OccupiedPokeBallEntity ball) {
                if (ball.getThrower() == mc.player || mc.player.getUUID().equals(ball.getOwnerId())) {
                    if (pUuid == null || ball.getPokeUUID() == null || pUuid.equals(ball.getPokeUUID())) {
                        return true;
                    }
                }
            } else if (entity instanceof PokeBallEntity ball) {
                if (ball.getThrower() == mc.player || mc.player.getUUID().equals(ball.getOwnerId())) {
                    if (pUuid == null || ball.getPokeUUID() == null || pUuid.equals(ball.getPokeUUID())) {
                        return true;
                    }
                }
            }
        }
        return false;
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

        Set<String> keepSet = null;
        if (EffectivenessConfig.CONFIG != null) {
            String filterStr = EffectivenessConfig.CONFIG.autoFarmKeepItemFilter.get();
            keepSet = ForageLootRegistry.parseFilterString(filterStr);
        }

        for (int slotId = 9; slotId <= 44; slotId++) {
            ItemStack current = mc.player.inventoryMenu.getSlot(slotId).getItem();
            if (current.isEmpty()) continue;

            ItemStack before = inventorySnapshot.get(slotId);
            boolean isNew = (before == null || before.isEmpty());
            boolean countIncreased = (!isNew && ItemStack.isSameItemSameComponents(current, before) && current.getCount() > before.getCount());

            if (isNew || countIncreased) {
                int addedCount = isNew ? current.getCount() : (current.getCount() - before.getCount());

                boolean isAllowed = ForageLootRegistry.isItemAllowed(current, keepSet);

                if (isAllowed) {
                    if (isMintSeed(current)) {
                        seedsFound += addedCount;
                        sendChat(mc, "§a[Автофарм] §e+ Семена Мяты x" + addedCount + " §7(Всего семян: " + seedsFound + " шт.)");
                    } else if (isMintItem(current)) {
                        mintsFound += addedCount;
                        sendChat(mc, "§a[Автофарм] §d+ " + current.getHoverName().getString() + " x" + addedCount + " §7(Всего мяты: " + mintsFound + " шт.)");
                    } else {
                        otherKeptFound += addedCount;
                        sendChat(mc, "§a[Автофарм] §b+ " + current.getHoverName().getString() + " x" + addedCount + " §7(Сохранено в инвентаре)");
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

    /**
     * Prioritizes blocks for Mint Farming:
     * 100: Moss Block (Блок мха) - Absolute best! Never decays to dirt, clean, best for AFK platforms.
     * 90: Moss Carpet / Modded Moss
     * 70: Podzol, Mycelium - Permanent vegetative blocks (never decay to dirt).
     * 50: Grass Block (Обычный дёрн).
     * 30: Mud / Rooted Dirt.
     * 20: Dirt / Coarse Dirt.
     * 10: Any other soil/land block from tags.
     */
    public static boolean isFarmingMints() {
        if (EffectivenessConfig.CONFIG == null) return true;
        String filter = EffectivenessConfig.CONFIG.autoFarmKeepItemFilter.get().toLowerCase(Locale.ROOT);
        return filter.contains("mint");
    }

    public static int getBlockScore(BlockState state, BlockPos pos, BlockPos playerPos, ClientLevel level) {
        Block b = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return 20;
        String path = id.getPath().toLowerCase(Locale.ROOT);

        int score = 30;
        boolean mintFarming = isFarmingMints();

        if (mintFarming) {
            // Moss blocks (Highest priority for mint)
            if (b == Blocks.MOSS_BLOCK || path.equals("moss_block")) {
                score = 100;
            } else if (b == Blocks.MOSS_CARPET || path.contains("moss")) {
                score = 90;
            } else if (b == Blocks.PODZOL || b == Blocks.MYCELIUM) {
                score = 75;
            } else if (b == Blocks.GRASS_BLOCK || path.contains("grass_block")) {
                score = 60;
            } else if (b == Blocks.MUD || b == Blocks.ROOTED_DIRT || path.contains("mud") || path.contains("rooted")) {
                score = 45;
            } else if (b == Blocks.DIRT || b == Blocks.COARSE_DIRT || path.contains("dirt") || state.is(BlockTags.DIRT)) {
                score = 35;
            } else if (path.contains("sand")) {
                score = 30;
            } else {
                score = 25;
            }

            // Small bonus for sky visibility when farming mints
            if (level != null && pos != null && level.canSeeSky(pos.above())) {
                score += 5;
            }
        } else {
            // Non-mint farming (bones, stones, treasures, mc loot): all solid blocks are great!
            score = 50;
        }

        // Strong bonus if this block is right under player's feet (dx=0, dz=0, dy=-1)
        if (playerPos != null && pos != null) {
            if (pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ() && pos.getY() == playerPos.getY() - 1) {
                score += 20;
            }
        }

        return score;
    }

    public static BlockPos findBestForageBlock(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        BlockPos playerPos = mc.player.blockPosition();

        BlockPos bestPos = null;
        int bestScore = -1;
        double bestDistSq = Double.MAX_VALUE;

        // Check feet and immediate surrounding in radius 3
        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (!isValidForageBlock(mc.level, pos)) continue;

                    int score = getBlockScore(mc.level.getBlockState(pos), pos, playerPos, mc.level);
                    if (score < 0) continue;

                    double distSq = pos.distSqr(playerPos);

                    if (score > bestScore || (score == bestScore && distSq < bestDistSq)) {
                        bestScore = score;
                        bestDistSq = distSq;
                        bestPos = pos;
                    }
                }
            }
        }
        return bestPos;
    }

    public static boolean isValidForageBlock(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        // Block must be solid / support standing or foraging
        if (!state.isSolid() && !state.blocksMotion()) {
            return false;
        }

        // Fluid check (cannot forage on open water or lava)
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);

        // Block above must allow entity/drop to exist (air, grass, flower, carpet, etc.)
        return !aboveState.isSolid() || aboveState.canBeReplaced() || aboveState.is(Blocks.MOSS_CARPET);
    }

    public static boolean isBiomeAridForMint(ClientLevel level, BlockPos pos) {
        if (!isFarmingMints() || level == null || pos == null) return false;
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(ResourceKey::location).orElse(null);
        if (biomeId == null) return false;
        String path = biomeId.getPath().toLowerCase(Locale.ROOT);
        return path.contains("desert") || path.contains("badlands") || path.contains("mesa")
            || path.contains("ocean") || path.contains("nether") || path.contains("the_end");
    }

    private static void handleBattleState(Minecraft mc) {
        if (!wasInBattle) {
            wasInBattle = true;
            battleTicks = 0;
            fleeAttempts = 0;
            sendChat(mc, "§e[Автофарм] §cВнимание: началась битва с диким покемоном!");
        }

        battleTicks++;
        // Execute action every 15 ticks (0.75s) to allow animations and avoid packet flooding
        if (battleTicks % 15 != 0) {
            return;
        }

        String mode = (EffectivenessConfig.CONFIG != null)
            ? EffectivenessConfig.CONFIG.autoFarmBattleMode.get().toUpperCase(Locale.ROOT)
            : "FLEE_OR_ATTACK";

        switch (mode) {
            case "COMMAND" -> {
                String cmd = EffectivenessConfig.CONFIG.autoFarmEndBattleCommand.get();
                statusText = "§eЗавершение битвы (" + cmd + ")...";
                executeCommand(mc, cmd);
            }
            case "FLEE" -> {
                statusText = "§eПобег из битвы (Flee)...";
                fleeAttempts++;
                attemptFlee(mc);
            }
            case "FLEE_OR_ATTACK" -> {
                fleeAttempts++;
                if (fleeAttempts <= 2) {
                    statusText = "§eПопытка побега (" + fleeAttempts + "/2)...";
                    attemptFlee(mc);
                } else {
                    statusText = "§cПобег не удался, автоатака...";
                    attackInBattle(mc);
                }
            }
            case "ATTACK" -> {
                statusText = "§cАвтобитва (выбор атаки)...";
                attackInBattle(mc);
            }
            default -> {
                statusText = "§eПобег из битвы...";
                attemptFlee(mc);
            }
        }
    }

    private static void attemptFlee(Minecraft mc) {
        if (mc.screen instanceof BattleScreen bs) {
            try {
                bs.chooseToFleeOrForfeitUsingKeyPress();
            } catch (Throwable ignored) {}
        } else {
            try {
                PixelmonClientData poke = (ClientProxy.battleManager != null) ? ClientProxy.battleManager.getCurrentPokemon() : null;
                UUID uuid = (poke != null) ? poke.pokemonUUID : null;
                NetworkHelper.sendToServer(new FleePacket(uuid));
            } catch (Throwable ignored) {}
        }
    }

    private static void attackInBattle(Minecraft mc) {
        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm == null || !bm.isBattling()) return;

        if (switchCooldownTicks > 0) {
            return;
        }

        PixelmonClientData enemy = PixelmonEffectivenessHelper.getActiveEnemy(bm);
        List<Holder<Type>> enemyTypes = (enemy != null) ? enemy.getTypes() : null;
        boolean inverse = bm.getOrDefault(ContextKeys.INVERSE_BATTLE, false);

        PixelmonClientData activeOur = bm.getCurrentPokemon();
        PlayerPartyStorage party = ClientStorageManager.party();

        // 1. Check moveset on active Pokémon
        Moveset moveset = (activeOur != null) ? activeOur.moveset : null;
        if ((moveset == null || moveset.isEmpty()) && activeOur != null && party != null) {
            Pokemon p = party.get(activeOur.pokemonUUID);
            if (p != null) {
                moveset = p.getMoveset();
            }
        }

        int bestMoveIndex = -1;
        double bestMoveScore = -1.0;
        Attack bestAttack = null;

        if (moveset != null) {
            for (int i = 0; i < moveset.size(); i++) {
                Attack attack = moveset.get(i);
                if (attack == null || attack.getMove() == null) continue;
                if (attack.getPP() <= 0) continue; // Out of PP

                AttackCategory cat = attack.getAttackCategory();
                if (cat == AttackCategory.STATUS) {
                    // Status move has no direct damage: low priority fallback (0.1 score)
                    if (bestMoveScore < 0.1) {
                        bestMoveScore = 0.1;
                        bestMoveIndex = i;
                        bestAttack = attack;
                    }
                    continue;
                }

                // Damaging move (PHYSICAL / SPECIAL)
                Type attackType = attack.getType().value();
                double mult = (enemyTypes != null && !enemyTypes.isEmpty())
                    ? PixelmonEffectivenessHelper.calculateEffectiveness(attack, attackType, enemyTypes, inverse)
                    : 1.0;

                if (mult == 0.0) {
                    // IMMUNE! (e.g. Laser on Dark / Normal on Ghost / Electric on Ground)
                    // Does 0 damage! Do not pick!
                    continue;
                }

                int basePower = attack.getMove().getBasePower();
                if (basePower <= 0) basePower = 60;

                double score = basePower * mult;

                // STAB bonus (x1.5 if move matches user's type)
                if (activeOur != null && activeOur.getTypes() != null) {
                    for (Holder<Type> ourType : activeOur.getTypes()) {
                        if (ourType != null && ourType.is(attack.getType().unwrapKey().orElse(null))) {
                            score *= 1.5;
                            break;
                        }
                    }
                }

                if (score > bestMoveScore) {
                    bestMoveScore = score;
                    bestMoveIndex = i;
                    bestAttack = attack;
                }
            }
        }

        boolean hasDamagingMove = (bestMoveIndex != -1 && bestMoveScore >= 1.0);

        // 2. If active Pokémon has NO attacking moves (or all have 0 PP / enemy is immune):
        // Switch to the most effective Pokémon in party!
        if (!hasDamagingMove) {
            Pokemon bestSwitch = null;
            double bestSwitchScore = 0.0;
            Attack bestSwitchAttack = null;

            UUID currentUUID = (activeOur != null) ? activeOur.pokemonUUID : null;

            if (party != null && bm.canSwitch) {
                for (int s = 0; s < party.countAll(); s++) {
                    Pokemon candidate = party.get(s);
                    if (candidate == null || candidate.isEgg() || candidate.isFainted() || candidate.getHealth() <= 0) {
                        continue;
                    }
                    if (currentUUID != null && currentUUID.equals(candidate.getUUID())) {
                        continue; // Skip currently active pokemon
                    }

                    Moveset cMoves = candidate.getMoveset();
                    if (cMoves == null) continue;

                    for (int mi = 0; mi < cMoves.size(); mi++) {
                        Attack cAtk = cMoves.get(mi);
                        if (cAtk == null || cAtk.getMove() == null) continue;
                        if (cAtk.getPP() <= 0) continue;
                        if (cAtk.getAttackCategory() == AttackCategory.STATUS) continue;

                        Type cType = cAtk.getType().value();
                        double cMult = (enemyTypes != null && !enemyTypes.isEmpty())
                            ? PixelmonEffectivenessHelper.calculateEffectiveness(cAtk, cType, enemyTypes, inverse)
                            : 1.0;

                        if (cMult == 0.0) continue; // Immune

                        int cPow = cAtk.getMove().getBasePower();
                        if (cPow <= 0) cPow = 60;

                        double cScore = cPow * cMult;
                        if (candidate.getForm() != null && candidate.getForm().getTypes() != null) {
                            for (Holder<Type> t : candidate.getForm().getTypes()) {
                                if (t != null && t.value().equals(cType)) {
                                    cScore *= 1.5;
                                    break;
                                }
                            }
                        }

                        if (cScore > bestSwitchScore) {
                            bestSwitchScore = cScore;
                            bestSwitch = candidate;
                            bestSwitchAttack = cAtk;
                        }
                    }
                }
            }

            if (bestSwitch != null && currentUUID != null) {
                String atkName = getAttackDisplayName(bestSwitchAttack);
                sendChat(mc, "§a[Автофарм] §eСмена покемона на §b" + bestSwitch.getDisplayName().getString()
                    + " §7(лучшая атака: §f" + atkName + " §a" + String.format(Locale.ROOT, "%.1f★", bestSwitchScore) + "§7)");
                statusText = "Смена на " + bestSwitch.getDisplayName().getString();
                switchPokemonInBattle(bm, bestSwitch.getUUID(), currentUUID, false);
                switchCooldownTicks = 30; // Wait 1.5s for switch to complete
                return;
            }
        }

        // 3. Execute the best move found
        if (bestMoveIndex != -1) {
            if (bestAttack != null) {
                String atkName = getAttackDisplayName(bestAttack);
                sendChat(mc, "§a[Автофарм] §cАтака: §e" + atkName + " §7(эффективность: §a" + String.format(Locale.ROOT, "%.1f★", bestMoveScore) + "§7)");
                statusText = "Атака: " + atkName;
            }

            if (mc.screen instanceof BattleScreen bs) {
                try {
                    bs.selectScreenImmediate(BattleMode.CHOOSE_ATTACK);
                } catch (Throwable ignored) {}

                boolean chosen = bs.chooseAttackUsingKeyPress(bestMoveIndex);
                if (!chosen) {
                    try {
                        bm.selectedAttack = bestMoveIndex;
                        bm.afkSelectMove();
                    } catch (Throwable ignored) {}
                }
            } else {
                try {
                    bm.selectedAttack = bestMoveIndex;
                    bm.afkSelectMove();
                } catch (Throwable ignored) {}
            }
        } else {
            // Absolute fallback: afk select move or struggle
            try {
                bm.afkSelectMove();
            } catch (Throwable ignored) {}
        }
    }

    public static void switchPokemonInBattle(ClientBattleManager bm, UUID newPokemonUUID, UUID currentPokemonUUID, boolean instant) {
        if (bm == null || newPokemonUUID == null) return;
        try {
            BattleActionPacket packet = new BattleActionPacket(
                bm.battleControllerIndex,
                new SwitchBattleAction(newPokemonUUID, currentPokemonUUID, instant)
            );
            if (bm.selectedActions == null) {
                bm.selectedActions = new java.util.ArrayList<>();
            }
            bm.selectedActions.add(packet);
            PixelmonClientData newPig = bm.getPokemon(newPokemonUUID);
            if (newPig != null) {
                newPig.isSwitchingIn = true;
            }
            bm.selectedMove();
        } catch (Throwable t) {
            try {
                NetworkHelper.sendToServer(new BattleActionPacket(
                    bm.battleControllerIndex,
                    new SwitchBattleAction(newPokemonUUID, currentPokemonUUID, instant)
                ));
            } catch (Throwable ignored) {}
        }
    }

    public static String getAttackDisplayName(Attack attack) {
        if (attack == null || attack.getMove() == null) return "Атака";
        try {
            String key = attack.getMove().getTranslationKey();
            if (key != null && !key.isEmpty()) {
                return Component.translatable(key).getString();
            }
        } catch (Throwable ignored) {}
        return attack.getMove().getAttackName();
    }

    private static void handleBattleEnded(Minecraft mc) {
        wasInBattle = false;
        battleTicks = 0;
        fleeAttempts = 0;
        switchCooldownTicks = 0;
        statusText = "Битва окончена. Продолжение автофарма...";
        sendChat(mc, "§a[Автофарм] §eДикая битва завершена!");

        if (mc.screen instanceof BattleScreen) {
            try {
                BattleScreen.restoreSettingsAndCloseStatic(ClientProxy.battleManager);
            } catch (Throwable ignored) {}
            mc.setScreen(null);
        }

        // Auto-heal party if enabled
        if (EffectivenessConfig.CONFIG != null && EffectivenessConfig.CONFIG.autoFarmAutoHeal.get()) {
            tryAutoHeal(mc, true);
        }

        state = State.FIND_BEST_BLOCK;
        stateTimer = 15;
    }

    public static void tryAutoHeal(Minecraft mc, boolean force) {
        if (mc.player == null || mc.player.connection == null) return;
        if (EffectivenessConfig.CONFIG == null || !EffectivenessConfig.CONFIG.autoFarmAutoHeal.get()) return;

        long now = System.currentTimeMillis();
        // 5 seconds cooldown between /pokeheal to prevent server flood/spam
        if (now - lastHealTime < 5000) {
            return;
        }

        PlayerPartyStorage party = ClientStorageManager.party();
        if (party == null) return;

        boolean needsHeal = force;
        int threshold = EffectivenessConfig.CONFIG.autoFarmHealThreshold.get();

        if (!needsHeal) {
            for (int i = 0; i < party.countAll(); i++) {
                Pokemon p = party.get(i);
                if (p != null) {
                    if (p.isFainted() || p.getHealthPercentage() <= threshold) {
                        needsHeal = true;
                        break;
                    }
                }
            }
        }

        if (needsHeal) {
            String cmd = EffectivenessConfig.CONFIG.autoFarmHealCommand.get();
            executeCommand(mc, cmd);
            lastHealTime = now;
            sendChat(mc, "§a[Автофарм] §bКоманда лечения: §f" + cmd);
        }
    }

    public static void executeCommand(Minecraft mc, String command) {
        if (mc.player == null || mc.player.connection == null || command == null || command.trim().isEmpty()) {
            return;
        }
        String clean = command.trim();
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        try {
            mc.player.connection.sendCommand(clean);
        } catch (Throwable t) {
            try {
                mc.player.connection.sendChat("/" + clean);
            } catch (Throwable ignored) {}
        }
    }

    private static void sendChat(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!active || targetBlockPos == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        double x = targetBlockPos.getX() + 0.5 - camPos.x;
        double y = targetBlockPos.getY() + 1.25 - camPos.y;
        double z = targetBlockPos.getZ() + 0.5 - camPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(0.025f, -0.025f, 0.025f);

        String tag = (targetBlockScore >= 50)
            ? "§a☘ [" + targetBlockName + "] ★" + targetBlockScore
            : "§e☘ [" + targetBlockName + "] ★" + targetBlockScore;

        int width = font.width(tag);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(
            tag,
            -width / 2.0f,
            0,
            0xFFFFFFFF,
            false,
            matrix,
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0x88000000,
            0xF000F0
        );

        bufferSource.endBatch();
        poseStack.popPose();
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
        int panelW = 230;
        int panelH = biomeWarning ? 100 : 90;

        // Semi-transparent background with emerald/gold border
        int borderColor = (targetBlockScore >= 50) ? 0xFF2ECC71 : 0xFFF1C40F;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA111A1E);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, borderColor);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, borderColor);

        String title = isRu ? "§a☘ Автофарм Forage §7(J - стоп, K - лут)" : "§a☘ Forage Auto-Farm §7(J stop, K loot)";
        graphics.drawString(font, title, panelX + 6, panelY + 5, 0xFFFFFFFF, true);

        String targetStr = String.format(Locale.ROOT, "§7%s: %s §f%s §7(%d★)",
            (isRu ? "Цель" : "Target"),
            (targetBlockScore >= 50 ? "§a" : "§e"),
            targetBlockName,
            targetBlockScore
        );
        graphics.drawString(font, targetStr, panelX + 6, panelY + 17, 0xFFFFFFFF, true);

        String st = "§7" + (isRu ? "Статус: " : "Status: ") + "§f" + statusText;
        if (font.width(st) > panelW - 12) {
            st = font.plainSubstrByWidth(st, panelW - 16) + "...";
        }
        graphics.drawString(font, st, panelX + 6, panelY + 29, 0xFFDDDDDD, true);

        String stats1 = String.format(Locale.ROOT, "§e%s: §f%d §7| §d%s: §f%d §7| §b%s: §f%d",
            (isRu ? "Сем" : "Seed"), seedsFound,
            (isRu ? "Мята" : "Mint"), mintsFound,
            (isRu ? "Лут" : "Loot"), otherKeptFound
        );
        graphics.drawString(font, stats1, panelX + 6, panelY + 41, 0xFFFFFFFF, true);

        String stats2 = String.format(Locale.ROOT, "§c%s: §f%d шт.",
            (isRu ? "Сброшено мусора" : "Junk dropped"), junkDropped
        );
        graphics.drawString(font, stats2, panelX + 6, panelY + 53, 0xFFAAAAAA, true);

        // Battle & Heal status line
        String bMode = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.autoFarmBattleMode.get() : "FLEE_OR_ATTACK";
        boolean healOn = (EffectivenessConfig.CONFIG == null) || EffectivenessConfig.CONFIG.autoFarmAutoHeal.get();
        String bModeLabel = switch (bMode.toUpperCase(Locale.ROOT)) {
            case "FLEE" -> isRu ? "Побег" : "Flee";
            case "ATTACK" -> isRu ? "Атака" : "Attack";
            case "COMMAND" -> "/endbattle";
            default -> isRu ? "Побег/Атака" : "Flee/Atk";
        };
        String battleLine = String.format(Locale.ROOT, "§7%s: §e%s §7| §7Heal: %s",
            (isRu ? "Битва" : "Battle"), bModeLabel,
            (healOn ? "§aВКЛ" : "§7ВЫКЛ")
        );
        graphics.drawString(font, battleLine, panelX + 6, panelY + 65, 0xFFFFFFFF, true);

        String hint = isRu ? "§8[K / Shift+J] — Настройка лута и битв" : "§8[K / Shift+J] — Configure loot & battle";
        graphics.drawString(font, hint, panelX + 6, panelY + 77, 0xFF888888, true);

        if (biomeWarning) {
            String bioWarn = isRu ? "§7(Инфо: в пустыне семена мяты редки)" : "§7(Mint seeds are rare in desert)";
            graphics.drawString(font, bioWarn, panelX + 6, panelY + 89, 0xFFAAAAAA, true);
        }
    }

    private static boolean isRussianLanguage(Minecraft mc) {
        return mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
