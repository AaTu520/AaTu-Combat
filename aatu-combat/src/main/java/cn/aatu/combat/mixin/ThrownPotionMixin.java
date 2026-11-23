package cn.aatu.combat.mixin;

import cn.aatu.combat.EffectDamageContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownPotion.class)
public abstract class ThrownPotionMixin {
    @WrapOperation(method = "applySplash", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"))
    private void aatu$markInstantEffect(MobEffect effect, Entity direct, Entity indirect, LivingEntity target,
                                        int amplifier, double intensity, Operation<Void> original) {
        if (target.level().isClientSide()) {
            original.call(effect, direct, indirect, target, amplifier, intensity);
            return;
        }
        EffectDamageContext.enter();
        try {
            original.call(effect, direct, indirect, target, amplifier, intensity);
        } finally {
            EffectDamageContext.exit();
        }
    }
}
