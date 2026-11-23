package cn.aatu.combat.mixin;

import cn.aatu.combat.CombatEvents;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CombatRules.class)
public abstract class CombatRulesMixin {
    @Inject(method = "getDamageAfterAbsorb", at = @At("HEAD"), cancellable = true)
    private static void aatu$preview(LivingEntity entity, float damage, DamageSource source,
                                     float armor, float toughness, CallbackInfoReturnable<Float> callback) {
        Float remaining = CombatEvents.preview(entity, damage, source, armor, toughness);
        if (remaining != null) callback.setReturnValue(remaining);
    }
}
