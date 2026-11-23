package cn.aatu.combat.mixin;

import cn.aatu.combat.DamageRecapTrace;
import cn.aatu.combat.DamageRecapTraceAccess;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DamageContainer.class, remap = false)
public abstract class DamageContainerTraceMixin implements DamageRecapTraceAccess {
    @Unique private CompoundTag aatu$damageTrace;

    @Override
    public CompoundTag aatu$getDamageTrace() { return aatu$damageTrace; }

    @Override
    public void aatu$setDamageTrace(CompoundTag trace) { aatu$damageTrace = trace; }

    @Inject(method = "setNewDamage", at = @At("HEAD"))
    private void aatu$recordDamageChange(float damage, CallbackInfo callback) {
        if (aatu$damageTrace != null) DamageRecapTrace.changed((DamageContainer) (Object) this, damage);
    }
}
