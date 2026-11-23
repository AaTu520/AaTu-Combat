package cn.aatu.combat;

import cn.aatu.combat.model.DamageFormula;
import cn.aatu.combat.model.PoolState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Independent pool that funds damage absorption for direct bypass attacks. */
public final class ToughnessPoolStore {
    public static final String KEY = "aatu_combat:toughness_pool";

    private ToughnessPoolStore() {}

    public static Snapshot snapshot(ServerPlayer player, CombatConfig.Settings settings) {
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (!Double.isFinite(toughness)) throw new IllegalArgumentException("Toughness must be finite");
        double capacity = Math.max(0, toughness) * settings.bypassToughnessPoolMultiplier();
        long now = PoolStore.now(player);
        CompoundTag root = player.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) {
            return new Snapshot(new PoolState(capacity, now, now), capacity);
        }
        CompoundTag saved = root.getCompound(KEY);
        double previousCapacity = saved.getDouble("capacity");
        if (!Double.isFinite(previousCapacity)) previousCapacity = 0;
        PoolState state = new PoolState(saved.getDouble("points"), saved.getLong("lastHitTick"),
                saved.getLong("lastRecoveryTick"));
        // Do not apply a newly equipped item's capacity to time that has already elapsed.
        state = state.advance(Math.max(0, Math.min(capacity, previousCapacity)), now,
                settings.idleEnabled(), settings.idleDelayTicks(), settings.idleFractionPerSecond());
        return new Snapshot(state, capacity);
    }

    public static void save(ServerPlayer player, PoolState state, double capacity) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("points", DamageFormula.clamp(state.points(), 0, capacity));
        tag.putDouble("capacity", capacity);
        tag.putLong("lastHitTick", state.lastHitTick());
        tag.putLong("lastRecoveryTick", state.lastRecoveryTick());
        player.getPersistentData().put(KEY, tag);
    }

    public static void refresh(ServerPlayer player, CombatConfig.Settings settings) {
        Snapshot snapshot = snapshot(player, settings);
        save(player, snapshot.state(), snapshot.capacity());
    }

    public record Snapshot(PoolState state, double capacity) {}
}
