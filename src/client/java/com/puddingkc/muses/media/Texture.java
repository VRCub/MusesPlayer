package com.puddingkc.muses.media;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL21.*;

public class Texture {
    private int textureId;
    private int width;
    private int height;
    private boolean ready = false;
    private final Pbo pbo = new Pbo();

    public Texture(int width, int height) {
        this.width = width;
        this.height = height;
        textureId = glGenTextures();
        regen();
        pbo.init(width, height);
    }

    public synchronized void resize(int width, int height) {
        this.width = width;
        this.height = height;
        regen();
        pbo.init(width, height);
    }

    private void regen() {
        RenderSystem.bindTexture(textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        RenderSystem.bindTexture(0);
        ready = true;
    }

    public synchronized void upload(ByteBuffer frame) {
        if (!pbo.allocated()) {
            return;
        }
        RenderSystem.bindTexture(textureId);
        unpackState();
        int prev = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        pbo.transfer(frame);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, prev);
        RenderSystem.bindTexture(0);
    }

    private void unpackState() {
        RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, 0);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, 0);
        RenderSystem.pixelStore(GL_UNPACK_ALIGNMENT, 4);
        RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, width);
    }

    public synchronized void stop() {
        pbo.release();
    }

    public void cleanup() {
        if (!ready) {
            return;
        }
        MinecraftClient.getInstance().execute(() -> {
            glDeleteTextures(textureId);
            pbo.release();
        });
        ready = false;
    }

    public int id() {
        return textureId;
    }
}
