package cn.aatu.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CombatUiPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<CombatUiPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("aatu_combat", "ui_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatUiPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, CombatUiPayload::data, CombatUiPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
