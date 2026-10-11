package com.puddingkc.muses.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class Style {
    private Style() {
    }

    public static final int PANEL_BG = 0xCC1a1a1a;
    public static final int PANEL_BORDER = 0x55bdbdbd;
    public static final int INSET_BG = 0x55000000;
    public static final int PREVIEW_BG = 0xE6000000;
    public static final int PREVIEW_BORDER = 0x44FFFFFF;

    public static final int TITLE = 0xFFF0F0F0;
    public static final int SUBTITLE = 0xFF9AA0AA;
    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_DIM = 0xFF9AA0AA;
    public static final int TEXT_FAINT = 0xFF6B7079;
    public static final int ACCENT = 0xFFE2B15A;

    public static final int TRACK = 0xFF555560;
    public static final int FILL = 0xFFE2B15A;
    public static final int KNOB = 0xFFFFFFFF;
    public static final int LIVE = 0xFF99423C;

    public static final int MASK = 0x99000000;
    public static final int SEL_BORDER = 0xFF55FF55;
    public static final int SEL_BORDER_ACTIVE = 0xFFFFFF55;
    public static final int HANDLE = 0xFFFFFFFF;

    public static final int CTRL_BG = 0x33919191;
    public static final int CTRL_BG_HOVER = 0x55919191;
    public static final int CTRL_BG_DISABLED = 0x18919191;
    public static final int CTRL_BORDER = 0x44919191;
    public static final int CTRL_BORDER_HOVER = 0x99919191;
    public static final int CTRL_TEXT_DISABLED = 0xFF636363;
    public static final int CTRL_TRACK = 0x40FFFFFF;
    public static final int CTRL_FILL = 0x66FFFFFF;
    public static final int CTRL_KNOB = 0xCCFFFFFF;

    public static void panel(DrawContext context, int x, int y, int w, int h) {
        paint(context, x, y, w, h, PANEL_BG, PANEL_BORDER);
    }

    public static void inset(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, INSET_BG);
    }

    public static void flatRect(DrawContext context, int x, int y, int w, int h, boolean hovered, boolean active) {
        paint(context, x, y, w, h, fillOf(hovered, active), edgeOf(hovered, active));
    }

    public static ButtonWidget button(Text text, int x, int y, int w, int h, ButtonWidget.PressAction onPress) {
        return new FlatButton(text, x, y, w, h, onPress);
    }

    private static void paint(DrawContext context, int x, int y, int w, int h, int fill, int edge) {
        context.fill(x, y, x + w, y + h, fill);
        context.drawBorder(x, y, w, h, edge);
    }

    private static int fillOf(boolean hovered, boolean active) {
        if (!active) return CTRL_BG_DISABLED;
        if (hovered) return CTRL_BG_HOVER;
        return CTRL_BG;
    }

    private static int edgeOf(boolean hovered, boolean active) {
        if (active && hovered) return CTRL_BORDER_HOVER;
        return CTRL_BORDER;
    }
}
