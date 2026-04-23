package technicfan.mpriscustomhud;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
                String cacheName = getCacheName(player.metadata.art_url);
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
                    NativeImage image = NativeImage.read(output.toByteArray());
                    minecraft.executeBlocking(() -> {
                        minecraft.getTextureManager().register(id, new DynamicTexture(id::getPath, image));
                    });
                    toRemove.remove(id);
                    if (!cached) {
                        addToCache(player.metadata.art_url, data);
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

    private static void addToCache(String url, BufferedImage image) {
        try {
            String name = getCacheName(url);
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

    private static String getCacheName(String url) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(StandardCharsets.UTF_8.encode(url));
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
        toRemove.removeIf(id -> true);
    }

    private static int dominantColor(NativeImage image) {
        int[] colorGroups = new int[512];
        for (int rgb : image.getPixels()) {
            // set all bits 0 except for 3 per color (e0_16 = 1110 0000_2)
            rgb &= 0x00e0e0e0;
            // extract the 3bit and put them at the right place for each color
            // then add one to the counter of that color
            // this creates 512 (256 >> 5 (/32) = 8; 8^3 = 512) groups a rgb color can land in
            colorGroups[rgb >> 15 | (rgb & 0x00ff00) >> 10 | (rgb & 0x0000ff) >> 5]++;
        }
        // find the group that appeared most
        int result = 0, max = colorGroups[0];
        for (int i = 1; i < colorGroups.length; i++) {
            if (colorGroups[i] > max) {
                result = i;
                max = colorGroups[i];
            }
        }
        // convert back to 8bit per color
        return (result & 0x1c0) << 15 | (result & 0x38) << 10 | (result & 0x7) << 5;
    }
}
