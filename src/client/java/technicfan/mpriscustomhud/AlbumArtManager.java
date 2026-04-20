package technicfan.mpriscustomhud;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public class AlbumArtManager {
    private static Minecraft client = Minecraft.getInstance();
    private static ConcurrentHashMap<ResourceLocation, DynamicTexture> albumArtCache = new ConcurrentHashMap<>();

    protected static void loadAlbumArt(String busname, String url) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, busname.substring(23));
        DynamicTexture oldTexture = albumArtCache.get(id);
        if (!url.isEmpty()) {
            BufferedImage image = null;
            try {
                image = ImageIO.read(URI.create(url).toURL());
                if (image != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(image, "PNG", output);
                    client.executeBlocking(() -> {
                        try {
                            DynamicTexture texture = new DynamicTexture(id::getPath, NativeImage.read(output.toByteArray()));
                            if (oldTexture != null) {
                                client.getTextureManager().release(id);
                                albumArtCache.remove(id);
                                oldTexture.close();
                            }
                            albumArtCache.put(id, texture);
                            client.getTextureManager().register(id, texture);
                        } catch (IOException e) {
                            MprisCustomHud.log("Failed to load album image into the game");
                            if (oldTexture != null) {
                                client.getTextureManager().release(id);
                                albumArtCache.remove(id);
                                oldTexture.close();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                MprisCustomHud.log("Failed to load album image");
                if (oldTexture != null) {
                    client.execute(() -> {
                        client.getTextureManager().release(id);
                        albumArtCache.remove(id);
                        oldTexture.close();
                    });
                }
            }
        } else if (oldTexture != null) {
            client.executeBlocking(() -> {
                client.getTextureManager().release(id);
                albumArtCache.remove(id);
                oldTexture.close();
            });
        }
    }

    public static ResourceLocation isLoaded(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, name.length() > 23 ? name.substring(23) : name);
        return albumArtCache.containsKey(id) ? id : null;
    }
}
