package com.puddingkc.muses.ui;

import com.puddingkc.muses.config.Settings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class GeneralTabScreen extends MusesScreen {
    public GeneralTabScreen(Screen parent) {
        super(parent, "设置");
    }

    @Override
    protected int panelHeight() {
        return 200;
    }

    @Override
    protected void initBody() {
        int left = bodyX;
        int top = bodyY + 14;
        int span = bodyW;
        int half = (span - 4) / 2;
        addDrawableChild(Style.button(barText(), left, top, span, 18, b -> flipBar()));
        addDrawableChild(Style.button(hudText(), left, top + 22, span, 18, b -> flipHud()));
        addDrawableChild(Style.button(danmakuText(), left, top + 44, span, 18, b -> flipDanmaku()));
        addDrawableChild(Style.button(dynLightText(), left, top + 66, half, 18, b -> flipLight()));
        ButtonWidget strength = Style.button(strengthText(), left + half + 4, top + 66, span - half - 4, 18, b -> cycleStrength());
        strength.active = Settings.INSTANCE.dynamicLight;
        addDrawableChild(strength);
        addDrawableChild(Style.button(spatialText(), left, top + 88, half, 18, b -> flipSpatial()));
        ButtonWidget rangeBtn = Style.button(rangeText(), left + half + 4, top + 88, span - half - 4, 18, b -> cycleRange());
        rangeBtn.active = Settings.INSTANCE.spatialAudio;
        addDrawableChild(rangeBtn);
    }

    private void reinit() {
        clearChildren();
        init();
    }

    @Override
    protected void renderBody(DrawContext context, int mouseX, int mouseY, float delta) {
        String hint = textRenderer.trimToWidth("全局默认设置，可在 管理 → 屏幕设置 中按屏幕覆盖", bodyW);
        context.drawTextWithShadow(textRenderer, Text.literal(hint), bodyX, bodyY, Style.TEXT_DIM);
    }

    private Text barText() {
        return onOff("视频下方进度条: ", Settings.INSTANCE.worldProgressBar);
    }

    private Text hudText() {
        return onOff("屏幕上方进度条: ", Settings.INSTANCE.screenHud);
    }

    private Text danmakuText() {
        return onOff("弹幕: ", Settings.INSTANCE.danmaku);
    }

    private Text dynLightText() {
        return onOff("动态照明: ", Settings.INSTANCE.dynamicLight);
    }

    private Text strengthText() {
        int level = Settings.INSTANCE.dynamicLightStrength;
        String name = "中";
        if (level == 0) name = "弱";
        else if (level == 2) name = "强";
        return Text.literal("照明强度: " + name);
    }

    private Text spatialText() {
        return onOff("空间音频: ", Settings.INSTANCE.spatialAudio);
    }

    private Text rangeText() {
        return Text.literal("音频范围: " + Settings.INSTANCE.audioRange);
    }

    private void flipBar() {
        Settings.INSTANCE.worldProgressBar = !Settings.INSTANCE.worldProgressBar;
        store();
    }

    private void flipHud() {
        Settings.INSTANCE.screenHud = !Settings.INSTANCE.screenHud;
        store();
    }

    private void flipDanmaku() {
        Settings.INSTANCE.danmaku = !Settings.INSTANCE.danmaku;
        store();
    }

    private void flipLight() {
        Settings.INSTANCE.dynamicLight = !Settings.INSTANCE.dynamicLight;
        store();
    }

    private void cycleStrength() {
        Settings.INSTANCE.dynamicLightStrength = (Settings.INSTANCE.dynamicLightStrength + 1) % 3;
        store();
    }

    private void flipSpatial() {
        Settings.INSTANCE.spatialAudio = !Settings.INSTANCE.spatialAudio;
        store();
    }

    private void cycleRange() {
        int current = Settings.INSTANCE.audioRange;
        int next = 32;
        if (current >= 64) next = 16;
        else if (current >= 32) next = 64;
        Settings.INSTANCE.audioRange = next;
        store();
    }

    private void store() {
        Settings.save();
        reinit();
    }

    private static Text onOff(String prefix, boolean on) {
        String mark = on ? "开" : "关";
        Formatting tone = on ? Formatting.GREEN : Formatting.RED;
        return Text.literal(prefix + mark).formatted(tone);
    }
}
