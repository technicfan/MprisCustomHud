package io.github.technicfan.mpriscustomhud;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import io.github.technicfan.mpriscustomhud.PlayerInfo.AlbumArt;

public class AlbumArtManager {
    private static Minecraft minecraft;
    private static HashSet<ResourceLocation> toRemove = new HashSet<>();

    protected static void init(Minecraft client) {
        minecraft = client;
    }

    protected static PlayerInfo loadAlbumArt(PlayerInfo player) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, player.busname.substring(23));
        if (!player.metadata.art_url.isEmpty()) {
            try {
                BufferedImage data = ImageIO.read(URI.create(player.metadata.art_url).toURL());
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
                    return player.update(new AlbumArt(id, dominantColor(image), image.getWidth(), image.getHeight()));
                }
            } catch (IOException e) {}
        }
        toRemove.add(id);
        return player.update(AlbumArt.EMPTY);
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

    private static int dominantColor(NativeImage img) {
        HashMap<Integer, double[]> buckets = new HashMap<>();
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 64);
        float[] hsb = new float[3];
        //? if >=1.21.2 {
        int offset = 0;
        //?} else {
        /*int offset = 16;*/
        //?}

        for (int x = 0; x < img.getWidth(); x += step) {
            for (int y = 0; y < img.getHeight(); y += step) {
                //? if >=1.21.2 {
                // this gives me an ARGB integer (with lsb in B)
                int rgb = img.getPixel(x, y);
                //?} else {
                /*// this gives me a ABGR integer (with lsb in R)
                int rgb = img.getPixelRGBA(x, y);*/
                //?}

                int alpha = (rgb >> 24) & 0xff;
                if (alpha < 200) {
                    continue;
                }

                int r = (rgb >> 16 - offset) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = (rgb >> offset) & 0xff;
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
            return 0x007f7f7f;
        }

        double[] best = null;
        for (double[] bucket : buckets.values()) {
            if (best == null || bucket[0] > best[0]) {
                best = bucket;
            }
        }

        double weight = Math.max(best[0], 1.0);
        return (Mth.clamp((int) (best[1] / weight), 0, 255) << 16) |
                (Mth.clamp((int) (best[2] / weight), 0, 255) << 8) |
                Mth.clamp((int) (best[3] / weight), 0, 255);
    }
}
