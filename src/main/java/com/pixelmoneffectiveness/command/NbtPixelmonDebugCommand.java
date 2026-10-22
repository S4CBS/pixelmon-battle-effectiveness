package com.pixelmoneffectiveness.command;

import com.mojang.brigadier.CommandDispatcher;
import com.pixelmoneffectiveness.client.render.PokemonDespawnDebugRenderer;
import com.pixelmoneffectiveness.client.telegram.TelegramEventWatcher;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class NbtPixelmonDebugCommand {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var toggleExec = (com.mojang.brigadier.Command<CommandSourceStack>) ctx -> {
            boolean enabled = false;
            if (FMLEnvironment.dist == Dist.CLIENT) {
                enabled = PokemonDespawnDebugRenderer.toggleDebug();
            }
            final boolean finalEnabled = enabled;
            if (finalEnabled) {
                ctx.getSource().sendSuccess(() -> Component.literal("§6[PixelmonDebug] §fОтображение деспавна над покемонами: §aВКЛЮЧЕНО"), false);
                ctx.getSource().sendSuccess(() -> Component.literal("§7(Зелёный/Жёлтый/Красный = активный таймер 4 мин, Голубой = заморожен 10 мин, Золотой/Сияющий = босс/легендарный)"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("§6[PixelmonDebug] §fОтображение деспавна над покемонами: §cВЫКЛЮЧЕНО"), false);
            }
            return 1;
        };

        var purgeExec = (com.mojang.brigadier.Command<CommandSourceStack>) ctx -> {
            CommandSourceStack src = ctx.getSource();
            MinecraftServer server = src.getServer();
            int count = 0;
            if (server != null) {
                ServerPlayer player = null;
                try {
                    player = src.getPlayer();
                } catch (Throwable ignored) {}

                for (ServerLevel level : server.getAllLevels()) {
                    for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof PixelmonEntity pe && pe.isAlive()) {
                            if (!TelegramEventWatcher.isWildPokemon(pe)) continue;
                            if (pe.isBossPokemon()) continue;
                            Pokemon p = pe.getPokemon();
                            if (p != null && (p.isBoss() || p.isLegendary() || p.isMythical() || p.isUltraBeast() || p.isShiny())) {
                                continue;
                            }
                            double dist = player != null ? pe.distanceTo(player) : 100.0;
                            if (dist > 64.0) {
                                pe.discard();
                                count++;
                            }
                        }
                    }
                }
            }
            final int purged = count;
            src.sendSuccess(() -> Component.literal("§a[PixelmonDebug] Очищено §e" + purged + "§a дальних диких покемонов (>64 блоков). Свежие покемоны сразу появятся рядом!"), false);
            return 1;
        };

        dispatcher.register(Commands.literal("nbtPixelmonDebug")
                .executes(toggleExec)
                .then(Commands.literal("clear").executes(purgeExec))
                .then(Commands.literal("purge").executes(purgeExec)));

        dispatcher.register(Commands.literal("nbtpixelmondebug")
                .executes(toggleExec)
                .then(Commands.literal("clear").executes(purgeExec))
                .then(Commands.literal("purge").executes(purgeExec)));

        dispatcher.register(Commands.literal("nbtdebug")
                .executes(toggleExec)
                .then(Commands.literal("clear").executes(purgeExec))
                .then(Commands.literal("purge").executes(purgeExec)));
    }
}
