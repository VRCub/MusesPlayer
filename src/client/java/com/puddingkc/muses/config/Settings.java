package com.puddingkc.muses.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.puddingkc.muses.MusesPlayer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Settings {
    public int volume = 100;
    public int brightness = 100;
    public boolean worldProgressBar = false;
    public boolean screenHud = true;
    public boolean danmaku = false;

    public boolean dynamicLight = false;
    public int dynamicLightStrength = 1;

    public boolean spatialAudio = false;
    public int audioRange = 32;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("muses-player.json");

    public static Settings INSTANCE = new Settings();

    public static void load() {
        Settings parsed;
        try {
            parsed = GSON.fromJson(Files.readString(PATH), Settings.class);
        } catch (Exception e) {
            parsed = null;
        }
        if (parsed != null) {
            INSTANCE = parsed;
            return;
        }
        INSTANCE = new Settings();
        save();
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            MusesPlayer.LOGGER.warn("settings write failed", e);
        }
    }
}
