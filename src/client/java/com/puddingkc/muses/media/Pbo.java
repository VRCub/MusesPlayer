package com.puddingkc.muses.media;

import net.minecraft.client.MinecraftClient;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.opengl.GL21.*;

public class Pbo {
    private final int[] id = new int[2];
    private boolean allocated = false;
    private int cursor;
    private ByteBuffer scratch;
    private final ReentrantLock lock = new ReentrantLock();

    private static void alloc(int id, int width, int height) {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4L, GL_STREAM_DRAW);
    }

    public void init(int width, int height) {
        if (!allocated) {
            glGenBuffers(id);
            allocated = true;
        }
        int prev = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        for (int buf : id) {
            alloc(buf, width, height);
        }
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, prev);
        scratch = ByteBuffer.allocateDirect(width * height * 4);
    }

    public void release() {
        lock.lock();
        try {
            if (!allocated) {
                return;
            }
            int[] copy = {id[0], id[1]};
            MinecraftClient.getInstance().execute(() -> glDeleteBuffers(copy));
            allocated = false;
            scratch = null;
        } finally {
            lock.unlock();
        }
    }

    public void transfer(ByteBuffer frame) {
        lock.lock();
        try {
            if (!allocated || scratch == null) {
                throw new IllegalStateException("unpack buffer missing");
            }
            int slot = Math.floorMod(cursor, 2);
            cursor++;
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id[slot]);
            ByteBuffer mapped = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
            if (mapped != null && mapped.remaining() == frame.remaining() && frame.remaining() == scratch.capacity()) {
                mapped.put(frame);
            }
            glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
        } finally {
            lock.unlock();
        }
    }

    public boolean allocated() {
        return allocated;
    }
}
