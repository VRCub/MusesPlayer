package com.puddingkc.muses.render;

import com.puddingkc.muses.input.Interaction;
import com.puddingkc.muses.media.Media;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public final class ScreenHud {
    private ScreenHud() {
    }

    private static final int BG = 0x99000000;
    private static final int TRACK = 0x66FFFFFF;
    private static final int FILL = 0xFFE2B15A;
    private static final int KNOB = 0xFFFFFFFF;
    private static final int LIVE = 0xFF99423C;
    private static final int TEXT = 0xFFFFFFFF;

    private record Panel(int x, int y, int w, int h, String title, String clock, int clockW, boolean live, float ratio) {
    }

    public static void render(DrawContext context, RenderTickCounter tick) {
        if (!Theater.authenticated || !Settings.INSTANCE.screenHud) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden) {
            return;
        }
        Stage aimed = Interaction.looking;
        if (aimed == null) {
            return;
        }
        Stage src = aimed.sourceStage();
        Projector projector = src.projector();
        Media media = src.current();
        if (projector == null || media == null) {
            return;
        }
        Panel panel = layout(context, mc.textRenderer, media, projector);
        paint(context, mc.textRenderer, panel);
    }

    private static Panel layout(DrawContext context, TextRenderer tr, Media media, Projector projector) {
        long pos = projector.progress();
        long total = projector.total();
        boolean live = total <= 0L;
        String title = media.title() == null ? "" : media.title();
        String clock = clockText(pos, total, live);
        int maxW = Math.min(context.getScaledWindowWidth() - 20, 360);
        int clockW = tr.getWidth(clock);
        int titleAvail = Math.max(20, maxW - 12 - clockW - 8);
        String shown = ellipsize(tr, title, titleAvail);
        int contentW = tr.getWidth(shown) + 8 + clockW;
        int boxW = Math.min(maxW, Math.max(120, contentW + 12));
        float ratio = live ? 0f : Math.clamp((float) pos / total, 0f, 1f);
        return new Panel(6, 6, boxW, 30, shown, clock, clockW, live, ratio);
    }

    private static String clockText(long pos, long total, boolean live) {
        if (live) {
            return format(pos, pos >= 3600000L) + " / 直播";
        }
        boolean hours = total >= 3600000L;
        return format(pos, hours) + " / " + format(total, hours);
    }

    private static void paint(DrawContext context, TextRenderer tr, Panel panel) {
        int x = panel.x;
        int y = panel.y;
        context.fill(x, y, x + panel.w, y + panel.h, BG);
        context.drawTextWithShadow(tr, Text.literal(panel.title), x + 6, y + 5, TEXT);
        context.drawTextWithShadow(tr, Text.literal(panel.clock), x + panel.w - 6 - panel.clockW, y + 5, 0xFFCCCCCC);
        int trackY = y + panel.h - 8;
        int x1 = x + 6;
        int x2 = x + panel.w - 6;
        if (panel.live) {
            context.fill(x1, trackY, x2, trackY + 3, LIVE);
            return;
        }
        context.fill(x1, trackY, x2, trackY + 3, TRACK);
        int fillW = Math.round((x2 - x1) * panel.ratio);
        context.fill(x1, trackY, x1 + fillW, trackY + 3, FILL);
        int knob = x1 + fillW;
        context.fill(knob - 1, trackY - 2, knob + 2, trackY + 5, KNOB);
    }

    private static String ellipsize(TextRenderer tr, String text, int avail) {
        if (text.isEmpty() || tr.getWidth(text) <= avail) {
            return text;
        }
        String ell = "…";
        int budget = Math.max(0, avail - tr.getWidth(ell));
        StringBuilder out = new StringBuilder();
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int cp = text.codePointAt(index);
            String glyph = new String(Character.toChars(cp));
            int glyphW = tr.getWidth(glyph);
            if (used + glyphW > budget) {
                break;
            }
            used += glyphW;
            out.append(glyph);
            index += Character.charCount(cp);
        }
        return out.append(ell).toString();
    }

    private static String format(long millis, boolean showHour) {
        long all = Math.max(0L, millis) / 1000L;
        long h = all / 3600L;
        long m = (all % 3600L) / 60L;
        long s = all % 60L;
        if (showHour) {
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }
}
