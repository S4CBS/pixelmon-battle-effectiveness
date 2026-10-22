package com.pixelmoneffectiveness.command;

import com.mojang.brigadier.CommandDispatcher;
import com.pixelmoneffectiveness.client.render.PokemonDespawnDebugRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class NbtPixelmonDebugCommand {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var exec = (com.mojang.brigadier.Command<CommandSourceStack>) ctx -> {
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

        dispatcher.register(Commands.literal("nbtPixelmonDebug").executes(exec));
        dispatcher.register(Commands.literal("nbtpixelmondebug").executes(exec));
        dispatcher.register(Commands.literal("nbtdebug").executes(exec));
    }
}
