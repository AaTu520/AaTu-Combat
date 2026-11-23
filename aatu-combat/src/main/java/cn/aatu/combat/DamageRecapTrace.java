package cn.aatu.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Read-only, bounded diagnostics attached to one supported player's damage sequence. */
public final class DamageRecapTrace {
    private static final int MAX_CHANGES = 8;
    private static final int MAX_CALLER_LENGTH = 160;
    private static final int MAX_STACK_DEPTH = 20;
    private static final StackWalker WALKER = StackWalker.getInstance();

    private DamageRecapTrace() {}

    public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (!DamageRecapSync.supported(player)) return;
            CompoundTag trace = new CompoundTag();
            trace.putInt("version", 1);
            trace.putString("phase", "incoming");
            trace.put("changes", new ListTag());
            trace.putDouble("armor", player.getAttributeValue(Attributes.ARMOR));
            trace.putDouble("toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            CombatConfig.Settings settings = CombatConfig.current();
            trace.putBoolean("combatActive", CombatEvents.enabled(player, settings));
            ((DamageRecapTraceAccess) event.getContainer()).aatu$setDamageTrace(trace);
            capturePools(player, trace, "Before");
        } catch (RuntimeException exception) {
            DamageRecapRecorder.report(exception);
        }
    }

    public static void stage(Player player, String key, String nextPhase) {
        if (!(player instanceof ServerPlayer)) return;
        try {
            DamageContainer damage = CombatEvents.currentDamage(player);
            CompoundTag trace = trace(damage);
            if (trace == null) return;
            trace.putFloat(key, damage.getNewDamage());
            trace.putString("phase", nextPhase);
        } catch (RuntimeException exception) {
            DamageRecapRecorder.report(exception);
        }
    }

    public static void armorPreview(DamageContainer damage, float armor, float toughness,
                                    double poolSpent, double remaining) {
        try {
            CompoundTag trace = trace(damage);
            if (trace == null) return;
            trace.putBoolean("armorPreviewCalled", true);
            trace.putFloat("armorUsed", armor);
            trace.putFloat("toughnessUsed", toughness);
            trace.putDouble("poolSpent", poolSpent);
            trace.putDouble("armorPreviewOutput", remaining);
        } catch (RuntimeException exception) {
            DamageRecapRecorder.report(exception);
        }
    }

    public static void changed(DamageContainer damage, float nextDamage) {
        try {
            CompoundTag trace = trace(damage);
            if (trace == null || Float.compare(damage.getNewDamage(), nextDamage) == 0) return;
            ListTag changes = trace.getList("changes", Tag.TAG_COMPOUND);
            if (changes.size() >= MAX_CHANGES) {
                trace.putInt("extraChanges", Math.min(Integer.MAX_VALUE - 1, trace.getInt("extraChanges")) + 1);
                return;
            }
            CompoundTag change = new CompoundTag();
            change.putFloat("before", damage.getNewDamage());
            change.putFloat("after", nextDamage);
            change.putString("phase", trace.getString("phase"));
            change.putString("caller", caller());
            changes.add(change);
        } catch (RuntimeException exception) {
            DamageRecapRecorder.report(exception);
        }
    }

    public static void append(ServerPlayer player, DamageContainer damage, CompoundTag entry) {
        try {
            CompoundTag trace = trace(damage);
            if (trace == null) return;
            capturePools(player, trace, "After");
            trace.putFloat("armorReduction", damage.getReduction(DamageContainer.Reduction.ARMOR));
            trace.putFloat("enchantmentReduction", damage.getReduction(DamageContainer.Reduction.ENCHANTMENTS));
            trace.putFloat("effectReduction", damage.getReduction(DamageContainer.Reduction.MOB_EFFECTS));
            entry.put("diagnostics", trace.copy());
        } catch (RuntimeException exception) {
            DamageRecapRecorder.report(exception);
        }
    }

    private static CompoundTag trace(DamageContainer damage) {
        return damage instanceof DamageRecapTraceAccess access ? access.aatu$getDamageTrace() : null;
    }

    private static void capturePools(ServerPlayer player, CompoundTag trace, String suffix) {
        CombatConfig.Settings settings = CombatConfig.current();
        if (!CombatEvents.enabled(player, settings)) return;
        // snapshot() advances an immutable value; it does not save or spend either pool.
        PoolStore.Snapshot armor = PoolStore.snapshot(player, settings);
        ToughnessPoolStore.Snapshot toughness = ToughnessPoolStore.snapshot(player, settings);
        trace.putDouble("armorPool" + suffix, armor.state().points());
        trace.putDouble("armorCapacity" + suffix, armor.capacity());
        trace.putDouble("toughnessPool" + suffix, toughness.state().points());
        trace.putDouble("toughnessCapacity" + suffix, toughness.capacity());
    }

    private static String caller() {
        String caller = WALKER.walk(frames -> frames.limit(MAX_STACK_DEPTH)
                .filter(frame -> !infrastructure(frame.getClassName()))
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                .findFirst().orElse("unknown"));
        return caller.length() <= MAX_CALLER_LENGTH ? caller : caller.substring(0, MAX_CALLER_LENGTH);
    }

    private static boolean infrastructure(String name) {
        return name.startsWith(DamageRecapTrace.class.getName())
                || name.equals(DamageContainer.class.getName())
                || name.startsWith("net.neoforged.neoforge.event.entity.living.")
                || name.startsWith("java.") || name.startsWith("jdk.");
    }
}
