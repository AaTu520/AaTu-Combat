package cn.aatu.combat;

import cn.aatu.combat.model.DamageFormula;
import cn.aatu.combat.model.PoolState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Persistent NBT avoids a synced registry or client dependency. No per-tick entity scan is needed. */
public final class PoolStore {
    public static final String KEY = "aatu_combat:pool";

    private PoolStore() {}

    public static long now(LivingEntity entity) {
        return entity.getServer().overworld().getGameTime();
    }

    public static Snapshot snapshot(LivingEntity entity, CombatConfig.Settings settings) {
        double armor = entity.getAttributeValue(Attributes.ARMOR);
        double toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double capacity = DamageFormula.capacity(armor, toughness, settings.formula());
        long now = now(entity);
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) {
            return new Snapshot(new PoolState(capacity, now, now), capacity, armor, toughness);
        }
        CompoundTag saved = root.getCompound(KEY);
        double previousCapacity = saved.getDouble("capacity");
        if (!Double.isFinite(previousCapacity)) previousCapacity = 0;
        PoolState state = new PoolState(saved.getDouble("points"), saved.getLong("lastHitTick"),
                saved.getLong("lastRecoveryTick"));
        // A capacity increase must not retroactively regenerate at the newly equipped armor's rate.
        state = state.advance(Math.max(0, Math.min(capacity, previousCapacity)), now,
                settings.idleEnabled(), settings.idleDelayTicks(), settings.idleFractionPerSecond());
        return new Snapshot(state, capacity, armor, toughness);
    }

    public static void save(LivingEntity entity, PoolState state, double capacity) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("points", DamageFormula.clamp(state.points(), 0, capacity));
        tag.putDouble("capacity", capacity);
        tag.putLong("lastHitTick", state.lastHitTick());
        tag.putLong("lastRecoveryTick", state.lastRecoveryTick());
        entity.getPersistentData().put(KEY, tag);
    }

    public static void refresh(LivingEntity entity, CombatConfig.Settings settings) {
        Snapshot snapshot = snapshot(entity, settings);
        save(entity, snapshot.state(), snapshot.capacity());
    }

    public record Snapshot(PoolState state, double capacity, double armor, double toughness) {}
}
