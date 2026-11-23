package cn.aatu.combat;

import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DamageRecapSync {
    private static volatile BiConsumer<Connection, CompoundTag> clientReceiver;

    private DamageRecapSync() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(DamageRecapPayload.TYPE, DamageRecapPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var receiver = clientReceiver;
                    if (receiver != null) receiver.accept(context.connection(), payload.data());
                }));
    }

    // UI supplies the callback only on a physical client; the server never loads UI classes.
    public static void setClientReceiver(BiConsumer<Connection, CompoundTag> receiver) {
        clientReceiver = receiver;
    }

    static boolean supported(ServerPlayer player) {
        return CombatConfig.damageRecapEnabled() && player.connection != null && player.connection.hasChannel(DamageRecapPayload.TYPE);
    }

    static void send(ServerPlayer player, CompoundTag data) {
        PacketDistributor.sendToPlayer(player, new DamageRecapPayload(data));
    }
}
