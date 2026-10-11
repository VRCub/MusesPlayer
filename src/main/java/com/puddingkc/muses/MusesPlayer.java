package com.puddingkc.muses;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusesPlayer implements ModInitializer {
    public static final String MOD_ID = "museplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger("Muses Player");

    public static final String VERSION = readVersion();

    private static String readVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .toString();
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Muses Player {} 已加载", VERSION);
    }
}
