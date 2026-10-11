package com.puddingkc.muses.ui;

import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Theater;
import com.puddingkc.muses.world.Venue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class CreateSelector {
    private CreateSelector() {
    }

    public enum Mode {NONE, VENUE, SCREEN}

    private static Mode mode = Mode.NONE;
    private static final List<Vector3f> points = new ArrayList<>();
    private static boolean pendingVenue = false;
    private static boolean pendingScreen = false;

    private static final long UNDO_COOLDOWN_MS = 250;
    private static long lastUndo = 0;

    public static Mode mode() {
        return mode;
    }

    public static boolean active() {
        return mode != Mode.NONE;
    }

    public static List<Vector3f> points() {
        return points;
    }

    public static boolean hasPendingVenue() {
        return pendingVenue;
    }

    public static boolean hasPendingScreen() {
        return pendingScreen;
    }

    public static void beginVenue() {
        enter(Mode.VENUE, "已进入观影区选择模式，左键选取两个对角方块，右键撤销");
    }

    public static void beginScreen() {
        enter(Mode.SCREEN, "已进入屏幕选择模式，逆时针左键选取屏幕四角，右键撤销");
    }

    public static void pick() {
        if (!active()) return;
        BlockPos block = aimedBlock();
        if (block == null) return;
        Vector3f spot = new Vector3f(block.getX(), block.getY(), block.getZ());
        if (mode == Mode.VENUE) {
            placeVenue(spot);
            return;
        }
        if (mode == Mode.SCREEN) placeScreen(spot);
    }

    public static void undo() {
        if (!active()) return;
        if (!takeUndoTurn()) return;
        if (!points.isEmpty()) points.removeLast();
        pendingVenue = false;
        pendingScreen = false;
        Messages.info("已撤销上一个选点");
    }

    public static void confirmVenue(String name) {
        if (!pendingVenue || points.size() < 2) return;
        Outbound.venueAdd(points.get(0), points.get(1), name);
        reset();
    }

    public static void confirmScreen(String venueLabel, String screenName, String source) {
        if (!pendingScreen || points.size() < 4) return;
        String venue = venueLabel.isEmpty() ? currentVenueLabel() : venueLabel;
        String label = screenName.isEmpty() ? "屏幕" : screenName;
        String parent = source == null ? "" : source.trim();
        Outbound.stageAdd(venue, label, points.get(0), points.get(1), points.get(2), points.get(3), parent);
        reset();
    }

    public static void cancel() {
        reset();
        Messages.warn("已退出创建模式");
    }

    public static void reset() {
        mode = Mode.NONE;
        points.clear();
        pendingVenue = false;
        pendingScreen = false;
    }

    public static String statusText() {
        if (mode == Mode.VENUE) return "观影区选择中 [" + points.size() + "/2] 左键选取，右键撤销";
        if (mode == Mode.SCREEN) return "屏幕选择中 [" + points.size() + "/4] 左键选取，右键撤销";
        return "";
    }

    private static void enter(Mode next, String tip) {
        reset();
        mode = next;
        closeMenu();
        Messages.tip(tip);
    }

    private static BlockPos aimedBlock() {
        MinecraftClient client = MinecraftClient.getInstance();
        HitResult look = client.crosshairTarget;
        if (look == null || look.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) look).getBlockPos();
    }

    private static void placeVenue(Vector3f spot) {
        if (points.size() >= 2) points.set(1, spot);
        else points.add(spot);
        if (points.size() != 2) return;
        pendingVenue = true;
        Messages.ok("已确认观影区范围，再次按快捷键打开菜单确认创建");
    }

    private static void placeScreen(Vector3f spot) {
        if (points.size() < 4) points.add(spot);
        if (points.size() != 4) return;
        pendingScreen = true;
        Messages.ok("已确认屏幕范围，再次按快捷键打开菜单确认创建");
    }

    private static boolean takeUndoTurn() {
        long now = System.currentTimeMillis();
        if (now - lastUndo < UNDO_COOLDOWN_MS) return false;
        lastUndo = now;
        return true;
    }

    private static String currentVenueLabel() {
        for (Venue venue : Theater.venues.values()) {
            if (venue.loaded) return venue.label;
        }
        return "观影区";
    }

    private static void closeMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) return;
        client.setScreen(null);
    }
}
