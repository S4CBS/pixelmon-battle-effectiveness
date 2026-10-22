package com.pixelmoneffectiveness.handler;

import com.pixelmonmod.pixelmon.world.dimension.ultraspace.UltraSpace;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Objects;

public class UltraSpaceFlightHandler {

    /**
     * In Pixelmon's UltraSpaceListener.onLivingTick, +0.07d is added to deltaMovement.y
     * on every tick in Ultra Space for non-creative living entities to simulate low gravity.
     * When a player has /fly enabled (player.getAbilities().flying == true), vanilla Minecraft
     * sets vertical gravity to 0, but Pixelmon still adds +0.07d to deltaMovement.y, causing
     * flying players in Survival to be continuously pulled upwards into the sky.
     *
     * We run at EventPriority.LOWEST on EntityTickEvent.Post (right after UltraSpaceListener)
     * and subtract 0.07d from deltaMovement.y if the player is flying, neutralizing the upward pull
     * so flight in Ultra Space feels identical to the Overworld.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
            if (!player.getAbilities().flying && !player.isFallFlying()) {
                return;
            }
            if (player.level() == null) {
                return;
            }
            if (UltraSpace.is(player.level()) || Objects.equals(player.level().dimension(), UltraSpace.WORLD)) {
                // Pixelmon checks !isNoGravity() && !isPassenger() && !isCrouching() && !isSwimming()
                // When crouching (pressing Shift to fly down), Pixelmon does NOT add 0.07d, so we don't subtract either.
                if (!player.isNoGravity() && !player.isPassenger() && !player.isCrouching() && !player.isSwimming()) {
                    Vec3 delta = player.getDeltaMovement();
                    player.setDeltaMovement(new Vec3(delta.x, delta.y - 0.07d, delta.z));
                }
            }
        }
    }
}
