package technicfan.mpriscustomhud.mod_support;

import java.util.List;

import java.util.HashMap;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.11 {
/*
import dev.ngspace.hudder.api.functionsandconsumers.FunctionAndConsumerAPI;
import dev.ngspace.hudder.api.variableregistry.DataVariableRegistry;
import dev.ngspace.hudder.api.variableregistry.VariableTypes;
import dev.ngspace.hudder.main.HudderRenderer;
import dev.ngspace.hudder.uielements.AUIElement;
*/
//?} else {
import io.github.ngspace.hudder.main.HudderRenderer;
import io.github.ngspace.hudder.uielements.AUIElement;
import io.github.ngspace.hudder.compilers.utils.functionandconsumerapi.FunctionAndConsumerAPI;
//? if >=1.21.9 {
import io.github.ngspace.hudder.data_management.api.DataVariableRegistry;
import io.github.ngspace.hudder.data_management.api.VariableTypes;
//?} else {
/*import io.github.ngspace.hudder.data_management.ObjectDataAPI;*/
//?}
//?}
import technicfan.mpriscustomhud.MprisCustomHud;
import technicfan.mpriscustomhud.PlayerInfo.AlbumArt;

public class HudderSupport {
    public static void register(
        HashMap<String, MprisCustomHud.Function<String>> stringmap,
        HashMap<String, MprisCustomHud.Function<Boolean>> boolmap,
        HashMap<String, MprisCustomHud.Function<Number>> numbermap,
        HashMap<String, MprisCustomHud.Function<List<String>>> listmap
    ) {
        //? if >=1.21.9 {
        DataVariableRegistry.registerVariable(k -> true, VariableTypes.BOOLEAN, "has_mpris");
        DataVariableRegistry.registerVariable(k ->
            MprisCustomHud.getCurrentPlayerInfo().isEmpty() ? null : MprisCustomHud.getCurrentPlayerInfo(),
                VariableTypes.OBJECT, "mpris_player_info");
        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getAvailablePlayers(), VariableTypes.OBJECT, "mpris_players");

        for (String key : stringmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> {
                String value = stringmap.get(key).run();
                return value.isEmpty() ? null : value;
            }, VariableTypes.STRING, key);
        }

        for (String key : boolmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> boolmap.get(key).run(), VariableTypes.BOOLEAN, key);
        }

        for (String key : listmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> listmap.get(key).run(), VariableTypes.OBJECT, key);
        }

        for (String key : numbermap.keySet()) {
            DataVariableRegistry.registerVariable(k -> numbermap.get(key).run(), VariableTypes.NUMBER, key);
        }
        //?} else {
        /*
        ObjectDataAPI.addObjectGetter(k -> k.equals("has_mpris") ? true : null);
        ObjectDataAPI.addObjectGetter(k ->
            k.equals("mpris_player_info") && !MprisCustomHud.getCurrentPlayerInfo().isEmpty() ? MprisCustomHud.getCurrentPlayerInfo() : null);
        ObjectDataAPI.addObjectGetter(k -> k.equals("mpris_players") ? MprisCustomHud.getAvailablePlayers() : null);

        for (String key : stringmap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> {
                if (!k.equals(key)) return null;
                String value = stringmap.get(key).run();
                return value.isEmpty() ? null : value;
            });
        }

        for (String key : boolmap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> k.equals(key) ? boolmap.get(key).run() : null);
        }

        for (String key : listmap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> k.equals(key) ? listmap.get(key).run() : null);
        }

        for (String key : numbermap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> k.equals(key) ? numbermap.get(key).run() : null);
        }
        */
        //?}

        FunctionAndConsumerAPI.getInstance().registerConsumer((ui, c, args) -> {
            AlbumArt albumArt;
            Object id = args[0].asType(Object.class);
            if (id instanceof AlbumArt art) {
                albumArt = art;
            } else if (id instanceof String name) {
                albumArt = MprisCustomHud.getPlayerInfoOrEmpty(name).metadata.album_art;
            } else {
                throw new IllegalArgumentException("First argument has to be either String or AlbumArt");
            }
            ui.addUIElement(new AlbumArtElement(albumArt, args[1].asInt(), args[2].asInt(), args[3].asInt(), args[4].asInt()));
        }, "mpris_album_art");

        FunctionAndConsumerAPI.getInstance().registerFunction((ui, c, args) -> {
                if (args.length < 1) {
                    return null;
                } else {
                    return MprisCustomHud.getPlayerInfo(args[0].asString());
                }
        }, "getPlayerInfo");

        MprisCustomHud.log("Registered Hudder variables and functions");
    }

    private static class AlbumArtElement extends AUIElement {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final ResourceLocation id;

        public AlbumArtElement(AlbumArt albumArt, int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            if (width == 0 && height == 0) {
                this.height = albumArt.getHeight();
                this.width = albumArt.getWidth();
            } else if (width == 0) {
                this.height = height;
                this.width = (int) (height * albumArt.getWidth() / albumArt.getHeight());
            } else if (height == 0) {
                this.width = width;
                this.height = (int) (width * albumArt.getHeight() / albumArt.getWidth());
            } else {
                this.height = height;
                this.width = width;
            }
            this.id = albumArt.getId();
        }

        @Override
        public void renderElement(GuiGraphics context, HudderRenderer renderer, DeltaTracker delta) {
            context.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, width, height, width, height);
        }
    }
}
