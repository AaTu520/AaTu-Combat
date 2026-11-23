package cn.aatu.combat.mixin;

import cn.aatu.combat.BypassProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerBypassProtectionMixin {
    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
            "Lnet/minecraft/world/entity/player/Player;setAbsorptionAmount(F)V"))
    private void aatu$protectAfterAbsorption(DamageSource source, float amount, CallbackInfo callback) {
        BypassProtection.protect((Player) (Object) this, source);
    }
}
