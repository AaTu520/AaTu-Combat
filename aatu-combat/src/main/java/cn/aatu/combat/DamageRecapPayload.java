package cn.aatu.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DamageRecapPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<DamageRecapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CombatMod.MOD_ID, "damage_recap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DamageRecapPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, DamageRecapPayload::data, DamageRecapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
