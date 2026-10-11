package com.puddingkc.muses.danmaku;

public record Bullet(long timeMs, int type, int color, String text) {
    public boolean scroll() {
        return switch (type) {
            case 1, 2, 3 -> true;
            default -> false;
        };
    }

    public boolean bottom() {
        if (type != 4) return false;
        return true;
    }

    public boolean top() {
        return switch (type) {
            case 5 -> true;
            default -> false;
        };
    }
}
