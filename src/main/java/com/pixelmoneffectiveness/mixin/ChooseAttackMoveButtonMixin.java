package com.pixelmoneffectiveness.mixin;

import com.pixelmoneffectiveness.util.PixelmonEffectivenessHelper;
import com.pixelmonmod.pixelmon.api.pokemon.type.Type;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.Effectiveness;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "com.pixelmonmod.pixelmon.client.gui.battles.battleScreens.ChooseAttack$MoveButton")
public abstract class ChooseAttackMoveButtonMixin {

    @Shadow
    private Attack attack;

    /**
     * Bypasses the Pokedex hasCaught check and single-opponent check,
     * allowing type effectiveness to be shown in every battle.
     */
    @Inject(method = "shouldRenderTypeEffectiveness", at = @At("HEAD"), cancellable = true)
    private void pixelmonEffectiveness$shouldRenderTypeEffectiveness(CallbackInfoReturnable<Boolean> cir) {
        if (PixelmonEffectivenessHelper.shouldShowEffectiveness(this.attack)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Redirects displayedEnemyPokemon array access so that in double battles
     * or when enemy 0 has fainted, the active/targeted enemy is used at index 0.
     */
    @Redirect(
        method = "renderAttackPPAndType",
        at = @At(
            value = "FIELD",
            target = "Lcom/pixelmonmod/pixelmon/client/gui/battles/ClientBattleManager;displayedEnemyPokemon:[Lcom/pixelmonmod/pixelmon/client/gui/battles/PixelmonClientData;"
        )
    )
    private PixelmonClientData[] pixelmonEffectiveness$redirectEnemyPokemon(ClientBattleManager bm) {
        PixelmonClientData active = PixelmonEffectivenessHelper.getActiveEnemy(bm);
        if (active != null) {
            return new PixelmonClientData[] { active };
        }
        return bm.displayedEnemyPokemon;
    }

    /**
     * Calculates move type effectiveness against enemy types,
     * including special move matchups like Freeze-Dry and Thousand Arrows.
     */
    @Redirect(
        method = "renderAttackPPAndType",
        at = @At(
            value = "INVOKEVIRTUAL",
            target = "Lcom/pixelmonmod/pixelmon/api/pokemon/type/Type;getTotalEffectiveness(Ljava/util/List;Z)D"
        )
    )
    private double pixelmonEffectiveness$calculateEffectiveness(Type attackType, List<Holder<Type>> enemyTypes, boolean inverse) {
        return PixelmonEffectivenessHelper.calculateEffectiveness(this.attack, attackType, enemyTypes, inverse);
    }

    /**
     * Formats the displayed effectiveness text with clear language and exact multiplier (e.g. 2x, 4x, 0.5x).
     */
    @Redirect(
        method = "renderAttackPPAndType",
        at = @At(
            value = "INVOKEVIRTUAL",
            target = "Lcom/pixelmonmod/pixelmon/battles/attacks/Effectiveness;displayName()Lnet/minecraft/network/chat/Component;"
        )
    )
    private Component pixelmonEffectiveness$formatDisplayName(Effectiveness eff) {
        return PixelmonEffectivenessHelper.formatDisplayName(eff, this.attack);
    }
}
