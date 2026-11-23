package cn.aatu.combat.mixin;

import cn.aatu.combat.DamageRecapRecorder;
import cn.aatu.combat.DamageRecapTrace;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDamageRecapMixin {
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void aatu$beforeArmor(DamageSource source, float amount, CallbackInfo callback) {
        DamageRecapTrace.stage((Player) (Object) this, "beforeArmor", "armor");
    }

    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private void aatu$afterArmor(DamageSource source, float amount, CallbackInfo callback) {
        DamageRecapTrace.stage((Player) (Object) this, "afterArmor", "magic");
    }

    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePre(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)F", remap = false))
    private void aatu$beforeDamageEvent(DamageSource source, float amount, CallbackInfo callback) {
        DamageRecapTrace.stage((Player) (Object) this, "afterMagic", "final_event");
    }

    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
            "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePre(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)F", remap = false))
    private void aatu$afterDamageEvent(DamageSource source, float amount, CallbackInfo callback) {
        DamageRecapTrace.stage((Player) (Object) this, "afterDamageEvent", "absorption");
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void aatu$recordHealthChange(Player player, float requestedHealth, Operation<Void> original,
                                         DamageSource source, float amount) {
        var pending = DamageRecapRecorder.beforeHealthChange(player, source, requestedHealth);
        original.call(player, requestedHealth);
        DamageRecapRecorder.afterHealthChange(pending);
    }
}
