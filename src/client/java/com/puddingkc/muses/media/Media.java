package com.puddingkc.muses.media;

import com.puddingkc.muses.net.Buffers;
import io.netty.buffer.ByteBuf;

public record Media(String requester, String title, String mrl, String origin,
                    long expireAt, boolean seekable, String[] options, String danmaku) {

    public static Media read(ByteBuf in) {
        String requester = Buffers.readText(in, 256);
        String title = Buffers.readText(in, 256);
        String mrl = Buffers.readText(in, 1024);
        String origin = Buffers.readText(in, 1024);
        long expireAt = in.readLong();
        boolean seekable = in.readBoolean();
        String[] options = readOptions(in);
        String danmaku = Buffers.readText(in, 64);
        return new Media(requester, title, mrl, origin, expireAt, seekable, options, danmaku);
    }

    private static String[] readOptions(ByteBuf in) {
        int count = in.readUnsignedByte();
        String[] options = new String[count];
        for (int i = 0; i < count; i++) {
            options[i] = Buffers.readText(in, 256);
        }
        return options;
    }

    public static Media label(String requester, String title) {
        return new Media(requester, title, "", "", -1, false, new String[0], "");
    }

    public boolean hasMedia() {
        return mrl != null && !mrl.isEmpty();
    }
}
