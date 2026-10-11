package com.puddingkc.muses.ui;

import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.world.Stage;
import net.minecraft.text.Text;

public class FlagSlider extends FlatSlider {
    private final Stage stage;
    private final String label;
    private final String key;
    private final int min;
    private final int max;
    private int lastSent;

    public FlagSlider(int x, int y, int w, int h, Stage stage, String label, String key, int min, int max, int fallback) {
        super(x, y, w, h, Text.literal(""), ratio(stage, key, min, max, fallback));
        this.stage = stage;
        this.label = label;
        this.key = key;
        this.min = min;
        this.max = max;
        this.lastSent = stage.flag(key, fallback);
        updateMessage();
    }

    private int currentValue() {
        double span = max - min;
        return (int) Math.round(min + value * span);
    }

    private void commit() {
        int next = currentValue();
        if (next == lastSent) return;
        lastSent = next;
        stage.flags.put(key, next);
        Outbound.flagCustom(stage, key, next, false);
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(label + ": " + currentValue()));
    }

    @Override
    protected void applyValue() {
        updateMessage();
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        commit();
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean used = super.keyPressed(keyCode, scanCode, modifiers);
        if (used) commit();
        return used;
    }

    private static double ratio(Stage stage, String key, int min, int max, int fallback) {
        return (stage.flag(key, fallback) - min) / (double) (max - min);
    }
}
