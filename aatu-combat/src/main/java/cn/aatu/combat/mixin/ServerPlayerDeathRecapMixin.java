package cn.aatu.combat.mixin;

import cn.aatu.combat.DamageRecapRecorder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Observe before Incapacitated cancels die(); this hook never changes the result.
@Mixin(value = ServerPlayer.class, priority = 1000000)
public abstract class ServerPlayerDeathRecapMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void aatu$recordDeathSource(DamageSource source, CallbackInfo callback) {
        DamageRecapRecorder.deathAttempt((ServerPlayer) (Object) this, source);
    }
}
