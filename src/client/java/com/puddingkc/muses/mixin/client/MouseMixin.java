package com.puddingkc.muses.mixin.client;

import com.puddingkc.muses.input.DragController;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void museplayer$moveScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!DragController.isMoving()) return;
        DragController.addDepth((float) vertical);
        ci.cancel();
    }
}
