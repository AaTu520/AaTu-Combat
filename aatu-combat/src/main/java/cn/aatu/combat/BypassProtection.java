package cn.aatu.combat;

import cn.aatu.combat.model.PoolState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

public final class BypassProtection {
    private static final double POOL_POINTS_PER_DAMAGE = 5.0;
    private static final double MAXIMUM_ABSORBED_FRACTION = 0.50;

    private BypassProtection() {}

    public static void protect(Player player, DamageSource source) {
        CombatConfig.Settings settings = CombatConfig.current();
        if (!(player instanceof ServerPlayer serverPlayer) || !CombatEvents.enabled(player, settings)
                || !isDirectBypassAttack(source)) return;
        try {
            DamageContainer container = CombatEvents.currentDamage(player);
            if (container == null) return;
            float damage = container.getNewDamage();
            if (!Float.isFinite(damage) || damage <= 0) return;

            ToughnessPoolStore.Snapshot pool = ToughnessPoolStore.snapshot(serverPlayer, settings);
            double points = pool.state().points();
            if (!Double.isFinite(points) || points <= 0) return;

            double absorbedDamage = Math.min(damage * MAXIMUM_ABSORBED_FRACTION, availableAbsorption(points));
            double exactRemaining = damage - absorbedDamage;
            float remainingDamage = (float) exactRemaining;
            // Round up so float precision cannot absorb more damage than the pool funds.
            if (remainingDamage < exactRemaining) remainingDamage = Math.nextUp(remainingDamage);
            double appliedAbsorption = (double) damage - remainingDamage;
            if (appliedAbsorption <= 0) return;

            PoolState spent = pool.state().hit(appliedAbsorption * POOL_POINTS_PER_DAMAGE, PoolStore.now(player));
            ToughnessPoolStore.save(serverPlayer, spent, pool.capacity());
            container.setNewDamage(remainingDamage);
        } catch (RuntimeException exception) {
            CombatEvents.reportFailure(exception);
        }
    }

    public static double availableAbsorption(double points) {
        return Double.isFinite(points) ? Math.max(0, points) / POOL_POINTS_PER_DAMAGE : 0;
    }

    private static boolean isDirectBypassAttack(DamageSource source) {
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR) || EffectDamageContext.active()
                || isEnvironmental(source)) return false;
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        // An unattributed source can also be an environmental hazard or an effect.
        return attacker instanceof LivingEntity || direct instanceof LivingEntity
                || (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity)
                || (source.is(DamageTypes.MAGIC) && AttackDamageContext.active());
    }

    private static boolean isEnvironmental(DamageSource source) {
        return source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.OUTSIDE_BORDER) || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.WITHER);
    }
}
