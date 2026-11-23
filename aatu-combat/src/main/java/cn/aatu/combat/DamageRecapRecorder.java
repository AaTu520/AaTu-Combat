package cn.aatu.combat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class DamageRecapRecorder {
    private static final int MAX_HITS = 64;
    private static final int INCIDENT_WINDOW_TICKS = 30 * 20;
    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final Map<UUID, History> HISTORIES = new HashMap<>();
    private static long lastErrorNanos;

    private DamageRecapRecorder() {}

    public static Pending beforeHealthChange(Player target, DamageSource source, float requestedHealth) {
        if (!(target instanceof ServerPlayer player)) return null;
        try {
            if (!DamageRecapSync.supported(player)) return null;
            CompoundTag entry = sourceDetails(player, source);
            DamageContainer damage = CombatEvents.currentDamage(player);
            float before = player.getHealth();
            entry.putFloat("input", damage == null ? Float.NaN : damage.getOriginalDamage());
            entry.putFloat("damage", damage == null ? before - requestedHealth : damage.getNewDamage());
            entry.putFloat("before", before);
            entry.putFloat("absorption", damage == null ? 0 : damage.getReduction(DamageContainer.Reduction.ABSORPTION));
            entry.putBoolean("lethal", before > 0 && requestedHealth <= 0);
            DamageRecapTrace.append(player, damage, entry);
            return new Pending(player, entry, before);
        } catch (RuntimeException exception) {
            report(exception);
            return null;
        }
    }

    public static void afterHealthChange(Pending pending) {
        if (pending == null) return;
        try {
            float after = pending.player.getHealth();
            pending.entry.putFloat("after", after);
            pending.entry.putFloat("loss", Math.max(0, pending.before - after));
            append(pending.player, pending.entry);
        } catch (RuntimeException exception) {
            report(exception);
        }
    }

    public static void deathAttempt(ServerPlayer player, DamageSource source) {
        try {
            if (!DamageRecapSync.supported(player)) return;
            History history = history(player);
            CompoundTag last = history.hits.peekLast();
            long tick = now(player);
            // A normal lethal hit was already recorded before die() can restore health or cancel death.
            if (last == null || last.getLong("tick") != tick || !last.getBoolean("lethal")) {
                CompoundTag entry = sourceDetails(player, source);
                for (String key : new String[]{"input", "damage", "before", "loss", "absorption"}) entry.putFloat(key, Float.NaN);
                entry.putFloat("after", player.getHealth());
                entry.putBoolean("unmeasured", true);
                entry.putBoolean("lethal", true);
                append(player, entry);
            }
            history.pending = snapshot(history, tick);
            history.pendingTick = tick;
        } catch (RuntimeException exception) {
            report(exception);
        }
    }

    private static CompoundTag sourceDetails(ServerPlayer player, DamageSource source) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("tick", now(player));
        tag.putString("source", bounded(source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse(source.getMsgId())));
        tag.putBoolean("bypass", source.is(DamageTypeTags.BYPASSES_ARMOR));
        tag.putBoolean("effect", EffectDamageContext.active());
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker == null && direct instanceof Projectile projectile) attacker = projectile.getOwner();
        if (attacker == null) attacker = direct;
        if (attacker != null) {
            tag.putString("attackerType", bounded(BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString()));
            tag.putString("attackerKey", bounded(attacker.getType().getDescriptionId()));
            if (attacker.hasCustomName() || attacker instanceof Player) {
                tag.putString("attackerName", bounded(attacker.getDisplayName().getString()));
            }
        }
        if (direct != null) tag.putString("directType", bounded(BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString()));
        return tag;
    }

    private static String bounded(String value) {
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= 160 ? clean : clean.substring(0, 160);
    }

    private static void append(ServerPlayer player, CompoundTag entry) {
        History history = history(player);
        entry.putLong("id", ++history.sequence);
        history.hits.addLast(entry);
        while (history.hits.size() > MAX_HITS) history.hits.removeFirst();
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SYNC_INTERVAL_TICKS != 0) return;
        try {
            if (!DamageRecapSync.supported(player)) return;
            History history = history(player);
            int phase = player.isDeadOrDying() ? 2 : IncapacitatedLink.isDown(player) ? 1 : 0;
            if (phase != history.phase) {
                if (phase != 0) {
                    if (history.phase != 1 || phase != 2) freeze(history, now(player));
                    history.reason = phase;
                    history.incidentVersion++;
                }
                history.phase = phase;
            }
            if (history.sent && history.sentSequence == history.sequence && history.sentPhase == history.phase
                    && history.sentIncident == history.incidentVersion) return;
            CompoundTag packet = new CompoundTag();
            packet.putInt("protocol", 1);
            packet.putLong("tick", now(player));
            packet.putInt("phase", phase);
            packet.putLong("incidentVersion", history.incidentVersion);
            packet.putLong("incidentTick", history.incidentTick);
            packet.putInt("reason", history.reason);
            ListTag entries = new ListTag();
            for (CompoundTag entry : history.hits) {
                if (entry.getLong("id") > history.sentSequence) entries.add(entry.copy());
            }
            packet.put("entries", entries);
            if (history.sentIncident != history.incidentVersion) packet.put("incident", history.frozen.copy());
            DamageRecapSync.send(player, packet);
            history.sent = true;
            history.sentSequence = history.sequence;
            history.sentPhase = phase;
            history.sentIncident = history.incidentVersion;
        } catch (RuntimeException exception) {
            report(exception);
        }
    }

    private static void freeze(History history, long tick) {
        // Snapshot at die(), before a downing mod restores health or later hits replace the fatal entry.
        if (history.pending != null && tick - history.pendingTick <= 20) {
            history.frozen = history.pending;
            history.incidentTick = history.pendingTick;
        } else {
            history.frozen = snapshot(history, tick);
            history.incidentTick = tick;
        }
        history.pending = null;
    }

    private static ListTag snapshot(History history, long tick) {
        ListTag entries = new ListTag();
        for (CompoundTag entry : history.hits) {
            if (tick - entry.getLong("tick") <= INCIDENT_WINDOW_TICKS) entries.add(entry.copy());
        }
        return entries;
    }

    public static void cloned(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        try {
            History history = HISTORIES.get(event.getEntity().getUUID());
            if (history == null || history.phase == 2) return;
            if (history.phase != 1) freeze(history, event.getEntity().level().getGameTime());
            history.reason = 2;
            history.phase = 2;
            history.incidentVersion++;
        } catch (RuntimeException exception) {
            report(exception);
        }
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        HISTORIES.remove(event.getEntity().getUUID());
    }

    public static void stopped(ServerStoppedEvent event) {
        HISTORIES.clear();
        lastErrorNanos = 0;
    }

    private static History history(ServerPlayer player) {
        return HISTORIES.computeIfAbsent(player.getUUID(), ignored -> new History());
    }

    private static long now(ServerPlayer player) { return player.level().getGameTime(); }

    static void report(RuntimeException exception) {
        long now = System.nanoTime();
        if (lastErrorNanos == 0 || now - lastErrorNanos > 60_000_000_000L) {
            lastErrorNanos = now;
            CombatMod.LOGGER.warn("Unable to record or synchronize damage recap", exception);
        }
    }

    public record Pending(ServerPlayer player, CompoundTag entry, float before) {}

    private static final class History {
        final ArrayDeque<CompoundTag> hits = new ArrayDeque<>();
        ListTag frozen = new ListTag();
        ListTag pending;
        long sequence, sentSequence, incidentVersion, sentIncident, incidentTick, pendingTick;
        int phase, sentPhase, reason;
        boolean sent;
    }
}
