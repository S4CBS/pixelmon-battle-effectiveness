package com.pixelmoneffectiveness.client.telegram;

import com.mojang.brigadier.CommandDispatcher;
import com.pixelmoneffectiveness.config.EffectivenessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class TelegramCommand {

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var tgBuilder = Commands.literal("telegram")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new TelegramSettingsScreen(null)));
                    return 1;
                })
                .then(Commands.literal("test")
                        .executes(ctx -> {
                            String token = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramBotToken.get() : "";
                            String chatId = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramChatId.get() : "";
                            TelegramBotClient.sendTestNotification(token, chatId, (success, msg) -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player != null) {
                                    mc.player.displayClientMessage(
                                            Component.literal(success ? ("§a[Telegram] " + msg) : ("§c[Telegram] " + msg)),
                                            false
                                    );
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            boolean now = TelegramBotClient.toggleEnabled();
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                String label = now ? "§aВключены" : "§cВыключены";
                                mc.player.displayClientMessage(Component.literal("§6[Telegram] §fУведомления: " + label), false);
                            }
                            return 1;
                        }));

        dispatcher.register(tgBuilder);

        var aliasBuilder = Commands.literal("tg")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new TelegramSettingsScreen(null)));
                    return 1;
                })
                .then(Commands.literal("test")
                        .executes(ctx -> {
                            String token = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramBotToken.get() : "";
                            String chatId = EffectivenessConfig.CONFIG != null ? EffectivenessConfig.CONFIG.telegramChatId.get() : "";
                            TelegramBotClient.sendTestNotification(token, chatId, (success, msg) -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player != null) {
                                    mc.player.displayClientMessage(
                                            Component.literal(success ? ("§a[Telegram] " + msg) : ("§c[Telegram] " + msg)),
                                            false
                                    );
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            boolean now = TelegramBotClient.toggleEnabled();
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                String label = now ? "§aВключены" : "§cВыключены";
                                mc.player.displayClientMessage(Component.literal("§6[Telegram] §fУведомления: " + label), false);
                            }
                            return 1;
                        }));

        dispatcher.register(aliasBuilder);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var builder = Commands.literal("pixelmonbot")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6[Telegram] §fИспользуйте §e/telegram§f или §e/tg§f для открытия меню настроек бота."), false);
                    return 1;
                });
        dispatcher.register(builder);
    }
}
