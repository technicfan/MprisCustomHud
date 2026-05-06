package technicfan.mpriscustomhud;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import technicfan.mpriscustomhud.PlayerInfo.AlbumArt;
import technicfan.mpriscustomhud.PlayerInfo.Metadata;

public class AlbumArtManager {
    private static Minecraft minecraft;
    private static final int maxCacheSize = 16;
    private static final File cacheDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "cache/" + MprisCustomHud.MOD_ID);
    private static HashSet<ResourceLocation> toRemove = new HashSet<>();
    private static ConcurrentHashMap<String, File> cache = new ConcurrentHashMap<>();

    protected static void init(Minecraft client) {
        minecraft = client;
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        } else {
            for (File f : cacheDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile() && pathname.getName().endsWith(".png");
                }
            })) {
                cache.put(f.getName(), f);
            }
        }
    }

    protected static PlayerInfo loadAlbumArt(PlayerInfo player) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, player.busname.substring(23));
        if (!player.metadata.art_url.isEmpty()) {
            try {
                BufferedImage data = null;
                boolean cached = false;
                String cacheName = getCacheName(player.metadata);
                if (cacheName != null && cache.containsKey(cacheName)) {
                    try {
                        data = ImageIO.read(cache.get(cacheName).toURI().toURL());
                        cached = true;
                    } catch (IOException e) {}
                }
                if (data == null) {
                    data = ImageIO.read(URI.create(player.metadata.art_url).toURL());
                }
                if (data != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(data, "PNG", output);
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
                    minecraft.executeBlocking(() -> {
                        minecraft.getTextureManager().register(id, new DynamicTexture(
                            //? if >=1.21.2 {
                            id::getPath,
                            //?}
                        image));
                    });
                    toRemove.remove(id);
                    if (!cached) {
                        addToCache(cacheName, data);
                    }
                    return player.update(new AlbumArt(id, dominantColor(image), image.getWidth(), image.getHeight()));
                }
            } catch (IOException e) {}
        }
        toRemove.add(id);
        return player.update(AlbumArt.EMPTY);
    }

    private static void removeOldest() {
        Optional<File> oldest = cache.values().stream().min((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        if (oldest.isPresent() && oldest.get().delete()) {
            cache.remove(oldest.get().getName());
        }
    }

    private static void addToCache(String name, BufferedImage image) {
        try {
            if (name != null) {
                if (cache.mappingCount() >= maxCacheSize) {
                    removeOldest();
                }
                File file = new File(cacheDir, name);
                if (file.createNewFile()) {
                    ImageIO.write(image, "PNG", file);
                    cache.put(name, file);
                }
            }
        } catch (IOException e) {}
    }

    private static String getCacheName(Metadata metadata) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(StandardCharsets.UTF_8.encode(metadata.track + metadata.trackid + metadata.art_url));
            return String.format("%032x.png", new BigInteger(1, md5.digest()));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    protected static void remove(AlbumArt albumArt) {
        if (albumArt.exists()) {
            toRemove.add(albumArt.getId());
        }
    }

    protected static void release(TextureManager manager) {
        toRemove.forEach(id -> {
            manager.release(id);
        });
        toRemove.clear();
    }

    protected static void clear() {
        for (File f : cache.values()) {
            f.delete();
        }
        cache.clear();
    }

    private static int dominantColor(NativeImage img) {
        HashMap<Integer, double[]> buckets = new HashMap<>(64);
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 64);
        float[] hsb = new float[3];

        for (int x = 0; x < img.getWidth(); x += step) {
            for (int y = 0; y < img.getHeight(); y += step) {
                // ? if >=1.21.2 {
                int rgb = img.getPixel(x, y);
                // ?} else {
                /* int rgb = img.getPixelRGBA(x, y); */
                // ?}

                int alpha = (rgb >>> 24) & 0xff;
                if (alpha < 200) {
                    continue;
                }

                int r = (rgb >>> 16) & 0xff;
                int g = (rgb >>> 8) & 0xff;
                int b = rgb & 0xff;
                Color.RGBtoHSB(r, g, b, hsb);

                if (hsb[2] < 0.2f || hsb[2] > 0.95f || hsb[1] < 0.2f) {
                    continue;
                }

                double[] values = buckets.computeIfAbsent(
                        (int) (hsb[0] * 12f), k -> new double[4]);
                double weight = hsb[1] * hsb[2];
                values[0] += weight;
                values[1] += r * weight;
                values[2] += g * weight;
                values[3] += b * weight;
            }
        }

        if (buckets.isEmpty()) {
            return 0xff7f7f7f;
        }

        double[] best = null;
        for (double[] bucket : buckets.values()) {
            if (best == null || bucket[0] > best[0]) {
                best = bucket;
            }
        }

        double weight = Math.max(best[0], 1.0);
        return (Math.clamp((int) (best[1] / weight), 0, 255) << 16) |
                (Math.clamp((int) (best[2] / weight), 0, 255) << 8) |
                Math.clamp((int) (best[3] / weight), 0, 255);
    }
}
