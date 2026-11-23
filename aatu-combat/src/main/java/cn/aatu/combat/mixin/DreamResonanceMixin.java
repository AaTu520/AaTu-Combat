package cn.aatu.combat.mixin;

import cn.aatu.combat.AttackDamageContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.bmt.dream_champions.common.affix.ResonanceAffix", remap = false)
public abstract class DreamResonanceMixin {
    @WrapOperation(method = "onHurt", remap = false, require = 0, at = @At(value = "INVOKE", remap = false, target =
            "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean aatu$markResonance(LivingEntity target, DamageSource source, float amount,
                                        Operation<Boolean> original) {
        if (target.level().isClientSide()) return original.call(target, source, amount);
        AttackDamageContext.enter();
        try {
            return original.call(target, source, amount);
        } finally {
            AttackDamageContext.exit();
        }
    }
}
