package technicfan.mpriscustomhud;

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
        toRemove.clear();;
    }

    protected static void clear() {
        for (File f : cache.values()) {
            f.delete();
        }
        cache.clear();
    }

    private static int dominantColor(NativeImage image) {
        int[] colorGroups = new int[32*32*32];
        //? if >=1.21.2 {
        for (int rgb : image.getPixels()) {
        //?} else {
        /*for (int rgb : image.getPixelsRGBA()) {*/
        //?}
            // set all bits 0 except for 6 per color (f8_16 = 1111 1000_2)
            rgb &= 0x00f8f8f8;
            // extract the first 5bit and put them at the right place for each color
            // then add one to the counter of that color
            // this creates many (256 >> 3 (/8) = 32 => 32^3) groups a rgb color can land in
            colorGroups[rgb >> 9 | (rgb & 0x00ff00) >> 6 | (rgb & 0x0000ff) >> 3]++;
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
        return (result & 0xfc00) << 9 | (result & 0x03e0) << 6 | (result & 0x001f) << 3;
    }
}
