package com.pixelmoneffectiveness.client.overlay;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.pokemon.species.evs.EVYields;
import com.pixelmonmod.pixelmon.api.pokemon.species.gender.Gender;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.EVStore;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

public class PokemonOverlayClient {

    public static final KeyMapping OPEN_OVERLAY_SETTINGS = new KeyMapping(
        "key.pixelmoneffectiveness.open_overlay_settings",
        73, // GLFW_KEY_I
        "key.categories.pixelmoneffectiveness"
    );

    private static PixelmonEntity lastTargetedPokemon = null;
    private static long lastTargetedTime = 0;

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(PokemonOverlayClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(PokemonOverlayClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(PokemonOverlayClient::onClientTick);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_OVERLAY_SETTINGS);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        while (OPEN_OVERLAY_SETTINGS.consumeClick()) {
            mc.setScreen(new PokemonOverlaySettingsScreen());
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (EffectivenessConfig.CONFIG != null && !EffectivenessConfig.CONFIG.overlayEnabled.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof PokemonOverlaySettingsScreen)) {
            return;
        }

        double reachDist = (EffectivenessConfig.CONFIG != null)
            ? EffectivenessConfig.CONFIG.overlayReachDistance.get()
            : 24.0;

        PixelmonEntity target = getTargetedPokemon(mc, reachDist);
        if (target == null || !target.isAlive()) {
            return;
        }

        boolean isSneaking = mc.player.isCrouching() || Screen.hasShiftDown();
        boolean isRu = isRussian(mc);

        Pokemon pokemon = resolvePokemon(mc, target);
        if (pokemon == null) {
            return;
        }

        List<Component> lines = buildOverlayLines(mc, target, pokemon, isSneaking, isRu);
        if (lines.isEmpty()) {
            return;
        }

        String anchor = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayAnchor.get() : "TOP_CENTER";
        int offX = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayOffsetX.get() : 0;
        int offY = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayOffsetY.get() : 10;
        float scale = (EffectivenessConfig.CONFIG != null) ? (float) EffectivenessConfig.CONFIG.overlayScale.get().doubleValue() : 1.0F;
        int alpha = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayBackgroundAlpha.get() : 200;
        int bgColor = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayBackgroundColor.get() : 0x141726;
        int borderColor = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayBorderColor.get() : 0x303650;
        int textColor = (EffectivenessConfig.CONFIG != null) ? EffectivenessConfig.CONFIG.overlayTextColor.get() : 0xFFFFFF;

        renderOverlayBox(
            event.getGuiGraphics(),
            mc.font,
            lines,
            anchor,
            offX,
            offY,
            scale,
            alpha,
            bgColor,
            borderColor,
            textColor,
            event.getGuiGraphics().guiWidth(),
            event.getGuiGraphics().guiHeight()
        );
    }

    public static PixelmonEntity getTargetedPokemon(Minecraft mc, double reachDistance) {
        if (mc.player == null || mc.level == null) return null;

        // 1. Direct crosshair pick entity
        if (mc.crosshairPickEntity instanceof PixelmonEntity pe && pe.isAlive()) {
            lastTargetedPokemon = pe;
            lastTargetedTime = System.currentTimeMillis();
            return pe;
        }

        // 2. Raycast from camera/player eye position
        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 viewVec = mc.player.getViewVector(1.0F);
        Vec3 reachVec = eyePos.add(viewVec.scale(reachDistance));

        AABB searchBox = mc.player.getBoundingBox().expandTowards(viewVec.scale(reachDistance)).inflate(2.0D);
        List<PixelmonEntity> candidates = mc.level.getEntitiesOfClass(PixelmonEntity.class, searchBox, Entity::isAlive);

        PixelmonEntity closest = null;
        double closestDist = reachDistance * reachDistance;

        for (PixelmonEntity pe : candidates) {
            AABB box = pe.getBoundingBox().inflate(0.35D);
            Optional<Vec3> hit = box.clip(eyePos, reachVec);
            if (hit.isPresent()) {
                double distSq = eyePos.distanceToSqr(hit.get());
                if (distSq < closestDist) {
                    closestDist = distSq;
                    closest = pe;
                }
            }
        }

        if (closest != null) {
            lastTargetedPokemon = closest;
            lastTargetedTime = System.currentTimeMillis();
            return closest;
        }

        // Keep target briefly (300ms) to prevent jitter when turning or moving
        if (lastTargetedPokemon != null && lastTargetedPokemon.isAlive() && (System.currentTimeMillis() - lastTargetedTime < 300)) {
            return lastTargetedPokemon;
        }

        lastTargetedPokemon = null;
        return null;
    }

    private static Pokemon resolvePokemon(Minecraft mc, PixelmonEntity clientEntity) {
        // In singleplayer, query server entity for 100% full server-side data (IVs, EVs, Nature, Ability)
        try {
            if (mc.getSingleplayerServer() != null && mc.level != null) {
                ServerLevel sLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
                if (sLevel != null) {
                    Entity sEnt = sLevel.getEntity(clientEntity.getId());
                    if (sEnt instanceof PixelmonEntity sPe && sPe.getPokemon() != null) {
                        return sPe.getPokemon();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Fallback to client entity Pokemon
        return clientEntity.getPokemon();
    }

    public static List<Component> buildOverlayLines(Minecraft mc, PixelmonEntity entity, Pokemon pokemon, boolean isSneaking, boolean isRu) {
        List<Component> lines = new ArrayList<>();
        if (pokemon == null) return lines;

        // 1. Line 1: [Caught Checkmark] [Name] [Gender] [Level]
        boolean isCaught = false;
        try {
            if (ClientStorageManager.pokedex() != null) {
                isCaught = ClientStorageManager.pokedex().getCaughtCount(pokemon.toBase()) > 0;
            }
        } catch (Throwable ignored) {}

        MutableComponent line1 = Component.empty();
        if (isCaught) {
            line1.append(Component.literal("✔ ").withStyle(s -> s.withColor(0x22C55E))); // Green checkmark
        } else {
            line1.append(Component.literal("○ ").withStyle(s -> s.withColor(0x64748B))); // Uncaught indicator
        }

        if (pokemon.isShiny()) {
            line1.append(Component.literal("★ ").withStyle(s -> s.withColor(0xFBBF24)));
        }

        String displayName = pokemon.getDisplayName().getString();
        line1.append(Component.literal(displayName + " ").withStyle(s -> s.withColor(0xFFFFFF).withBold(true)));

        Gender gender = pokemon.getGender();
        if (gender == Gender.MALE) {
            line1.append(Component.literal("♂ ").withStyle(s -> s.withColor(0x3B82F6)));
        } else if (gender == Gender.FEMALE) {
            line1.append(Component.literal("♀ ").withStyle(s -> s.withColor(0xEC4899)));
        }

        String lvlStr = (isRu ? "Ур." : "Lvl.") + pokemon.getPokemonLevel();
        line1.append(Component.literal(lvlStr).withStyle(s -> s.withColor(0xFFFFFF)));
        lines.add(line1);

        // 2. Line 2: Hearts Health Bar [❤❤❤❤❤❤❤❤❤❤]
        int hp = pokemon.getHealth();
        int maxHp = pokemon.getMaxHealth();
        float ratio = (maxHp > 0) ? Math.max(0.0F, Math.min(1.0F, (float) hp / maxHp)) : 1.0F;

        MutableComponent heartsComp = Component.empty();
        for (int i = 0; i < 10; i++) {
            if (ratio >= (i + 1) * 0.1F) {
                heartsComp.append(Component.literal("❤").withStyle(s -> s.withColor(0xEF4444)));
            } else if (ratio >= (i + 0.5F) * 0.1F) {
                heartsComp.append(Component.literal("❤").withStyle(s -> s.withColor(0xB91C1C)));
            } else {
                heartsComp.append(Component.literal("❤").withStyle(s -> s.withColor(0x374151)));
            }
        }
        if (isSneaking) {
            heartsComp.append(Component.literal(String.format(Locale.ROOT, " (%d/%d HP)", hp, maxHp)).withStyle(s -> s.withColor(0x94A3B8)));
        }
        lines.add(heartsComp);

        // 3. Line 3: TRAINER / OWNER
        String ownerName = null;
        try {
            if (pokemon.getOwnerPlayer() != null) {
                ownerName = pokemon.getOwnerPlayer().getScoreboardName();
            } else if (entity != null && entity.getOwner() != null) {
                ownerName = entity.getOwner().getScoreboardName();
            } else if (pokemon.getOriginalTrainer() != null && !pokemon.getOriginalTrainer().isEmpty()) {
                ownerName = pokemon.getOriginalTrainer();
            }
        } catch (Throwable ignored) {}

        MutableComponent trainerComp = Component.literal(isRu ? "ТРЕНЕР: " : "TRAINER: ").withStyle(s -> s.withColor(0xCBD5E1));
        if (ownerName == null || ownerName.isEmpty()) {
            trainerComp.append(Component.literal(isRu ? "Дикий" : "Wild").withStyle(s -> s.withColor(0x4ADE80)));
        } else {
            trainerComp.append(Component.literal(ownerName).withStyle(s -> s.withColor(0xFFFFFF)));
        }
        lines.add(trainerComp);

        // 4. Line 4: Types: Rock/Flying
        MutableComponent typesComp = Component.literal(isRu ? "Типы: " : "Types: ").withStyle(s -> s.withColor(0xCBD5E1));
        List<Holder<Type>> types = pokemon.getTypes();
        if (types == null || types.isEmpty()) {
            typesComp.append(Component.literal("--").withStyle(s -> s.withColor(0x64748B)));
        } else {
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) {
                    typesComp.append(Component.literal("/").withStyle(s -> s.withColor(0x94A3B8)));
                }
                String rawName = types.get(i).unwrapKey().map(k -> k.location().getPath()).orElse("normal");
                String typeName = getShortTypeName(rawName, isRu);
                int color = getTypeColor(rawName);
                typesComp.append(Component.literal(typeName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
            }
        }
        lines.add(typesComp);

        // 5. Line 5: EV Yield (e.g. EV: 2 Атака)
        Stats form = pokemon.getForm();
        EVYields yields = (form != null) ? form.getEVYields() : null;
        String evText = formatEVYield(yields, isRu);
        MutableComponent evComp = Component.literal("EV: ").withStyle(s -> s.withColor(0xCBD5E1));
        evComp.append(Component.literal(evText).withStyle(s -> s.withColor(0xFFFFFF)));
        lines.add(evComp);

        // 6. Extended Information (Sneak / Normal)
        if (!isSneaking) {
            MutableComponent hintComp = Component.literal(isRu ? "<Присядьте для дополнительной информации>" : "<Sneak for more info>")
                .withStyle(s -> s.withColor(0x64748B).withItalic(true));
            lines.add(hintComp);
        } else {
            // SNEAK MODE:
            // 6.1 IVs
            try {
                IVStore ivStore = pokemon.getIVs();
                if (ivStore != null) {
                    int[] ivArr = ivStore.getArray();
                    int totalIv = 0;
                    for (int v : ivArr) totalIv += v;
                    int ivPct = (int) Math.round((totalIv / 186.0) * 100);

                    MutableComponent ivComp = Component.literal("IVs: ").withStyle(s -> s.withColor(0xCBD5E1));
                    ivComp.append(Component.literal(String.format(Locale.ROOT, "%d/%d/%d/%d/%d/%d ", ivArr[0], ivArr[1], ivArr[2], ivArr[3], ivArr[4], ivArr[5])).withStyle(s -> s.withColor(0xFFFFFF)));
                    ivComp.append(Component.literal("(" + ivPct + "%)").withStyle(s -> s.withColor(0x22C55E)));
                    lines.add(ivComp);

                    MutableComponent ivDetail = Component.empty();
                    appendStatIV(ivDetail, "HP", ivArr[0]);
                    ivDetail.append(Component.literal(" | ").withStyle(s -> s.withColor(0x475569)));
                    appendStatIV(ivDetail, isRu ? "Атк" : "Atk", ivArr[1]);
                    ivDetail.append(Component.literal(" | ").withStyle(s -> s.withColor(0x475569)));
                    appendStatIV(ivDetail, isRu ? "Защ" : "Def", ivArr[2]);
                    ivDetail.append(Component.literal(" | ").withStyle(s -> s.withColor(0x475569)));
                    appendStatIV(ivDetail, isRu ? "СпА" : "SpA", ivArr[3]);
                    ivDetail.append(Component.literal(" | ").withStyle(s -> s.withColor(0x475569)));
                    appendStatIV(ivDetail, isRu ? "СпЗ" : "SpD", ivArr[4]);
                    ivDetail.append(Component.literal(" | ").withStyle(s -> s.withColor(0x475569)));
                    appendStatIV(ivDetail, isRu ? "Скор" : "Spe", ivArr[5]);
                    lines.add(ivDetail);
                }
            } catch (Throwable ignored) {}

            // 6.2 Nature (Характер)
            try {
                if (pokemon.getNature() != null) {
                    MutableComponent natureComp = Component.literal((isRu ? "Характер: " : "Nature: ")).withStyle(s -> s.withColor(0xCBD5E1));
                    String natName = Component.translatable(pokemon.getNature().getTranslationKey()).getString();
                    natureComp.append(Component.literal(natName).withStyle(s -> s.withColor(0xFFFFFF)));

                    BattleStatsType inc = pokemon.getNature().getIncreasedStat();
                    BattleStatsType dec = pokemon.getNature().getDecreasedStat();
                    if (inc != null && dec != null && inc != dec) {
                        natureComp.append(Component.literal(" (+" + getStatShort(inc, isRu)).withStyle(s -> s.withColor(0x4ADE80)));
                        natureComp.append(Component.literal(", -").withStyle(s -> s.withColor(0x94A3B8)));
                        natureComp.append(Component.literal(getStatShort(dec, isRu) + ")").withStyle(s -> s.withColor(0xF87171)));
                    }
                    lines.add(natureComp);
                }
            } catch (Throwable ignored) {}

            // 6.3 Ability (Способность)
            try {
                String abilityName = (pokemon.getAbility() != null)
                    ? Component.translatable(pokemon.getAbility().getTranslationKey()).getString()
                    : pokemon.getAbilityName();
                if (abilityName != null && !abilityName.isEmpty()) {
                    MutableComponent abComp = Component.literal((isRu ? "Способность: " : "Ability: ")).withStyle(s -> s.withColor(0xCBD5E1));
                    abComp.append(Component.literal(abilityName).withStyle(s -> s.withColor(0xFFFFFF)));
                    if (pokemon.hasHiddenAbility()) {
                        abComp.append(Component.literal(isRu ? " (Скрытая)" : " (Hidden)").withStyle(s -> s.withColor(0xFBBF24)));
                    }
                    lines.add(abComp);
                }
            } catch (Throwable ignored) {}

            // 6.4 Trained EVs if player's own Pokémon
            try {
                boolean isOwn = mc.player != null && pokemon.getOwnerPlayer() != null && mc.player.getUUID().equals(pokemon.getOwnerPlayer().getUUID());
                if (isOwn && pokemon.getEVs() != null) {
                    EVStore evs = pokemon.getEVs();
                    int[] evArr = evs.getArray();
                    int totalEv = 0;
                    for (int v : evArr) totalEv += v;
                    MutableComponent evDetail = Component.literal("EVs: ").withStyle(s -> s.withColor(0xCBD5E1));
                    evDetail.append(Component.literal(String.format(Locale.ROOT, "%d/%d/%d/%d/%d/%d ", evArr[0], evArr[1], evArr[2], evArr[3], evArr[4], evArr[5])).withStyle(s -> s.withColor(0xFFFFFF)));
                    evDetail.append(Component.literal(String.format(Locale.ROOT, "(%d/510)", totalEv)).withStyle(s -> s.withColor(0xFDE047)));
                    lines.add(evDetail);
                }
            } catch (Throwable ignored) {}
        }

        // 7. Footer brand: Pixelmon
        MutableComponent brand = Component.literal("Pixelmon").withStyle(s -> s.withColor(0x3B82F6).withItalic(true));
        lines.add(brand);

        return lines;
    }

    private static void appendStatIV(MutableComponent comp, String statName, int iv) {
        comp.append(Component.literal(statName + " ").withStyle(s -> s.withColor(0x94A3B8)));
        int color = (iv == 31) ? 0x22C55E : (iv == 0 ? 0xEF4444 : 0xFFFFFF);
        comp.append(Component.literal(String.valueOf(iv)).withStyle(s -> s.withColor(color).withBold(iv == 31)));
    }

    public static void renderOverlayBox(
        GuiGraphics guiGraphics,
        Font font,
        List<Component> lines,
        String anchor,
        int offsetX,
        int offsetY,
        float scale,
        int alpha,
        int bgColorRgb,
        int borderColorRgb,
        int textColorRgb,
        int screenWidth,
        int screenHeight
    ) {
        if (lines.isEmpty()) return;

        int maxLineWidth = 0;
        for (Component line : lines) {
            int w = font.width(line);
            if (w > maxLineWidth) maxLineWidth = w;
        }

        int padding = 8;
        int lineHeight = 11;
        int boxWidth = Math.max(175, maxLineWidth + padding * 2);
        int boxHeight = lines.size() * lineHeight + padding * 2 - 2;

        int scaledW = (int) (boxWidth * scale);
        int scaledH = (int) (boxHeight * scale);

        int baseX = 0;
        int baseY = 0;

        switch (anchor) {
            case "TOP_LEFT" -> {
                baseX = 10 + offsetX;
                baseY = 10 + offsetY;
            }
            case "TOP_RIGHT" -> {
                baseX = screenWidth - scaledW - 10 + offsetX;
                baseY = 10 + offsetY;
            }
            case "BOTTOM_LEFT" -> {
                baseX = 10 + offsetX;
                baseY = screenHeight - scaledH - 35 + offsetY;
            }
            case "BOTTOM_RIGHT" -> {
                baseX = screenWidth - scaledW - 10 + offsetX;
                baseY = screenHeight - scaledH - 35 + offsetY;
            }
            case "CUSTOM" -> {
                baseX = offsetX;
                baseY = offsetY;
            }
            case "TOP_CENTER" -> {
                baseX = (screenWidth - scaledW) / 2 + offsetX;
                baseY = offsetY;
            }
            default -> {
                baseX = (screenWidth - scaledW) / 2 + offsetX;
                baseY = offsetY;
            }
        }

        int a = Math.max(0, Math.min(255, alpha));
        int argbBg = (a << 24) | (bgColorRgb & 0xFFFFFF);
        int borderAlpha = Math.min(255, a + 45);
        int argbBorder = (borderAlpha << 24) | (borderColorRgb & 0xFFFFFF);
        int argbInnerBorder = (Math.min(255, a / 2) << 24) | (borderColorRgb & 0xFFFFFF);

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(baseX, baseY, 0);
        poseStack.scale(scale, scale, 1.0F);

        // Background card fill
        guiGraphics.fill(0, 0, boxWidth, boxHeight, argbBg);

        // Stylish border outline
        guiGraphics.renderOutline(0, 0, boxWidth, boxHeight, argbBorder);
        guiGraphics.renderOutline(1, 1, boxWidth - 2, boxHeight - 2, argbInnerBorder);

        // Text lines
        int curY = padding;
        for (Component line : lines) {
            guiGraphics.drawString(font, line, padding, curY, textColorRgb, false);
            curY += lineHeight;
        }

        poseStack.popPose();
    }

    public static String getShortTypeName(String rawName, boolean isRu) {
        if (rawName == null) return "--";
        String n = rawName.toLowerCase(Locale.ROOT);
        if (!isRu) {
            return capitalize(n);
        }
        return switch (n) {
            case "rock" -> "Камен.";
            case "flying" -> "Летающ.";
            case "fire" -> "Огнен.";
            case "water" -> "Водный";
            case "grass" -> "Травян.";
            case "electric" -> "Электр.";
            case "ice" -> "Ледян.";
            case "fighting" -> "Боевой";
            case "poison" -> "Ядовит.";
            case "ground" -> "Землян.";
            case "psychic" -> "Психич.";
            case "bug" -> "Насеком.";
            case "ghost" -> "Призрак";
            case "dragon" -> "Дракон";
            case "steel" -> "Стальн.";
            case "dark" -> "Тёмный";
            case "fairy" -> "Волшеб.";
            case "normal" -> "Нормал.";
            default -> capitalize(n);
        };
    }

    public static int getTypeColor(String rawName) {
        if (rawName == null) return 0x94A3B8;
        return switch (rawName.toLowerCase(Locale.ROOT)) {
            case "normal" -> 0xA8A878;
            case "fire" -> 0xF08030;
            case "water" -> 0x6890F0;
            case "grass" -> 0x78C850;
            case "electric" -> 0xF8D030;
            case "ice" -> 0x98D8D8;
            case "fighting" -> 0xC03028;
            case "poison" -> 0xA040A0;
            case "ground" -> 0xE0C068;
            case "flying" -> 0xA890F0;
            case "psychic" -> 0xF85888;
            case "bug" -> 0xA8B820;
            case "rock" -> 0xB8A038;
            case "ghost" -> 0x705898;
            case "dragon" -> 0x7038F8;
            case "steel" -> 0xB8B8D0;
            case "dark" -> 0x705848;
            case "fairy" -> 0xEE99AC;
            default -> 0x94A3B8;
        };
    }

    public static String formatEVYield(EVYields yields, boolean isRu) {
        if (yields == null || yields.getTotalCount() == 0) {
            return "--";
        }
        List<String> parts = new ArrayList<>();
        if (yields.hp() > 0) parts.add(yields.hp() + " HP");
        if (yields.attack() > 0) parts.add(yields.attack() + " " + (isRu ? "Атака" : "Attack"));
        if (yields.defense() > 0) parts.add(yields.defense() + " " + (isRu ? "Защита" : "Defense"));
        if (yields.specialAttack() > 0) parts.add(yields.specialAttack() + " " + (isRu ? "Сп. Атака" : "Sp. Atk"));
        if (yields.specialDefense() > 0) parts.add(yields.specialDefense() + " " + (isRu ? "Сп. Защита" : "Sp. Def"));
        if (yields.speed() > 0) parts.add(yields.speed() + " " + (isRu ? "Скорость" : "Speed"));

        return String.join(", ", parts);
    }

    public static String getStatShort(BattleStatsType type, boolean isRu) {
        if (type == null) return "";
        return switch (type) {
            case HP -> "HP";
            case ATTACK -> isRu ? "Атк" : "Atk";
            case DEFENSE -> isRu ? "Защ" : "Def";
            case SPECIAL_ATTACK -> isRu ? "СпА" : "SpA";
            case SPECIAL_DEFENSE -> isRu ? "СпЗ" : "SpD";
            case SPEED -> isRu ? "Скор" : "Spe";
            default -> type.name();
        };
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static boolean isRussian(Minecraft mc) {
        if (mc == null || mc.getLanguageManager() == null) return false;
        return mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
