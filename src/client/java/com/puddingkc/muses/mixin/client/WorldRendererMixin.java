package com.puddingkc.muses.mixin.client;

import com.puddingkc.muses.render.WorldDraw;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    public void museplayer$noClouds(CallbackInfo ci) {
        if (!WorldDraw.skybox) return;
        ci.cancel();
    }
}
