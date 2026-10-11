package com.puddingkc.muses.net;

import io.netty.buffer.ByteBuf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;

public final class Buffers {
    private Buffers() {
    }

    public static String readText(ByteBuf in, int cap) {
        return new String(readCounted(in, cap, "text"), StandardCharsets.UTF_8);
    }

    public static void writeText(ByteBuf out, String value) {
        writeCounted(out, value.getBytes(StandardCharsets.UTF_8));
    }

    public static Vector3f readVec(ByteBuf in) {
        float x = in.readFloat();
        float y = in.readFloat();
        float z = in.readFloat();
        return new Vector3f(x, y, z);
    }

    public static void writeVec(ByteBuf out, Vector3f vec) {
        float[] xyz = {vec.x, vec.y, vec.z};
        for (float n : xyz) out.writeFloat(n);
    }

    public static byte[] readBlob(ByteBuf in, int cap) {
        return readCounted(in, cap, "blob");
    }

    public static void writeBlob(ByteBuf out, byte[] data) {
        writeCounted(out, data);
    }

    private static byte[] readCounted(ByteBuf in, int cap, String kind) {
        int size = in.readUnsignedShort();
        if (size > cap) {
            throw new IllegalStateException(kind + " length " + size + " exceeds cap " + cap);
        }
        byte[] raw = new byte[size];
        in.readBytes(raw);
        return raw;
    }

    private static void writeCounted(ByteBuf out, byte[] raw) {
        out.writeShort(raw.length);
        out.writeBytes(raw);
    }
}
