package com.puddingkc.muses.vr;

import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;

public final class Vr {
    public static final boolean loaded = FabricLoader.getInstance().isModLoaded("vivecraft");

    private Vr() {
    }

    public static boolean active() {
        if (!loaded) return false;
        return VrBridge.active();
    }

    public static boolean rightEye() {
        if (!loaded) return false;
        return VrBridge.rightEye();
    }

    public static Matrix4f rotation() {
        return VrBridge.rotation();
    }
}
