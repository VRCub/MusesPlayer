package com.puddingkc.muses.media;

import com.puddingkc.muses.MusesPlayer;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;

public final class VlcEngine {
    private VlcEngine() {
    }

    private static MediaPlayerFactory factory;
    private static Throwable error;

    public static void load() {
        try {
            MusesPlayer.LOGGER.info("VLC 启动中");
            MediaPlayerFactory created = new MediaPlayerFactory(
                    "--no-video-title-show",
                    "--audio-filter=scaletempo");
            factory = created;
            MusesPlayer.LOGGER.info("VLC 已就绪");
        } catch (Throwable failed) {
            error = failed;
            MusesPlayer.LOGGER.error("VLC 启动失败", failed);
        }
    }

    public static MediaPlayerFactory factory() {
        return factory;
    }

    public static boolean ready() {
        return factory != null;
    }

    public static Throwable error() {
        return error;
    }
}
