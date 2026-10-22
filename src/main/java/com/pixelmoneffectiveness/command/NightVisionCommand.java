package com.pixelmoneffectiveness.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collection;

public class NightVisionCommand {

    private static final String NBT_TAG_NIGHT_VISION = "pe_night_vision";

    private static CompoundTag getPersistentData(ServerPlayer player) {
        if ((Object) player instanceof IEntityExtension ext) {
            return ext.getPersistentData();
        }
        return null;
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /nightvision
        var builder = Commands.literal("nightvision")
                .executes(ctx -> toggleSelf(ctx.getSource()))
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> toggleTargets(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
        dispatcher.register(builder);

        // /nv (alias)
        var aliasBuilder = Commands.literal("nv")
                .executes(ctx -> toggleSelf(ctx.getSource()))
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> toggleTargets(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
        dispatcher.register(aliasBuilder);
    }

    private static int toggleSelf(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = toggleNightVision(player);
        if (enabled) {
            source.sendSuccess(() -> Component.literal("§6[Pixelmon] §fНочное зрение: §aВключено"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6[Pixelmon] §fНочное зрение: §cВыключено"), false);
        }
        return 1;
    }

    private static int toggleTargets(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            boolean enabled = toggleNightVision(player);
            if (enabled) {
                player.sendSystemMessage(Component.literal("§6[Pixelmon] §fНочное зрение: §aВключено"));
            } else {
                player.sendSystemMessage(Component.literal("§6[Pixelmon] §fНочное зрение: §cВыключено"));
            }
        }
        source.sendSuccess(() -> Component.literal("§6[Pixelmon] §fПереключено ночное зрение для §e" + targets.size() + " §fигроков"), true);
        return targets.size();
    }

    public static boolean toggleNightVision(ServerPlayer player) {
        CompoundTag data = getPersistentData(player);
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            if (data != null) data.putBoolean(NBT_TAG_NIGHT_VISION, false);
            return false;
        } else {
            if (data != null) data.putBoolean(NBT_TAG_NIGHT_VISION, true);
            applyNightVision(player);
            return true;
        }
    }

    public static void applyNightVision(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                MobEffectInstance.INFINITE_DURATION,
                0,
                false, // ambient
                false, // visible particles
                true   // showIcon
        ));
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = getPersistentData(player);
            if (data != null && data.getBoolean(NBT_TAG_NIGHT_VISION)) {
                applyNightVision(player);
            }
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = getPersistentData(player);
            if (data != null && data.getBoolean(NBT_TAG_NIGHT_VISION)) {
                applyNightVision(player);
            }
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = getPersistentData(player);
            if (data != null && data.getBoolean(NBT_TAG_NIGHT_VISION)) {
                applyNightVision(player);
            }
        }
    }
}
