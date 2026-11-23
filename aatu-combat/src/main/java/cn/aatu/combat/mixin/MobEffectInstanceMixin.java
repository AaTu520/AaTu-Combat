package cn.aatu.combat.mixin;

import cn.aatu.combat.EffectDamageContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean aatu$markEffectTick(MobEffect effect, LivingEntity target, int amplifier,
                                        Operation<Boolean> original) {
        if (target.level().isClientSide()) return original.call(effect, target, amplifier);
        EffectDamageContext.enter();
        try {
            return original.call(effect, target, amplifier);
        } finally {
            EffectDamageContext.exit();
        }
    }
}
