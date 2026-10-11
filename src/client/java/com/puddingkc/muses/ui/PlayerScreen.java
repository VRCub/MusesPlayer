package com.puddingkc.muses.ui;

import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.input.Interaction;
import com.puddingkc.muses.media.Media;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import com.puddingkc.muses.world.Venue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerScreen extends Screen {
    private Stage target;
    private FlatTextField urlField;

    private int panelX, panelY, panelW, panelH;
    private int queueX, queueY, queueW, queueH;
    private int mainX, mainW;
    private int previewX, previewY, previewW, previewH;
    private int queueScroll = 0;

    private static final float[] RATE_STEPS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f};
    private final Set<String> permQueried = new HashSet<>();

    public PlayerScreen(Stage target) {
        super(Text.literal("Muses Player"));
        this.target = target;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Stage target() {
        if (target == null) return null;
        return target.sourceStage();
    }

    @Override
    protected void init() {
        if (target == null) target = fallbackScreen();
        layoutFrame();
        rebuild();
    }

    private void layoutFrame() {
        int margin = 8;
        panelW = Math.min(440, width - 20);
        panelH = Math.min(248, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int contentTop = panelY + 22;
        queueX = panelX + margin;
        queueY = contentTop + 12;
        queueW = 110;
        queueH = panelY + panelH - margin - queueY;
        mainX = queueX + queueW + margin;
        mainW = panelX + panelW - margin - mainX;
        previewX = mainX;
        previewY = queueY;
        previewW = mainW;
        previewH = Math.clamp(panelH - 168, 30, 76);
    }

    private void rebuild() {
        clearChildren();
        Stage screen = target();
        addDrawableChild(Style.button(Text.literal("全屏"), previewX + previewW - 40, previewY, 40, 14, b -> openFullscreen()));
        int cursor = previewY + previewH + 6;
        addDrawableChild(new SeekWidget(previewX, cursor, previewW, 16));
        cursor += 20;
        placeUrlRow(cursor);
        cursor += 22;
        addDrawableChild(new VolumeSlider(previewX, cursor, previewW, 16));
        cursor += 18;
        addDrawableChild(new BrightnessSlider(previewX, cursor, previewW, 16));
        cursor += 18;
        placeTransport(screen, cursor);
        cursor += 24;
        placeFooter(cursor);
    }

    private void placeUrlRow(int y) {
        urlField = new FlatTextField(textRenderer, previewX, y, previewW - 50, 18, Text.literal(""));
        urlField.setMaxLength(512);
        urlField.setPlaceholder(Text.literal("输入视频/直播/图片链接...").formatted(Formatting.GRAY));
        addDrawableChild(urlField);
        addDrawableChild(Style.button(Text.literal("发送"), previewX + previewW - 44, y, 44, 18, b -> enqueue()));
    }

    private void placeTransport(Stage screen, int y) {
        int third = (previewW - 12) / 3;
        ButtonWidget skipButton = Style.button(Text.literal("跳过"), previewX, y, third, 18, b -> {
            if (screen == null) return;
            Outbound.skip(screen, Screen.hasShiftDown());
        });
        skipButton.setTooltip(Tooltip.of(Text.literal("点击投票跳过，或使用 Shift+点击 强制跳过")));
        addDrawableChild(skipButton);
        addDrawableChild(Style.button(Text.literal("重新同步"), previewX + third + 6, y, third, 18, b -> {
            if (screen == null) return;
            Outbound.sync(screen);
        }));
        queryRate(screen);
        RateWidget rate = new RateWidget(previewX + 2 * (third + 6), y, previewW - 2 * (third + 6), 18);
        rate.setTooltip(Tooltip.of(Text.literal("点击循环切换倍速 [0.5x~3x]")));
        addDrawableChild(rate);
    }

    private void queryRate(Stage screen) {
        if (screen == null) return;
        if (!permQueried.add(screen.label)) return;
        Outbound.perm(screen, "stage.rate");
    }

    private void placeFooter(int y) {
        int half = (mainW - 6) / 2;
        addDrawableChild(Style.button(Text.literal("设置"), mainX, y, half, 18, b ->
                client.setScreen(new GeneralTabScreen(this))));
        addDrawableChild(Style.button(Text.literal("管理"), mainX + half + 6, y, mainW - half - 6, 18, b ->
                client.setScreen(new ManageHubScreen(this, target))));
    }

    private void enqueue() {
        Stage screen = target();
        if (screen == null || urlField == null) return;
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        Outbound.enqueue(screen, url);
        urlField.setText("");
    }

    private void openFullscreen() {
        if (client == null) return;
        client.setScreen(new FullscreenScreen(this, target()));
    }

    private static Stage fallbackScreen() {
        if (Interaction.current != null) return Interaction.current;
        for (Venue venue : Theater.venues.values()) {
            if (!venue.loaded) continue;
            for (Stage screen : venue.stages()) {
                if (screen.interactable()) return screen;
            }
        }
        return null;
    }

    @Override
    public void removed() {
        Settings.save();
        super.removed();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Style.panel(context, panelX, panelY, panelW, panelH);
        Style.inset(context, queueX, queueY, queueW, queueH);
        context.fill(previewX, previewY, previewX + previewW, previewY + previewH, Style.PREVIEW_BG);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Stage screen = target();
        paintHeader(context, screen);
        context.drawTextWithShadow(textRenderer, Text.literal("播放队列"), queueX, queueY - 11, Style.TEXT_DIM);
        renderQueue(context, screen);
        renderPreview(context, screen);
    }

    private void paintHeader(DrawContext context, Stage screen) {
        context.drawTextWithShadow(textRenderer, Text.literal("Muses Player"), panelX + 8, panelY + 7, Style.TITLE);
        if (screen == null) return;
        String info = "屏幕: " + screen.label + "  观影区: " + screen.venue.label;
        int titleW = textRenderer.getWidth("Muses Player");
        int maxInfoW = panelW - 16 - titleW - 8;
        String shown = textRenderer.trimToWidth(info, Math.max(0, maxInfoW));
        int iw = textRenderer.getWidth(shown);
        context.drawTextWithShadow(textRenderer, Text.literal(shown), panelX + panelW - 8 - iw, panelY + 7, Style.SUBTITLE);
    }

    private void renderPreview(DrawContext context, Stage t) {
        Projector frame = t == null ? null : t.projector();
        if (!drawable(frame)) {
            paintNoSignal(context);
            return;
        }
        float aspect = (float) frame.width() / frame.height();
        int[] box = Gui.fit(previewX, previewY, previewW, previewH, aspect);
        context.draw();
        Gui.texture(context, frame.textureId(), box[0], box[1], box[2], box[3], frame.flippedX(), frame.flippedY());
        Media info = t.current();
        if (info == null) return;
        context.fill(previewX, previewY + previewH - 11, previewX + previewW, previewY + previewH, pack(0x90, 0, 0, 0));
        String name = textRenderer.trimToWidth(info.title(), previewW - 6);
        context.drawTextWithShadow(textRenderer, Text.literal(name), previewX + 3, previewY + previewH - 10, Style.TEXT);
    }

    private void paintNoSignal(DrawContext context) {
        String none = "无画面";
        int tw = textRenderer.getWidth(none);
        context.drawTextWithShadow(textRenderer, Text.literal(none),
                previewX + (previewW - tw) / 2, previewY + previewH / 2 - 4, Style.TEXT_FAINT);
    }

    private static int pack(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean drawable(Projector frame) {
        return frame != null && frame.textureId() >= 0 && frame.width() > 0 && frame.height() > 0;
    }

    private void renderQueue(DrawContext context, Stage t) {
        if (t == null) return;
        List<Media> items = new ArrayList<>(t.queue());
        int rowH = 11;
        int rows = queueH / rowH;
        if (items.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("队列为空"), queueX + 4, queueY + 4, Style.TEXT_FAINT);
            return;
        }
        queueScroll = Math.clamp(queueScroll, 0, Math.max(0, items.size() - rows));
        int last = Math.min(items.size(), queueScroll + rows);
        for (int idx = queueScroll; idx < last; idx++) paintQueueRow(context, items.get(idx), idx, rowH);
    }

    private void paintQueueRow(DrawContext context, Media item, int idx, int rowH) {
        int rowY = queueY + 3 + (idx - queueScroll) * rowH;
        boolean playing = idx == 0;
        if (playing) context.fill(queueX, rowY - 1, queueX + 2, rowY + 9, Style.ACCENT);
        int color = playing ? Style.TEXT : Style.TEXT_DIM;
        String line = textRenderer.trimToWidth((idx + 1) + ". " + item.title(), queueW - 8);
        context.drawTextWithShadow(textRenderer, Text.literal(line), queueX + 5, rowY, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (!overQueue(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, h, v);
        queueScroll -= (int) Math.signum(v);
        return true;
    }

    private boolean overQueue(double mouseX, double mouseY) {
        return mouseX >= queueX && mouseX < queueX + queueW && mouseY >= queueY && mouseY < queueY + queueH;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (typing()) return false;
        if (!inventoryKey(keyCode, scanCode)) return false;
        close();
        return true;
    }

    private boolean typing() {
        return urlField != null && urlField.isFocused();
    }

    private boolean inventoryKey(int keyCode, int scanCode) {
        return client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode);
    }

    private final class SeekWidget extends ButtonWidget {
        private boolean dragging = false;
        private float previewRatio = 0f;

        SeekWidget(int x, int y, int w, int h) {
            super(x, y, w, h, Text.literal(""), b -> {}, DEFAULT_NARRATION_SUPPLIER);
        }

        private boolean seekable() {
            Stage screen = target();
            Projector frame = screen == null ? null : screen.projector();
            return frame != null && frame.canSeek() && frame.total() > 0;
        }

        private float ratioAt(double mouseX) {
            return Math.clamp((float) (mouseX - getX()) / getWidth(), 0f, 1f);
        }

        private void commit() {
            Stage screen = target();
            Projector frame = screen == null ? null : screen.projector();
            if (frame == null || frame.total() <= 0) return;
            long progress = (long) (previewRatio * frame.total());
            screen.setProgress(progress);
            Outbound.seek(screen, progress);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!seekable()) return;
            dragging = true;
            previewRatio = ratioAt(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dx, double dy) {
            if (!dragging) return;
            previewRatio = ratioAt(mouseX);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            if (dragging) {
                previewRatio = ratioAt(mouseX);
                commit();
                dragging = false;
            }
            super.onRelease(mouseX, mouseY);
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX();
            int top = getY();
            int span = getWidth();
            int tall = getHeight();
            Stage screen = target();
            Projector frame = screen == null ? null : screen.projector();
            long total = frame == null ? 0 : frame.total();
            int trackY = top + tall - 6;
            if (total <= 0) {
                paintIdle(context, left, top, span, trackY, frame, screen);
                return;
            }
            paintSeek(context, left, top, span, trackY, shownPos(frame, total), total);
        }

        private long shownPos(Projector frame, long total) {
            if (dragging && total > 0) return (long) (previewRatio * total);
            if (frame == null) return 0;
            return frame.progress();
        }

        private void paintSeek(DrawContext context, int left, int top, int span, int trackY, long pos, long total) {
            context.fill(left, trackY, left + span, trackY + 4, Style.TRACK);
            float ratio = Math.clamp((float) pos / total, 0f, 1f);
            int filled = (int) (span * ratio);
            context.fill(left, trackY, left + filled, trackY + 4, Style.FILL);
            int knob = left + filled;
            context.fill(knob - 1, trackY - 2, knob + 2, trackY + 6, Style.KNOB);
            context.drawTextWithShadow(textRenderer, Text.literal(fmt(pos) + " / " + fmt(total)), left, top - 1, Style.TEXT);
        }

        private void paintIdle(DrawContext context, int left, int top, int span, int trackY, Projector frame, Stage screen) {
            context.fill(left, trackY, left + span, trackY + 4, Style.LIVE);
            String live = frame != null && screen != null && screen.current() != null ? "直播 / 不可跳转" : "未播放";
            context.drawTextWithShadow(textRenderer, Text.literal(live), left, top - 1, Style.TEXT_DIM);
        }
    }

    private static String fmt(long millis) {
        long all = Math.max(0, millis) / 1000;
        long h = all / 3600;
        long m = (all % 3600) / 60;
        long s = all % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    private final class RateWidget extends FlatButton {
        RateWidget(int x, int y, int w, int h) {
            super(Text.literal(""), x, y, w, h, b -> {});
        }

        private float currentRate() {
            Stage screen = target();
            Projector frame = screen == null ? null : screen.projector();
            if (frame == null) return 1.0f;
            return frame.rate();
        }

        private float nextRate() {
            float cur = currentRate();
            int i = 0;
            while (i < RATE_STEPS.length) {
                float step = RATE_STEPS[i];
                if (step > cur + 1e-4f) return step;
                i++;
            }
            return RATE_STEPS[0];
        }

        private boolean denied() {
            Stage screen = target();
            return screen != null && Boolean.FALSE.equals(screen.perms.get("stage.rate"));
        }

        @Override
        public void onPress() {
            Stage screen = target();
            if (screen == null || denied()) return;
            float next = nextRate();
            screen.setRate(next);
            Outbound.rate(screen, next);
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            this.active = !denied();
            float cur = currentRate();
            String digits = cur == Math.rint(cur) ? String.valueOf((int) cur) : String.valueOf(cur);
            setMessage(Text.literal("倍速: " + digits + "x"));
            super.renderWidget(context, mouseX, mouseY, delta);
        }
    }

    private final class VolumeSlider extends FlatSlider {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.literal(""), Settings.INSTANCE.volume / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("音量: " + Settings.INSTANCE.volume + "%"));
        }

        @Override
        protected void applyValue() {
            int level = (int) Math.round(value * 100);
            Settings.INSTANCE.volume = level;
            Stage screen = target();
            if (screen == null || screen.projector() == null) return;
            screen.projector().setVolume(level);
        }

        @Override
        protected void onCommit() {
            Settings.save();
        }
    }

    private final class BrightnessSlider extends FlatSlider {
        BrightnessSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.literal(""), Settings.INSTANCE.brightness / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("亮度: " + Settings.INSTANCE.brightness + "%"));
        }

        @Override
        protected void applyValue() {
            Settings.INSTANCE.brightness = (int) Math.round(value * 100);
        }

        @Override
        protected void onCommit() {
            Settings.save();
        }
    }
}
