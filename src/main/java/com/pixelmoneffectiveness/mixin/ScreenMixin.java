package com.pixelmoneffectiveness.mixin;

import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.log.SingleInstanceMoveResult;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.status.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes a NullPointerException in Screen.apply when used outside of standard attacks,
 * such as during Dynamax Raid cheer effects (DynamaxCheerRaidStrategy.cheer).
 * Pixelmon attempts to access user.attack without checking for null, crashing the battle.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Redirect(
        method = "apply",
        at = @At(
            value = "INVOKE",
            target = "Lcom/pixelmonmod/pixelmon/battles/attacks/Attack;getMoveResultForPixelmonWrapper(Lcom/pixelmonmod/pixelmon/battles/controller/participants/PixelmonWrapper;)Lcom/pixelmonmod/pixelmon/battles/controller/log/SingleInstanceMoveResult;"
        )
    )
    private SingleInstanceMoveResult pixelmonEffectiveness$safeGetMoveResult(Attack attack, PixelmonWrapper user) {
        if (attack == null) {
            return null;
        }
        return attack.getMoveResultForPixelmonWrapper(user);
    }
}
