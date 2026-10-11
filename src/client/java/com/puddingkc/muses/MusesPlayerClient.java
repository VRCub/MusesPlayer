package com.puddingkc.muses;

import com.mojang.brigadier.context.CommandContext;
import com.puddingkc.muses.config.Settings;
import com.puddingkc.muses.input.DragController;
import com.puddingkc.muses.input.Interaction;
import com.puddingkc.muses.light.DynamicLights;
import com.puddingkc.muses.media.FlatProjector;
import com.puddingkc.muses.media.NativeLoader;
import com.puddingkc.muses.media.Projector;
import com.puddingkc.muses.media.SpatialAudio;
import com.puddingkc.muses.media.VlcEngine;
import com.puddingkc.muses.net.Channel;
import com.puddingkc.muses.net.Inbound;
import com.puddingkc.muses.net.Outbound;
import com.puddingkc.muses.render.ScreenHud;
import com.puddingkc.muses.render.SeekBar;
import com.puddingkc.muses.render.SelectionDraw;
import com.puddingkc.muses.render.WorldDraw;
import com.puddingkc.muses.ui.CreateSelector;
import com.puddingkc.muses.ui.CreateTabScreen;
import com.puddingkc.muses.ui.Messages;
import com.puddingkc.muses.ui.PlayerScreen;
import com.puddingkc.muses.world.Stage;
import com.puddingkc.muses.world.Theater;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class MusesPlayerClient implements ClientModInitializer {
    public static final MinecraftClient client = MinecraftClient.getInstance();

    public static KeyBinding menuKey;
    public static KeyBinding moveKey;

    public static Consumer<Stage> menuOpener = stage -> {};

    private static boolean menuPressed;
    private static boolean barUsePressed;
    private static Stage barSeekStage;
    private static long barSeekProgress;

    @Override
    public void onInitializeClient() {
        NativeLoader.setup();
        VlcEngine.load();
        Settings.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.museplayer.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.museplayer"
        ));
        moveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.museplayer.move",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.museplayer"
        ));

        Channel.register();

        menuOpener = stage -> {
            if (!Theater.authenticated || client.player == null || client.world == null || client.currentScreen != null) {
                return;
            }
            client.setScreen(new PlayerScreen(stage));
        };

        ClientPlayNetworking.registerGlobalReceiver(Channel.ID, (payload, ctx) -> ctx.client().execute(() -> {
            ByteBuf buf = Unpooled.wrappedBuffer(payload.data());
            try {
                Inbound.handle(buf);
            } catch (Exception e) {
                MusesPlayer.LOGGER.error("frame dispatch failed", e);
            } finally {
                buf.release();
            }
        }));

        WorldRenderEvents.AFTER_SETUP.register(context -> {
            updateTextures();
            Interaction.scan();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(WorldDraw::render);
        WorldRenderEvents.AFTER_ENTITIES.register(WorldDraw::renderBars);
        WorldRenderEvents.AFTER_ENTITIES.register(WorldDraw::renderDanmaku);
        WorldRenderEvents.AFTER_ENTITIES.register(SelectionDraw::render);

        HudRenderCallback.EVENT.register(ScreenHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(MusesPlayerClient::clientTick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, c) -> c.execute(() -> {
            Theater.reset();
            DragController.reset();
        }));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("museplayer")
                        .then(ClientCommandManager.literal("version").executes(MusesPlayerClient::version))
                        .executes(MusesPlayerClient::version)));
    }

    private static void clientTick(MinecraftClient mc) {
        pollMenu(mc);
        pollMove(mc);
        DragController.tick();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            barUsePressed = false;
            return;
        }
        if (trackBar(mc)) return;
        commitBarSeek();
        barUsePressed = false;
        pollLookUse(mc);
    }

    private static void pollMenu(MinecraftClient mc) {
        while (menuKey.wasPressed()) {
            if (mc.player == null || mc.world == null || mc.currentScreen != null) continue;
            if (!Theater.authenticated) {
                Messages.actionBar("与服务端握手失败，无法操作", Formatting.RED);
                continue;
            }
            if (CreateSelector.active()) {
                mc.setScreen(new CreateTabScreen(null));
                continue;
            }
            menuOpener.accept(Interaction.looking != null ? Interaction.looking : Interaction.current);
        }
    }

    private static void pollMove(MinecraftClient mc) {
        while (moveKey.wasPressed()) {
            if (DragController.isMoving()) {
                DragController.confirm();
                continue;
            }
            if (!Theater.authenticated || mc.player == null || mc.world == null
                    || mc.currentScreen != null || Interaction.looking == null) {
                continue;
            }
            DragController.begin(Interaction.looking);
        }
    }

    private static boolean trackBar(MinecraftClient mc) {
        if (!Theater.authenticated || Interaction.barTarget == null || Interaction.looking != null) {
            return false;
        }
        boolean useDown = mc.options.useKey.isPressed();
        boolean handsOk = mc.player.getStackInHand(Hand.MAIN_HAND).isEmpty()
                && mc.player.getStackInHand(Hand.OFF_HAND).isEmpty();
        if (!useDown || !handsOk) return false;
        Stage target = Interaction.barTarget.sourceStage();
        Projector projector = target.projector();
        if (projector instanceof FlatProjector && projector.canSeek() && projector.total() > 0) {
            SeekBar.setPreview(Interaction.barTarget, Interaction.barRatio);
            barSeekStage = target;
            barSeekProgress = (long) (Interaction.barRatio * projector.total());
            barUsePressed = true;
            return true;
        }
        barUsePressed = true;
        return false;
    }

    private static void pollLookUse(MinecraftClient mc) {
        if (Interaction.looking == null) {
            menuPressed = false;
            return;
        }
        boolean pressed = mc.options.useKey.isPressed();
        if (!pressed) {
            menuPressed = false;
            return;
        }
        if (menuPressed) return;
        menuPressed = true;
        boolean handsEmpty = mc.player.getMainHandStack().isEmpty()
                && mc.player.getOffHandStack().isEmpty();
        if (handsEmpty && Theater.authenticated) {
            menuOpener.accept(Interaction.looking);
        }
    }

    private static void commitBarSeek() {
        SeekBar.clearPreview();
        if (barSeekStage == null) return;
        barSeekStage.setProgress(barSeekProgress);
        Outbound.seek(barSeekStage, barSeekProgress);
        barSeekStage = null;
    }

    private static void updateTextures() {
        for (Stage screen : Theater.screens) {
            try {
                screen.swapTexture();
                screen.update();
            } catch (Exception e) {
                MusesPlayer.LOGGER.error("texture refresh failed", e);
            }
        }
        try {
            SpatialAudio.update();
        } catch (Exception e) {
            MusesPlayer.LOGGER.error("spatial audio refresh failed", e);
        }
        try {
            DynamicLights.refresh();
        } catch (Exception e) {
            MusesPlayer.LOGGER.error("dynamic light refresh failed", e);
        }
    }

    private static int version(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("Muses Player " + MusesPlayer.VERSION)
                .formatted(Formatting.AQUA));
        return 1;
    }
}
