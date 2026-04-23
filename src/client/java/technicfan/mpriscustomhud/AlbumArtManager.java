package technicfan.mpriscustomhud;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import technicfan.mpriscustomhud.PlayerInfo.AlbumArt;

public class AlbumArtManager {
    private static Minecraft client = Minecraft.getInstance();
    private static HashSet<ResourceLocation> toRemove = new HashSet<>();

    protected static PlayerInfo loadAlbumArt(PlayerInfo player) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, player.busname.substring(23));
        if (!player.metadata.art_url.isEmpty()) {
            try {
                BufferedImage data = ImageIO.read(URI.create(player.metadata.art_url).toURL());
                if (data != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(data, "PNG", output);
                    NativeImage image = NativeImage.read(output.toByteArray());
                    client.executeBlocking(() -> {
                        client.getTextureManager().register(id, new DynamicTexture(id::getPath, image));
                    });
                    toRemove.remove(id);
                    return player.update(new AlbumArt(id, dominantColor(image), image.getWidth(), image.getHeight()));
                }
            } catch (IOException e) {
                MprisCustomHud.log("Failed to load album image");
                MprisCustomHud.LOGGER.warn(e.toString(), e.fillInStackTrace());
            }
        }
        toRemove.add(id);
        return player.update(AlbumArt.empty());
    }

    protected static void remove(AlbumArt albumArt) {
        if (!albumArt.isEmpty()) {
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
            rgb &= 0xffe0e0e0;
            // extract the 3bit and put them at the right place for each color
            // then add one to the counter of that color
            colorGroups[(rgb & 0xff0000) >> 15 | (rgb & 0x00ff00) >> 10 | (rgb & 0x0000ff) >> 5]++;
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
