package technicfan.mpriscustomhud.mod_support;

import java.util.HashMap;
import java.util.List;

//? if <=1.21.10 {
import com.minenash.customhud.HudElements.icon.IconElement;
import com.minenash.customhud.HudElements.supplier.BooleanSupplierElement;
import com.minenash.customhud.HudElements.supplier.NumberSupplierElement;
import com.minenash.customhud.HudElements.supplier.StringSupplierElement;
import com.minenash.customhud.data.Flags;
import com.minenash.customhud.registry.CustomHudRegistry;
import com.minenash.customhud.render.RenderPiece;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import technicfan.mpriscustomhud.PlayerInfo.AlbumArt;
//?}
import technicfan.mpriscustomhud.MprisCustomHud;

public class CustomHudSupport {
    public static void register(
        HashMap<String, MprisCustomHud.Function<String>> stringmap,
        HashMap<String, MprisCustomHud.Function<Boolean>> boolmap,
        HashMap<String, MprisCustomHud.Function<Number>> numbermap,
        HashMap<String, MprisCustomHud.Function<List<String>>> listmap
    ) {
        //? if <=1.21.10 {
        CustomHudRegistry.registerElement("mpris_album_art", (f, c) -> {
            return new AlbumArtElement(f);
        });

        for (String key : stringmap.keySet()) {
            CustomHudRegistry.registerElement(key,
                (f, c) -> Flags.wrap(new StringSupplierElement(() -> {
                    String value = stringmap.get(key).run();
                    return value.isEmpty() ? null : value;
                }), f));
        }

        for (String key : boolmap.keySet()) {
            CustomHudRegistry.registerElement(key, (f, c) -> Flags.wrap(new BooleanSupplierElement(() -> boolmap.get(key).run()), f));
        }

        for (String key : listmap.keySet()) {
            CustomHudRegistry.registerElement(key, (f, c) -> Flags.wrap(new StringSupplierElement(() -> String.join(", ", listmap.get(key).run())), f));
        }

        for (String key : numbermap.keySet()) {
            CustomHudRegistry.registerElement(key, (f, c) -> {
                if (f.formatted) {
                    return new StringSupplierElement(() -> MprisCustomHud.formatMicro(numbermap.get(key).run()));
                } else {
                    return new NumberSupplierElement(() -> numbermap.get(key).run(), f);
                }
            });
        }

        MprisCustomHud.log("Registered CustomHud variables");
        //?}
    }

    //? if <=1.21.10 {
    private static class AlbumArtElement extends IconElement {
        private final boolean formatted;
        private ResourceLocation id;
        private int height;

        private AlbumArtElement(Flags flags) {
            super(flags, 32);
            this.formatted = flags.formatted;
            update(MprisCustomHud.getCurrentPlayerInfo().metadata.album_art);
        }

        private void update(AlbumArt albumArt) {
            id = albumArt.getId();
            height = formatted ? (int) (width * albumArt.getHeight() / albumArt.getWidth()) : width;
        }

        @Override
        public void render(GuiGraphics context, RenderPiece piece) {
            if (System.currentTimeMillis() % 20 == 0) {
                update(MprisCustomHud.getCurrentPlayerInfo().metadata.album_art);
            }
            rotate(context.pose().pushMatrix(), width, height);
            context.blit(RenderPipelines.GUI_TEXTURED, id, piece.x + shiftX, piece.y + shiftY, 0, 0, width, height, width, height);
            context.pose().popMatrix();
        }
    }
    //?}
}
