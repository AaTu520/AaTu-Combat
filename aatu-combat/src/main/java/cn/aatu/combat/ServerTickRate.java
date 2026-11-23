package cn.aatu.combat;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerTickRate {
    private static final TickRateWindow WINDOW = new TickRateWindow();

    private ServerTickRate() {}

    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        WINDOW.tick(System.nanoTime(), server.tickRateManager().tickrate(),
                server.isPaused() || server.tickRateManager().isFrozen());
    }

    public static void addTo(CompoundTag data) {
        TickRateWindow.Snapshot sample = WINDOW.snapshot();
        data.putDouble("tps", sample.tps());
        data.putDouble("tickTarget", sample.target());
        data.putBoolean("ticksPaused", sample.paused());
        // A sequence also sends a heartbeat when TPS and combat values stay unchanged.
        data.putLong("tpsSequence", sample.sequence());
    }

    public static void clear() {
        WINDOW.clear();
    }
}
