package com.puddingkc.muses.media;

import com.puddingkc.muses.MusesPlayer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NativeLoader {
    private NativeLoader() {
    }

    private static final String[] LIB_NAMES = {"libvlc.dll", "libvlc.so", "libvlc.dylib"};
    private static final String[] MANUAL_DIRS = {"Muses-vlc", "muses-runtime"};

    public static void setup() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path modsDir = gameDir.resolve("mods");
            Path cacheDir = gameDir.resolve("muses-natives");
            Path zip = findNativesZip(modsDir);
            Path chosen = zip == null ? null : ensureExtracted(zip, cacheDir);
            if (chosen == null) {
                chosen = manualDir(gameDir, modsDir);
            }
            if (chosen == null) {
                MusesPlayer.LOGGER.info("[natives] 没有可用原生库，由 vlcj 查找");
                return;
            }
            apply(chosen);
        } catch (Throwable t) {
            MusesPlayer.LOGGER.warn("[natives] 路径设置失败，由 vlcj 默认发现", t);
        }
    }

    private static Path manualDir(Path gameDir, Path modsDir) {
        for (String name : MANUAL_DIRS) {
            for (Path base : new Path[]{modsDir, gameDir}) {
                Path dir = base.resolve(name);
                if (hasLib(dir)) {
                    return dir;
                }
            }
        }
        return null;
    }

    private static void apply(Path dir) {
        String abs = dir.toAbsolutePath().toString();
        String prior = System.getProperty("jna.library.path");
        boolean blank = prior == null || prior.isEmpty();
        System.setProperty("jna.library.path", blank ? abs : abs + File.pathSeparator + prior);
        Path plugins = dir.resolve("plugins");
        if (Files.isDirectory(plugins)) {
            System.setProperty("VLC_PLUGIN_PATH", plugins.toAbsolutePath().toString());
        }
        MusesPlayer.LOGGER.info("[natives] 原生目录 {}", abs);
    }

    private static boolean hasLib(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        for (String name : LIB_NAMES) {
            if (Files.exists(dir.resolve(name))) {
                return true;
            }
        }
        return false;
    }

    private static Path findNativesZip(Path modsDir) {
        if (!Files.isDirectory(modsDir)) {
            return null;
        }
        try (var listing = Files.list(modsDir)) {
            Iterator<Path> it = listing.iterator();
            while (it.hasNext()) {
                Path candidate = it.next();
                String file = candidate.getFileName().toString().toLowerCase();
                if (!file.endsWith(".zip")) {
                    continue;
                }
                if (looksLikeNatives(candidate)) {
                    return candidate;
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean looksLikeNatives(Path zip) {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                if (nativeName(entries.nextElement().getName())) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static boolean nativeName(String raw) {
        String name = raw.replace('\\', '/');
        String base = name.substring(name.lastIndexOf('/') + 1);
        for (String lib : LIB_NAMES) {
            if (lib.equals(base)) {
                return true;
            }
        }
        return name.contains("plugins/");
    }

    private static Path ensureExtracted(Path zip, Path cacheDir) throws IOException {
        String fingerprint = fingerprint(zip);
        Path root = cacheDir.resolve("current");
        Path marker = cacheDir.resolve(".source");
        if (cached(marker, root, fingerprint)) {
            return root;
        }
        MusesPlayer.LOGGER.info("[natives] 正在解压 {}", zip.getFileName());
        if (Files.exists(root)) {
            deleteRecursively(root);
        }
        Files.createDirectories(root);
        unpack(zip, root, detectPrefix(zip));
        if (!hasLib(root)) {
            MusesPlayer.LOGGER.warn("[natives] 解压结果里没有 libvlc，已忽略");
            return null;
        }
        Files.createDirectories(cacheDir);
        Files.writeString(marker, fingerprint);
        return root;
    }

    private static String fingerprint(Path zip) throws IOException {
        long size = Files.size(zip);
        long mtime = Files.getLastModifiedTime(zip).toMillis();
        return zip.getFileName() + "|" + size + "|" + mtime;
    }

    private static boolean cached(Path marker, Path root, String fingerprint) throws IOException {
        return Files.exists(marker)
                && Files.readString(marker).trim().equals(fingerprint)
                && hasLib(root);
    }

    private static void unpack(Path zip, Path root, String prefix) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) {
                    continue;
                }
                Path out = safeResolve(root, rel);
                if (out == null) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Path parent = out.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String detectPrefix(Path zip) throws IOException {
        String shared = null;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName().replace('\\', '/');
                if (name.isEmpty()) {
                    continue;
                }
                String top = head(name);
                if (top.isEmpty()) {
                    return "";
                }
                if (shared == null) {
                    shared = top;
                } else if (!shared.equals(top)) {
                    return "";
                }
            }
        }
        return shared == null ? "" : shared;
    }

    private static String head(String name) {
        int slash = name.indexOf('/');
        if (slash < 0) {
            return "";
        }
        return name.substring(0, slash + 1);
    }

    private static Path safeResolve(Path root, String rel) {
        Path base = root.normalize();
        Path out = base.resolve(rel).normalize();
        if (!out.startsWith(base)) {
            return null;
        }
        return out;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        List<Path> paths;
        try (var walk = Files.walk(dir)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }
}
