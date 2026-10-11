package com.puddingkc.muses.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Theater {
    private Theater() {
    }

    public static final Map<String, Venue> venues = new LinkedHashMap<>();
    public static final List<Stage> screens = new ArrayList<>();

    public static final Map<String, Boolean> perms = new HashMap<>();

    public static boolean connected = false;
    public static boolean authenticated = false;
    public static byte[] authNonce = null;

    public static void addScreen(Stage stage) {
        screens.add(stage);
    }

    public static void removeScreen(Stage stage) {
        screens.remove(stage);
    }

    public static void reset() {
        connected = false;
        authenticated = false;
        authNonce = null;
        perms.clear();
        dropVenues();
        dropScreens();
    }

    private static void dropVenues() {
        for (Venue venue : venues.values()) venue.dispose();
        venues.clear();
    }

    private static void dropScreens() {
        for (Stage stage : screens) stage.cleanup();
        screens.clear();
    }
}
