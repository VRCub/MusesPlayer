package com.puddingkc.muses.net;

import com.puddingkc.muses.MusesPlayer;
import com.puddingkc.muses.input.DragController;
import com.puddingkc.muses.media.Media;
import com.puddingkc.muses.ui.MusesScreen;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import com.puddingkc.muses.world.Venue;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

public final class Inbound {
    private interface Handler {
        void read(ByteBuf in);
    }

    private static final Handler[] ROUTES = new Handler[256];

    static {
        ROUTES[Protocol.RATE] = Inbound::onRate;
        ROUTES[Protocol.PERM] = Inbound::onPerm;
        ROUTES[Protocol.MOVE] = Inbound::onMove;
        ROUTES[Protocol.SCALE] = Inbound::onScale;
        ROUTES[Protocol.FLAG_CUSTOM] = Inbound::onFlagCustom;
        ROUTES[Protocol.FLAG] = Inbound::onFlag;
        ROUTES[Protocol.UV] = Inbound::onUv;
        ROUTES[Protocol.SKIP] = Inbound::onSkip;
        ROUTES[Protocol.QUEUE] = Inbound::onQueue;
        ROUTES[Protocol.VENUE_LEAVE] = Inbound::onVenueLeave;
        ROUTES[Protocol.VENUE_ENTER] = Inbound::onVenueEnter;
        ROUTES[Protocol.STAGE_DEL] = Inbound::onStageDel;
        ROUTES[Protocol.STAGE_ADD] = Inbound::onStageAdd;
        ROUTES[Protocol.VENUE_DEL] = Inbound::onVenueDel;
        ROUTES[Protocol.VENUE_ADD] = Inbound::onVenueAdd;
        ROUTES[Protocol.SYNC] = Inbound::onSync;
        ROUTES[Protocol.ENQUEUE] = Inbound::onEnqueue;
        ROUTES[Protocol.AUTH] = Inbound::onAuth;
        ROUTES[Protocol.HELLO] = Inbound::onHello;
    }

    private Inbound() {
    }

    public static void handle(ByteBuf in) {
        int op = in.readUnsignedByte();
        Handler handler = ROUTES[op];
        if (handler == null) {
            MusesPlayer.LOGGER.warn("未知 opcode: {}", op);
        } else {
            handler.read(in);
        }
        int leftover = in.readableBytes();
        if (leftover > 0) {
            MusesPlayer.LOGGER.warn("opcode {} 处理后残留 {} 字节", op, leftover);
        }
    }

    private static void onHello(ByteBuf in) {
        String version = Buffers.readText(in, 16);
        if (!checkVersion(version)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(
                        "Muses Player 版本与服务端不匹配，请更新模组后重试，本地协议 " + Protocol.PROTOCOL_VERSION
                                + "，服务端 " + version).formatted(Formatting.RED), false);
            }
            return;
        }
        Theater.connected = true;
        Outbound.hello(MusesPlayer.VERSION);
        byte[] nonce = Crypto.newNonce();
        Theater.authNonce = nonce;
        Outbound.authChallenge(nonce);
    }

    private static void onAuth(ByteBuf in) {
        byte[] signature = Buffers.readBlob(in, 256);
        byte[] nonce = Theater.authNonce;
        boolean ok = Crypto.verify(nonce, signature);
        Theater.authenticated = ok;
        Theater.authNonce = null;
        if (!ok) {
            MusesPlayer.LOGGER.warn("鉴权失败：服务端签名未通过校验，功能已禁用");
        }
    }

    private static void onEnqueue(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        Media media = Media.read(in);
        if (stage == null) return;
        stage.setPendingRate(1.0f);
        stage.play(media);
    }

    private static void onSync(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        long progress = in.readLong();
        if (stage == null) return;
        stage.setProgress(progress);
    }

    private static void onRate(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        float rate = in.readFloat();
        if (stage == null) return;
        stage.setRate(rate);
    }

    private static void onVenueAdd(ByteBuf in) {
        String name = label(in);
        Venue venue = Venue.read(name, in);
        Theater.venues.put(name, venue);
    }

    private static void onVenueDel(ByteBuf in) {
        Venue venue = Theater.venues.remove(label(in));
        if (venue == null) return;
        if (venue.loaded) venue.unload();
        venue.dispose();
    }

    private static void onStageAdd(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        int count = in.readUnsignedByte();
        for (int i = 0; i < count; i++) {
            Stage stage = Stage.read(in, venue);
            stage.readUv(in);
            stage.readScale(in);
            stage.readFlags(in);
            venue.attach(stage);
        }
    }

    private static void onStageDel(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = venue.detach(label(in));
        if (stage != null) stage.cleanup();
    }

    private static void onVenueEnter(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        while (in.readableBytes() > 0) {
            String name = label(in);
            Media media = Media.read(in);
            long position = in.readLong();
            float rate = in.readFloat();
            Stage stage = venue.stage(name);
            if (stage == null) continue;
            stage.setPending(media);
            if (position >= 0) stage.setPendingSeek(position);
            stage.setPendingRate(rate);
        }
        venue.load();
    }

    private static void onVenueLeave(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        venue.unload();
    }

    private static void onQueue(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        int stageCount = in.readUnsignedByte();
        for (int i = 0; i < stageCount; i++) {
            Stage stage = findStage(venue, in);
            int itemCount = in.readUnsignedByte();
            Media[] items = new Media[itemCount];
            for (int j = 0; j < itemCount; j++) {
                String requester = Buffers.readText(in, 256);
                String title = Buffers.readText(in, 256);
                items[j] = Media.label(requester, title);
            }
            if (stage != null) stage.setQueue(items);
        }
    }

    private static void onSkip(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        if (stage == null) return;
        stage.stop();
    }

    private static void onUv(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        if (stage == null) {
            in.skipBytes(16);
            return;
        }
        stage.readUv(in);
    }

    private static void onFlag(ByteBuf in) {
        int id = in.readUnsignedByte();
        Venue venue = findVenue(in);
        if (venue == null) {
            label(in);
            in.skipBytes(4);
            return;
        }
        Stage stage = findStage(venue, in);
        int value = in.readInt();
        if (stage == null || id >= Protocol.Flag.ALL.length) return;
        stage.flags.put(Protocol.Flag.ALL[id].key, value);
        stage.metaChanged();
    }

    private static void onFlagCustom(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) {
            label(in);
            label(in);
            in.skipBytes(5);
            return;
        }
        Stage stage = findStage(venue, in);
        String key = label(in);
        int value = in.readInt();
        boolean drop = in.readBoolean();
        if (stage == null) return;
        if (drop) stage.flags.remove(key);
        else stage.flags.put(key, value);
        stage.metaChanged();
    }

    private static void onScale(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        if (stage == null) {
            in.skipBytes(9);
            return;
        }
        stage.readScale(in);
    }

    private static void onMove(ByteBuf in) {
        Venue venue = findVenue(in);
        if (venue == null) return;
        Stage stage = findStage(venue, in);
        Vector3f c1 = Buffers.readVec(in);
        Vector3f c2 = Buffers.readVec(in);
        Vector3f c3 = Buffers.readVec(in);
        Vector3f c4 = Buffers.readVec(in);
        if (stage == null) return;
        DragController.onAuthoritativeMove(stage, c1, c2, c3, c4);
    }

    private static void onPerm(ByteBuf in) {
        String venueLabel = label(in);
        String stageLabel = label(in);
        String node = Buffers.readText(in, 64);
        boolean allowed = in.readBoolean();
        Venue venue = venueLabel.isEmpty() ? null : Theater.venues.get(venueLabel);
        Stage stage = (venue == null || stageLabel.isEmpty()) ? null : venue.stage(stageLabel);
        if (stage != null) stage.perms.put(node, allowed);
        else Theater.perms.put(node, allowed);
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen instanceof MusesScreen muses) muses.onPermUpdate(node);
    }

    private static Venue findVenue(ByteBuf in) {
        return Theater.venues.get(label(in));
    }

    private static Stage findStage(Venue venue, ByteBuf in) {
        return venue.stage(label(in));
    }

    private static String label(ByteBuf in) {
        return Buffers.readText(in, Stage.MAX_LABEL_LENGTH);
    }

    public static boolean checkVersion(String remote) {
        String[] remoteParts = remote.split("\\.");
        String[] localParts = Protocol.PROTOCOL_VERSION.split("\\.");
        if (remoteParts.length < 2 || localParts.length < 2) return false;
        return remoteParts[0].equals(localParts[0]) && remoteParts[1].equals(localParts[1]);
    }
}
