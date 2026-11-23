package cn.aatu.combat;

import cn.aatu.combat.model.FormulaParameters;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CombatUiSync {
    private static final int UPDATE_INTERVAL = 5;
    private static final long ERROR_LOG_INTERVAL_NANOS = 60_000_000_000L;
    private static final Map<ServerPlayer, CompoundTag> LAST_SENT = new WeakHashMap<>();
    private static volatile ClientSnapshot clientSnapshot;
    private static long lastErrorNanos;

    private CombatUiSync() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(CombatUiPayload.TYPE, CombatUiPayload.CODEC, CombatUiSync::receive);
    }

    private static void receive(CombatUiPayload payload, IPayloadContext context) {
        clientSnapshot = new ClientSnapshot(context.connection(), payload.data().copy(), System.nanoTime());
    }

    public static CompoundTag clientState(Connection connection) {
        ClientSnapshot snapshot = clientSnapshot;
        if (snapshot == null || snapshot.connection != connection) return null;
        CompoundTag data = snapshot.data.copy();
        data.putLong("_uiReceivedAt", snapshot.receivedAt);
        return data;
    }

    public static void clearClientState() {
        clientSnapshot = null;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % UPDATE_INTERVAL != 0
                || player.connection == null) return;
        try {
            if (!player.connection.hasChannel(CombatUiPayload.TYPE)) return;
        } catch (RuntimeException exception) {
            reportFailure(exception);
            return;
        }
        CompoundTag data;
        try {
            CombatConfig.Settings settings = CombatConfig.current();
            data = CombatEvents.enabled(player, settings) ? activeState(player, settings) : state(0);
        } catch (RuntimeException exception) {
            reportFailure(exception);
            data = state(2);
        }
        ServerTickRate.addTo(data);
        try {
            if (!data.equals(LAST_SENT.get(player))) {
                PacketDistributor.sendToPlayer(player, new CombatUiPayload(data));
                LAST_SENT.put(player, data);
            }
        } catch (RuntimeException exception) {
            reportFailure(exception);
        }
    }

    private static void reportFailure(RuntimeException exception) {
        long now = System.nanoTime();
        if (lastErrorNanos == 0 || now - lastErrorNanos >= ERROR_LOG_INTERVAL_NANOS) {
            lastErrorNanos = now;
            CombatMod.LOGGER.warn("Unable to synchronize combat UI data", exception);
        }
    }

    private static CompoundTag activeState(ServerPlayer player, CombatConfig.Settings settings) {
        PoolStore.Snapshot snapshot = PoolStore.snapshot(player, settings);
        FormulaParameters formula = settings.formula();
        CompoundTag data = state(1);
        data.putDouble("pool", snapshot.state().points());
        data.putDouble("capacity", snapshot.capacity());
        data.putDouble("armor", snapshot.armor());
        data.putDouble("armorReduction", player.getArmorValue());
        data.putDouble("toughness", (float) snapshot.toughness());
        data.putDouble("health", player.getHealth());
        data.putDouble("maxReduction", formula.maxArmorReduction());
        data.putDouble("armorDivisor", formula.armorDivisor());
        data.putDouble("minimumArmor", formula.minimumArmorFraction());
        data.putDouble("baseToughness", formula.baseToughness());
        data.putDouble("toughnessDivisor", formula.toughnessDivisor());
        data.putDouble("soloReduction", formula.soloToughnessReduction());
        data.putDouble("soloReference", formula.soloToughnessReference());
        try {
            ToughnessPoolStore.Snapshot bypassPool = ToughnessPoolStore.snapshot(player, settings);
            data.putDouble("bypassAbsorption", BypassProtection.availableAbsorption(bypassPool.state().points()));
            data.putDouble("bypassAbsorptionMax", BypassProtection.availableAbsorption(bypassPool.capacity()));
        } catch (RuntimeException exception) {
            reportFailure(exception);
        }
        return data;
    }

    private static CompoundTag state(int status) {
        CompoundTag data = new CompoundTag();
        data.putInt("status", status);
        return data;
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LAST_SENT.remove(player);
    }

    public static void stopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
        ServerTickRate.clear();
        lastErrorNanos = 0;
    }

    private record ClientSnapshot(Connection connection, CompoundTag data, long receivedAt) {}
}
