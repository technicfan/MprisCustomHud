package technicfan.mpriscustomhud;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public class AlbumArtManager {
    private static Minecraft client = Minecraft.getInstance();
    private static ConcurrentHashMap<String, Tuple<ResourceLocation, Float>> loaded = new ConcurrentHashMap<>();
    private static HashSet<ResourceLocation> toRemove = new HashSet<>();
    public static final Tuple<ResourceLocation, Float> missing = new Tuple<>(ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, "textures/missing.png"), 1f);

    public static class Tuple<K, L> {
        private final K a;
        private final L b;

        Tuple(K a, L b) {
            this.a = a;
            this.b = b;
        }

        public K getA() {
            return a;
        }

        public L getB() {
            return b;
        }
    }

    private static void remove(ResourceLocation id) {
        loaded.remove(id.getPath());
        toRemove.add(id);
    }

    private static void add(Tuple<ResourceLocation, Float> element) {
        toRemove.remove(element.getA());
        loaded.put(element.getA().getPath(), element);
    }

    protected static void loadAlbumArt(String busname, String url) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, busname.substring(23));
        if (!url.isEmpty()) {
            try {
                BufferedImage data = ImageIO.read(URI.create(url).toURL());
                if (data != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(data, "PNG", output);
                    NativeImage image = NativeImage.read(output.toByteArray());
                    client.executeBlocking(() -> {
                        client.getTextureManager().register(id, new DynamicTexture(id::getPath, image));
                        add(new Tuple<>(id, (float) image.getWidth() / image.getHeight()));
                    });
                }
            } catch (IOException e) {
                MprisCustomHud.log("Failed to load album image");
                remove(id);
            }
        } else {
            remove(id);
        }
    }

    protected static void release(TextureManager manager) {
        toRemove.forEach(id -> {
            manager.release(id);
        });
        toRemove.removeIf(id -> true);
    }

    public static Tuple<ResourceLocation, Float> getInfo(String name) {
        String path = name.length() > 23 ? name.substring(23) : name;
        Tuple<ResourceLocation, Float> id = loaded.get(path);
        return id != null ? id : missing;
    }
}
