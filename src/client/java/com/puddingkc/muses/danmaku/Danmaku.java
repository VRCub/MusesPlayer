package com.puddingkc.muses.danmaku;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.puddingkc.muses.MusesPlayer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class Danmaku {
    private Danmaku() {
    }

    private static final String UA = "MusesPlayer/1.2 (compatible; commentary-fetch)";
    private static final String XML_URL = "https://comment.bilibili.com/%s.xml";

    private static final Cache<String, List<Bullet>> CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(64)
            .build();
    private static final Set<String> inflight = ConcurrentHashMap.newKeySet();

    public static List<Bullet> get(String cid) {
        if (cid == null || cid.isEmpty()) return null;
        List<Bullet> hit = CACHE.getIfPresent(cid);
        if (hit == null) fetch(cid);
        return hit;
    }

    private static void fetch(String cid) {
        if (!inflight.add(cid)) return;
        CompletableFuture.runAsync(() -> load(cid));
    }

    private static void load(String cid) {
        try {
            List<Bullet> parsed = parse(decode(download(cid)));
            CACHE.put(cid, Collections.unmodifiableList(parsed));
            MusesPlayer.LOGGER.info("加载 {} 条弹幕 (cid {})", parsed.size(), cid);
        } catch (Exception e) {
            MusesPlayer.LOGGER.warn("加载弹幕失败 (cid {}): {}", cid, e.toString());
            CACHE.put(cid, Collections.emptyList());
        } finally {
            inflight.remove(cid);
        }
    }

    private static byte[] download(String cid) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(XML_URL.formatted(cid)))
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.bilibili.com")
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return resp.body();
        }
    }

    static String decode(byte[] body) {
        if (body == null || body.length == 0) return "";
        String plain = new String(body, StandardCharsets.UTF_8);
        if (xmlish(plain)) return plain;
        int first = gzipMagic(body) ? 0 : 1;
        for (int step = first; step < 3; step++) {
            String text = expand(body, step);
            if (text == null) continue;
            if (step == 0 || xmlish(text)) return text;
        }
        return plain;
    }

    private static String expand(byte[] body, int step) {
        if (step == 0) return tryGzip(body);
        return tryInflate(body, step > 1);
    }

    private static boolean gzipMagic(byte[] body) {
        if (body.length <= 2) return false;
        return (body[0] & 0xFF) == 0x1f && (body[1] & 0xFF) == 0x8b;
    }

    private static boolean xmlish(String text) {
        return text.contains("<d ") || text.contains("<i>");
    }

    private static String tryGzip(byte[] body) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return utf8(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String tryInflate(byte[] body, boolean nowrap) {
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(body), new Inflater(nowrap))) {
            return utf8(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String utf8(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    static List<Bullet> parse(String xml) {
        ArrayList<Bullet> bullets = new ArrayList<>();
        int cursor = 0;
        String openMark = "<d p=\"";
        String closeMark = "</d>";
        while (cursor <= xml.length()) {
            int open = xml.indexOf(openMark, cursor);
            if (open < 0) break;
            int attrAt = open + openMark.length();
            int quote = xml.indexOf('"', attrAt);
            if (quote < 0) break;
            int gt = xml.indexOf('>', quote + 1);
            if (gt < 0) break;
            int close = xml.indexOf(closeMark, gt + 1);
            if (close < 0) break;
            cursor = close + closeMark.length();
            accept(bullets, xml.substring(attrAt, quote), xml.substring(gt + 1, close));
        }
        bullets.sort(Comparator.comparingLong(Bullet::timeMs));
        return bullets;
    }

    private static void accept(List<Bullet> out, String attrs, String body) {
        String timeRaw = attrAt(attrs, 0);
        String typeRaw = attrAt(attrs, 1);
        String colorRaw = attrAt(attrs, 3);
        if (timeRaw == null || typeRaw == null || colorRaw == null) return;
        try {
            long timeMs = (long) (Double.parseDouble(timeRaw) * 1000);
            int type = Integer.parseInt(typeRaw);
            int color = (int) (Long.parseLong(colorRaw) & 0xFFFFFF);
            String text = unescape(body);
            if (text.isEmpty()) return;
            out.add(new Bullet(timeMs, type, color, text));
        } catch (NumberFormatException ignored) {
        }
    }

    private static String attrAt(String attrs, int index) {
        int start = 0;
        int slot = 0;
        for (int i = 0; i <= attrs.length(); i++) {
            if (i != attrs.length() && attrs.charAt(i) != ',') continue;
            if (slot == index) return attrs.substring(start, i);
            slot++;
            start = i + 1;
        }
        return null;
    }

    private static String unescape(String raw) {
        String[] from = {"&amp;", "&lt;", "&gt;", "&quot;", "&#39;", "&apos;"};
        String[] to = {"&", "<", ">", "\"", "'", "'"};
        String cur = raw;
        for (int i = 0; i < from.length; i++) {
            cur = cur.replace(from[i], to[i]);
        }
        return cur.trim();
    }
}
