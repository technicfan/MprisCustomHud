package technicfan.mpriscustomhud.mod_support;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

//? if <=1.21.11 {
import com.minenash.customhud.HudElements.icon.IconElement;
import com.minenash.customhud.HudElements.interfaces.HudElement;
import com.minenash.customhud.data.Flags;
import com.minenash.customhud.registry.CustomHudRegistry;
import com.minenash.customhud.render.RenderPiece;
import com.minenash.customhud.HudElements.FuncElements.Bool;
import com.minenash.customhud.HudElements.FuncElements.Num;
import com.minenash.customhud.HudElements.FuncElements.Str;

import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.2 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.resources.ResourceLocation;

import technicfan.mpriscustomhud.PlayerInfo.AlbumArt;
//?}
import technicfan.mpriscustomhud.MprisCustomHud;
import technicfan.mpriscustomhud.PlayerInfo;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CustomHudSupport {
    //? if <=1.21.11 {
    private static HudElement format(Supplier sup, Function<PlayerInfo, Number> s, Flags f) {
        if (f.formatted) {
            return new Str<PlayerInfo>(sup, p -> MprisCustomHud.formatMicro(s.apply(p)));
        } else {
            return new Num(sup, s, f);
        }
    }

    private static HudElement getPlayerAttribute(UUID pid, Supplier<PlayerInfo> sup, String name, Flags flags) {
        if (name != null) {
            if (name.equals("album_art")) return new AlbumArtElement(pid, sup, flags);
            if (ModSupport.times.containsKey(name)) {
                return format(sup, ModSupport.times.get(name), flags);
            } else if (ModSupport.bools.containsKey(name)) {
                return Flags.wrap(new Bool(sup, ModSupport.bools.get(name)), flags);
            } else if (ModSupport.numbers.containsKey(name)) {
                return new Num(sup, ModSupport.numbers.get(name), flags);
            } else if (ModSupport.strings.containsKey(name)) {
                return Flags.wrap(new Str(sup, ModSupport.nullIfEmpty(ModSupport.strings.get(name))), flags);
            }
        }
        return null;
    }
    //?}

    public static void register() {
        //? if <=1.21.11 {
        CustomHudRegistry.registerList("mpris_players", "p", MprisCustomHud::getPlayers, (pid, sup, name, flags, context) -> getPlayerAttribute(pid, sup, name, flags));
        CustomHudRegistry.registerParser("mpris_player", (v, c) -> {
            String[] parts = v.split(" ")[0].split(":");
            if (!parts[0].startsWith("mpris_players") && parts[0].startsWith("mpris_")) {
                parts[0] = parts[0].substring(6);
                Flags f = Flags.parse(c.profile().name, c.line(), v.split(" "));
                if (parts.length <= 2 || !parts[0].equals("player")) {
                    return getPlayerAttribute(null, MprisCustomHud::getCurrentPlayerInfo, parts.length == 2 ? parts[1] : parts[0].equals("player") ? null : parts[0], f);
                } else {
                    return getPlayerAttribute(null, () -> MprisCustomHud.getPlayerInfoOrEmpty(parts[1]), parts[2], f);
                }
            } else {
                return null;
            }
        });

        ModSupport.lists.forEach((v, f) -> {
            CustomHudRegistry.registerList(v, v.substring(7, 10), () -> f.get(), (pid, sup, name, flags, context) -> Flags.wrap(new Str(sup, s -> s), flags));
        });

        MprisCustomHud.log("Registered CustomHud variables");
        //?}
    }

    //? if <=1.21.11 {
    private static class AlbumArtElement extends IconElement {
        private final boolean formatted;
        private final int drawWidth;
        private final Supplier<PlayerInfo> sup;

        private AlbumArtElement(UUID pid, Supplier<PlayerInfo> sup, Flags flags) {
            super(flags, 10);
            this.formatted = flags.formatted;
            this.sup = sup;
            this.drawWidth = (int) (9 * scale);
            this.providerID = pid;
        }

        @Override
        public void render(GuiGraphics context, RenderPiece piece) {
            AlbumArt albumArt = piece.value != null ? ((PlayerInfo) piece.value).metadata.album_art : sup.get() != null ? sup.get().metadata.album_art : null;
            if (albumArt == null) {
                return;
            }
            ResourceLocation id = albumArt.getId();
            int height = formatted ? (int) (drawWidth * albumArt.getHeight() / albumArt.getWidth()) : drawWidth;
            float y = piece.y;
            if (!referenceCorner)
                y -= (drawWidth-9)/2;

            //? if >=1.21.6 {
            context.pose().pushMatrix();
            rotate(context.pose(), drawWidth, height);
            context.pose().translate(piece.x + shiftX + 0.6f, y + shiftY - 1);
            context.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, drawWidth, height, drawWidth, height);
            context.pose().popMatrix();
            //?} else {
            /*com.mojang.blaze3d.vertex.PoseStack matrices = context.pose();
            matrices.pushPose();
            matrices.translate(piece.x + shiftX + 0.6f, y + shiftY - 1, 0);
            rotate(matrices, drawWidth, height);
            //? if >=1.21.2 {
            context.blit(net.minecraft.client.renderer.RenderType::guiTexturedOverlay, id, 0, 0, 0, 0, drawWidth, height, drawWidth, height);
            //?} else
            context.blit(id, 0, 0, 0, 0, drawWidth, height, drawWidth, height);
            matrices.popPose();*/
            //?}
        }
    }
    //?}
}
