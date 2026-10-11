package com.puddingkc.muses.net;

import com.puddingkc.muses.world.Stage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.joml.Vector3f;

import java.util.function.Consumer;

public final class Outbound {
    private Outbound() {
    }

    private static void emit(int op, Consumer<ByteBuf> fill) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
        buf.writeByte(op);
        fill.accept(buf);
        byte[] raw = new byte[buf.readableBytes()];
        buf.readBytes(raw);
        buf.release();
        ClientPlayNetworking.send(new Channel(raw));
    }

    private static void stageRef(ByteBuf buf, Stage stage) {
        Buffers.writeText(buf, stage.venue.label);
        Buffers.writeText(buf, stage.label);
    }

    private static void corners(ByteBuf buf, Vector3f c1, Vector3f c2, Vector3f c3, Vector3f c4) {
        Buffers.writeVec(buf, c1);
        Buffers.writeVec(buf, c2);
        Buffers.writeVec(buf, c3);
        Buffers.writeVec(buf, c4);
    }

    public static void hello(String version) {
        emit(Protocol.HELLO, buf -> Buffers.writeText(buf, version));
    }

    public static void authChallenge(byte[] nonce) {
        emit(Protocol.AUTH, buf -> Buffers.writeBlob(buf, nonce));
    }

    public static void enqueue(Stage stage, String url) {
        emit(Protocol.ENQUEUE, buf -> {
            stageRef(buf, stage);
            Buffers.writeText(buf, url);
        });
    }

    public static void sync(Stage stage) {
        emit(Protocol.SYNC, buf -> stageRef(buf, stage));
    }

    public static void venueAdd(Vector3f a, Vector3f b, String name) {
        emit(Protocol.VENUE_ADD, buf -> {
            Buffers.writeVec(buf, a);
            Buffers.writeVec(buf, b);
            Buffers.writeText(buf, name);
        });
    }

    public static void venueDel(String name) {
        emit(Protocol.VENUE_DEL, buf -> Buffers.writeText(buf, name));
    }

    public static void stageAdd(String venueLabel, String label, Vector3f c1, Vector3f c2,
                                Vector3f c3, Vector3f c4, String parent) {
        emit(Protocol.STAGE_ADD, buf -> {
            Buffers.writeText(buf, venueLabel);
            Buffers.writeText(buf, label);
            corners(buf, c1, c2, c3, c4);
            Buffers.writeText(buf, parent);
        });
    }

    public static void stageDel(Stage stage) {
        emit(Protocol.STAGE_DEL, buf -> stageRef(buf, stage));
    }

    public static void skip(Stage stage, boolean force) {
        emit(Protocol.SKIP, buf -> {
            stageRef(buf, stage);
            buf.writeBoolean(force);
        });
    }

    public static void skipRatio(Stage stage, float ratio) {
        emit(Protocol.SKIP_RATIO, buf -> {
            stageRef(buf, stage);
            buf.writeFloat(ratio);
        });
    }

    public static void idle(Stage stage, String url) {
        emit(Protocol.IDLE, buf -> {
            stageRef(buf, stage);
            Buffers.writeText(buf, url);
        });
    }

    public static void uv(Stage stage, float su, float sv, float eu, float ev) {
        emit(Protocol.UV, buf -> {
            stageRef(buf, stage);
            buf.writeFloat(su);
            buf.writeFloat(sv);
            buf.writeFloat(eu);
            buf.writeFloat(ev);
        });
    }

    public static void flag(Stage stage, int flagId, int value) {
        emit(Protocol.FLAG, buf -> {
            buf.writeByte(flagId);
            stageRef(buf, stage);
            buf.writeInt(value);
        });
    }

    public static void flagCustom(Stage stage, String key, int value, boolean drop) {
        emit(Protocol.FLAG_CUSTOM, buf -> {
            stageRef(buf, stage);
            Buffers.writeText(buf, key);
            buf.writeInt(value);
            buf.writeBoolean(drop);
        });
    }

    public static void scale(Stage stage, boolean stretch, float sx, float sy) {
        emit(Protocol.SCALE, buf -> {
            stageRef(buf, stage);
            buf.writeBoolean(stretch);
            buf.writeFloat(sx);
            buf.writeFloat(sy);
        });
    }

    public static void seek(Stage stage, long progress) {
        emit(Protocol.SEEK, buf -> {
            stageRef(buf, stage);
            buf.writeLong(progress);
        });
    }

    public static void rate(Stage stage, float r) {
        emit(Protocol.RATE, buf -> {
            stageRef(buf, stage);
            buf.writeFloat(r);
        });
    }

    public static void move(Stage stage, Vector3f c1, Vector3f c2, Vector3f c3, Vector3f c4) {
        emit(Protocol.MOVE, buf -> {
            stageRef(buf, stage);
            corners(buf, c1, c2, c3, c4);
        });
    }

    public static void perm(Stage stage, String node) {
        perm(stage.venue.label, stage.label, node);
    }

    public static void perm(String venueLabel, String stageLabel, String node) {
        String venue = venueLabel == null ? "" : venueLabel;
        String stage = stageLabel == null ? "" : stageLabel;
        emit(Protocol.PERM, buf -> {
            Buffers.writeText(buf, venue);
            Buffers.writeText(buf, stage);
            Buffers.writeText(buf, node);
        });
    }
}
