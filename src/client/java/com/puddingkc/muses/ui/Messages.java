package com.puddingkc.muses.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Messages {
    private Messages() {
    }

    private static final String PREFIX_TEXT = "[Muses] ";
    private static final Formatting PREFIX_COLOR = Formatting.AQUA;
    private static final Formatting OK_COLOR = Formatting.WHITE;
    private static final Formatting ERROR_COLOR = Formatting.RED;
    private static final Formatting WARN_COLOR = Formatting.GOLD;
    private static final Formatting INFO_COLOR = Formatting.GRAY;
    private static final Formatting TIP_COLOR = Formatting.AQUA;

    public static void ok(String text) {
        chat(text, OK_COLOR);
    }

    public static void error(String text) {
        chat(text, ERROR_COLOR);
    }

    public static void warn(String text) {
        chat(text, WARN_COLOR);
    }

    public static void info(String text) {
        chat(text, INFO_COLOR);
    }

    public static void tip(String text) {
        chat(text, TIP_COLOR);
    }

    public static void actionBar(String text, Formatting color) {
        send(text, color, true);
    }

    public static MutableText line(String text, Formatting color) {
        return prefix().append(Text.literal(text).formatted(color));
    }

    private static MutableText prefix() {
        return Text.literal(PREFIX_TEXT).formatted(PREFIX_COLOR);
    }

    private static void chat(String text, Formatting color) {
        send(text, color, false);
    }

    private static void send(String text, Formatting color, boolean overlay) {
        ClientPlayerEntity who = MinecraftClient.getInstance().player;
        if (who == null) return;
        who.sendMessage(line(text, color), overlay);
    }
}
