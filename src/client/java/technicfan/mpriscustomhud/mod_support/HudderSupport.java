package technicfan.mpriscustomhud.mod_support;

//? if >=1.21.1 {
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.2 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
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
//?}

public class HudderSupport {
    public static void register() {
        //? if >=1.21.1 {
        //? if >=1.21.9 {
        DataVariableRegistry.registerVariable(k -> true, VariableTypes.BOOLEAN, "has_mpris");
        DataVariableRegistry.registerVariable(k ->
            MprisCustomHud.getCurrentPlayerInfo().isEmpty() ? null : MprisCustomHud.getCurrentPlayerInfo(),
                VariableTypes.OBJECT, "mpris_player_info");
        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getPlayers(), VariableTypes.OBJECT, "mpris_players");
        //?} else {
        /*
        ObjectDataAPI.addObjectGetter(k -> k.equals("has_mpris") ? true : null);
        ObjectDataAPI.addObjectGetter(k ->
            k.equals("mpris_player_info") && !MprisCustomHud.getCurrentPlayerInfo().isEmpty() ? MprisCustomHud.getCurrentPlayerInfo() : null);
        ObjectDataAPI.addObjectGetter(k -> k.equals("mpris_players") ? MprisCustomHud.getPlayers() : null);*/
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
        //?}
    }

    //? if >=1.21.1 {
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
            //? if >=1.21.6 {
            context.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, width, height, width, height);
            //?} else if >=1.21.2 {
            /*context.blit(net.minecraft.client.renderer.RenderType::guiTexturedOverlay, id, x, y, 0, 0, width, height, width, height);*/
            //?} else
            /*context.blit(id, x, y, 0, 0, width, height, width, height);*/
        }
    }
    //?}
}
