package com.puddingkc.muses.net;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record Channel(byte[] data) implements CustomPayload {
    public static final Identifier CHANNEL_ID = Identifier.of("muses", "net");
    public static final CustomPayload.Id<Channel> ID = new CustomPayload.Id<>(CHANNEL_ID);

    public static final PacketCodec<PacketByteBuf, Channel> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeBytes(payload.data()),
            buf -> new Channel(drain(buf))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static byte[] drain(ByteBuf buf) {
        int size = buf.readableBytes();
        byte[] bytes = new byte[size];
        buf.readBytes(bytes, 0, size);
        return bytes;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }
}
