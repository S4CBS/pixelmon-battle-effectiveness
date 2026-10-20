package com.pixelmoneffectiveness.client.autofarm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pixelmonmod.pixelmon.api.moveskills.MoveSkill;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.client.keybindings.SendPokemonKey;
import com.pixelmonmod.pixelmon.client.listener.SendoutListener;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import com.pixelmonmod.pixelmon.comm.packetHandlers.moveskills.UseMoveSkillPacket;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.entities.pokeballs.OccupiedPokeBallEntity;
import com.pixelmonmod.pixelmon.entities.pokeballs.PokeBallEntity;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MintAutoFarmClient {

    public static final KeyMapping TOGGLE_AUTOFARM_KEY = new KeyMapping(
        "key.pixelmoneffectiveness.toggle_mint_farm",
        74, // GLFW_KEY_J
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
    private static int junkDropped = 0;
    private static String statusText = "Готов к запуску";
    private static boolean biomeWarning = false;

    private static long lastThrowTime = 0;
    private static int spawnWaitTicks = 0;
    private static int recallWaitTicks = 0;

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
        } else {
            state = State.FIND_BEST_BLOCK;
            stateTimer = 0;
            statusText = "Поиск наилучшего блока...";
            lastThrowTime = 0;
            spawnWaitTicks = 0;
            recallWaitTicks = 0;
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
                ? (isRu ? "§a[Автофарм] §eВКЛЮЧЕН §7(J - выкл). Поиск наилучшего блока (Мох/Дёрн)..." : "§a[AutoFarm] §eENABLED §7(J to toggle). Searching for best block (Moss/Grass)...")
                : (isRu ? "§e[Автофарм] §cВЫКЛЮЧЕН." : "§e[AutoFarm] §cDISABLED.");
            mc.player.displayClientMessage(Component.literal(msg), true);
        }

        if (!active) return;

        // If player is dead, inside a container screen (chest, etc.), or in battle -> pause
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

        // Biome validation according to Pixelmon forage_loot anticondition
        biomeWarning = !isBiomeSuitableForMint(mc.level, mc.player.blockPosition());

        switch (state) {
            case FIND_BEST_BLOCK -> {
                targetBlockPos = findBestForageBlock(mc);
                if (targetBlockPos == null) {
                    statusText = "§cНет подходящего блока под открытым небом!";
                    stateTimer = 20; // Recheck in 1 second
                    return;
                }
                BlockState bState = mc.level.getBlockState(targetBlockPos);
                targetBlockScore = getBlockScore(bState);
                targetBlockName = bState.getBlock().getName().getString();
                statusText = "Выбран: " + targetBlockName + " (" + targetBlockScore + "★)";
                state = State.SELECT_POKEMON;
                stateTimer = 2;
            }

            case SELECT_POKEMON -> {
                // Verify block is still valid or find an even better one
                targetBlockPos = findBestForageBlock(mc);
                if (targetBlockPos == null) {
                    state = State.FIND_BEST_BLOCK;
                    return;
                }
                BlockState bState = mc.level.getBlockState(targetBlockPos);
                targetBlockScore = getBlockScore(bState);
                targetBlockName = bState.getBlock().getName().getString();

                int partyCount = party.countAll();
                int currentSlot = party.getSelectedSlot();
                int readySlot = -1;
                int minCooldown = Integer.MAX_VALUE;

                // Check if any Pokémon from player is already in world
                PixelmonEntity anyInWorld = getAnyPartyPokemonInWorld(mc, party);

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
                        // If another pokemon is out in the world, recall it first!
                        if (anyInWorld != null) {
                            state = State.RECALL_POKEMON;
                            stateTimer = 2;
                            return;
                        }
                        party.setSelectedSlot(readySlot, true);
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
                    party.setSelectedSlot(nextSlot, true);
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
                        sendChat(mc, "§a[Автофарм] §e+ Семена Мяты x" + addedCount + " §7(Всего семян: " + seedsFound + " шт.)");
                    } else {
                        mintsFound += addedCount;
                        sendChat(mc, "§a[Автофарм] §d+ " + current.getHoverName().getString() + " x" + addedCount + " §7(Всего мяты: " + mintsFound + " шт.)");
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
    public static int getBlockScore(BlockState state) {
        Block b = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return -1;
        String path = id.getPath().toLowerCase(Locale.ROOT);

        // 1. Moss blocks (Highest priority)
        if (b == Blocks.MOSS_BLOCK || path.equals("moss_block")) {
            return 100;
        }
        if (b == Blocks.MOSS_CARPET || path.contains("moss")) {
            return 90;
        }

        // 2. Podzol / Mycelium (Never turn into plain dirt)
        if (b == Blocks.PODZOL || b == Blocks.MYCELIUM) {
            return 70;
        }

        // 3. Grass blocks
        if (b == Blocks.GRASS_BLOCK || path.contains("grass_block")) {
            return 50;
        }

        // 4. Mud / Rooted Dirt
        if (b == Blocks.MUD || b == Blocks.ROOTED_DIRT || path.contains("mud") || path.contains("rooted")) {
            return 30;
        }

        // 5. Dirt / Coarse Dirt
        if (b == Blocks.DIRT || b == Blocks.COARSE_DIRT || path.contains("dirt")) {
            return 20;
        }

        // 6. Generic soil / land
        if (state.is(BlockTags.DIRT)) {
            return 15;
        }

        return -1;
    }

    /**
     * Scans for the best possible block in a 7x7x4 area around the player.
     * A higher priority block (e.g. Moss Block = 100) ALWAYS beats a lower priority block
     * (e.g. Grass Block = 50), even if the moss block is a couple blocks away!
     */
    public static BlockPos findBestForageBlock(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        BlockPos playerPos = mc.player.blockPosition();

        BlockPos bestPos = null;
        int bestScore = -1;
        double bestDistSq = Double.MAX_VALUE;

        // Check feet and immediate surrounding in radius 3
        for (int dy = 1; dy >= -2; dy--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (!isValidForageBlock(mc.level, pos)) continue;

                    int score = getBlockScore(mc.level.getBlockState(pos));
                    if (score < 0) continue;

                    double distSq = pos.distSqr(playerPos);
                    // Slight preference to feet block if scores are identical
                    if (dx == 0 && dz == 0 && (dy == -1 || dy == 0)) {
                        distSq -= 2.0;
                    }

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
        BlockState state = level.getBlockState(pos);
        if (getBlockScore(state) < 0) {
            return false;
        }

        BlockPos above = pos.above();
        // Crucial requirement for Mint Seeds from Pixelmon forage_loot: must see sky!
        if (!level.canSeeSky(above)) {
            return false;
        }

        // Block above must allow entity/drop to exist (air, grass, flower, carpet, etc.)
        BlockState aboveState = level.getBlockState(above);
        return !aboveState.isSolid() || aboveState.canBeReplaced() || aboveState.is(Blocks.MOSS_CARPET);
    }

    public static boolean isBiomeSuitableForMint(ClientLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(ResourceKey::location).orElse(null);
        if (biomeId == null) return true;
        String path = biomeId.getPath().toLowerCase(Locale.ROOT);

        // Pixelmon forage_loot anticonditions: arid, oceanic, end, hellish
        if (path.contains("desert") || path.contains("badlands") || path.contains("mesa")
            || path.contains("ocean") || path.contains("beach")
            || path.contains("nether") || path.contains("crimson") || path.contains("warped") || path.contains("soul") || path.contains("basalt")
            || path.contains("the_end") || path.contains("end_")) {
            return false;
        }
        return true;
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

        String tag = (targetBlockScore >= 90)
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
        int panelW = 215;
        int panelH = biomeWarning ? 78 : 68;

        // Semi-transparent background with emerald/gold border
        int borderColor = (targetBlockScore >= 90) ? 0xFF2ECC71 : 0xFFF1C40F;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA111A1E);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, borderColor);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, borderColor);

        String title = isRu ? "§a☘ Автофарм Мяты §7(J - стоп)" : "§a☘ Mint Auto-Farm §7(J to stop)";
        graphics.drawString(font, title, panelX + 6, panelY + 5, 0xFFFFFFFF, true);

        String targetStr = String.format(Locale.ROOT, "§7%s: %s §f%s §7(%d★)",
            (isRu ? "Цель" : "Target"),
            (targetBlockScore >= 90 ? "§a" : "§e"),
            targetBlockName,
            targetBlockScore
        );
        graphics.drawString(font, targetStr, panelX + 6, panelY + 17, 0xFFFFFFFF, true);

        String st = "§7" + (isRu ? "Статус: " : "Status: ") + "§f" + statusText;
        if (font.width(st) > panelW - 12) {
            st = font.plainSubstrByWidth(st, panelW - 16) + "...";
        }
        graphics.drawString(font, st, panelX + 6, panelY + 29, 0xFFDDDDDD, true);

        String stats1 = String.format(Locale.ROOT, "§e%s: §f%d §7| §d%s: §f%d",
            (isRu ? "Семена" : "Seeds"), seedsFound,
            (isRu ? "Мята" : "Mints"), mintsFound
        );
        graphics.drawString(font, stats1, panelX + 6, panelY + 41, 0xFFFFFFFF, true);

        String stats2 = String.format(Locale.ROOT, "§c%s: §f%d шт.",
            (isRu ? "Сброшено мусора" : "Junk dropped"), junkDropped
        );
        graphics.drawString(font, stats2, panelX + 6, panelY + 53, 0xFFAAAAAA, true);

        if (biomeWarning) {
            String bioWarn = isRu ? "§c⚠ Биом запрещен для семян мяты!" : "§c⚠ Biome blocked for mint seeds!";
            graphics.drawString(font, bioWarn, panelX + 6, panelY + 65, 0xFFFF5555, true);
        }
    }

    private static boolean isRussianLanguage(Minecraft mc) {
        return mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
