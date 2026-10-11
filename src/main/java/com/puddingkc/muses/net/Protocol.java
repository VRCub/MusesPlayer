package com.puddingkc.muses.net;

public final class Protocol {
    public static final String PROTOCOL_VERSION = "1.2";

    public static final int HELLO = 0;
    public static final int AUTH = 1;
    public static final int ENQUEUE = 2;
    public static final int SYNC = 3;
    public static final int VENUE_ADD = 4;
    public static final int VENUE_DEL = 5;
    public static final int STAGE_ADD = 6;
    public static final int STAGE_DEL = 7;
    public static final int VENUE_ENTER = 8;
    public static final int VENUE_LEAVE = 9;
    public static final int QUEUE = 10;
    public static final int SKIP = 11;
    public static final int SKIP_RATIO = 12;
    public static final int RELAY = 13;
    public static final int IDLE = 14;
    public static final int UV = 15;
    public static final int PANEL = 16;
    public static final int FLAG = 17;
    public static final int FLAG_CUSTOM = 18;
    public static final int SCALE = 19;
    public static final int SEEK = 21;
    public static final int MOVE = 22;
    public static final int PERM = 23;
    public static final int RATE = 24;

    private Protocol() {
    }

    public enum Flag {
        MUTE("mute"),
        INTERACTABLE("interactable"),
        ASPECT("aspect"),
        FOV("fov");

        public static final Flag[] ALL = values();

        public final String key;

        Flag(String key) {
            this.key = key;
        }
    }
}
