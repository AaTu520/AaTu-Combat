package cn.aatu.combat.mixin;

import cn.aatu.combat.CombatEvents;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealingMixin {
    @Unique private int aatu$healDepth;

    @WrapMethod(method = "heal(F)V")
    private void aatu$actualHealing(float amount, Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        float before = entity.getHealth();
        boolean outermost = aatu$healDepth++ == 0;
        try { original.call(amount); }
        finally { aatu$healDepth--; }
        // The final health delta prevents canceled, capped or nested healing from replenishing twice.
        if (outermost) CombatEvents.healed(entity, entity.getHealth() - before);
    }
}
