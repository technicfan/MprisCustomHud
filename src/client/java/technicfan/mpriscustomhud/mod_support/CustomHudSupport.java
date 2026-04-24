package technicfan.mpriscustomhud.mod_support;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

//? if <=1.21.11 {
import com.minenash.customhud.HudElements.icon.IconElement;
import com.minenash.customhud.HudElements.interfaces.HudElement;
import com.minenash.customhud.data.Flags;
import com.minenash.customhud.registry.CustomHudRegistry;
import com.minenash.customhud.registry.ParseContext;
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
    private static HashMap<String, Function<PlayerInfo, String>> strings = new HashMap();
    private static HashMap<String, Function<PlayerInfo, Boolean>> bools = new HashMap();
    private static HashMap<String, Function<PlayerInfo, Number>> numbers = new HashMap();
    private static HashMap<String, Function<PlayerInfo, Number>> times = new HashMap();

    private static void loadMaps() {
        strings.put("busname", p -> p.busname);
        strings.put("name", p -> p.name);
        strings.put("track", p -> p.metadata.track);
        strings.put("trackid", p -> p.metadata.trackid);
        strings.put("album", p -> p.metadata.album);
        strings.put("repeat", p -> p.repeat);
        strings.put("artist", p -> p.metadata.artist);
        strings.put("lyrics", p -> p.metadata.lyrics);
        strings.put("created_at", p -> p.metadata.created_at);
        strings.put("first_played", p -> p.metadata.first_played);
        strings.put("last_played", p -> p.metadata.last_played);
        strings.put("art_url", p -> p.metadata.art_url);
        strings.put("url", p -> p.metadata.url);
        bools.put("shuffle", p -> p.shuffle);
        bools.put("playing", p -> p.playing);
        bools.put("exists", p -> !p.isEmpty());
        bools.put("has_album_art", p -> p.metadata.album_art.exists());
        numbers.put("rate", p -> p.rate);
        numbers.put("volume", p -> p.volume);
        numbers.put("bpm", p -> p.metadata.bpm);
        numbers.put("disc", p -> p.metadata.disc);
        numbers.put("number", p -> p.metadata.number);
        numbers.put("times_played", p -> p.metadata.times_played);
        numbers.put("auto_rating", p -> p.metadata.auto_rating);
        numbers.put("user_rating", p -> p.metadata.user_rating);
        numbers.put("album_width", p -> p.metadata.album_art.width);
        numbers.put("album_height", p -> p.metadata.album_art.height);
        numbers.put("album_color", p -> p.metadata.album_art.color);
        times.put("progress", p -> p.progress());
        times.put("duration", p -> p.metadata.duration);
        times.put("data_age", p -> p.isEmpty() ? 0 : p.metadata.data_age());
    }

    private static Function<PlayerInfo, String> nullIfEmpty(Function<PlayerInfo, String> s) {
        return p -> {
            String v = s.apply(p);
            return v.isEmpty() ? null : v;
        };
    }

    private static HudElement format(Supplier sup, Function<PlayerInfo, Number> s, Flags f) {
        if (f.formatted) {
            return new Str<PlayerInfo>(sup, p -> MprisCustomHud.formatMicro(s.apply(p)));
        } else {
            return new Num(sup, s, f);
        }
    }

    private static HudElement getAttribute(UUID pid, Supplier<PlayerInfo> sup, String name, Flags flags) {
        if (name != null) {
            if (name.equals("album_art")) return new AlbumArtElement(pid, sup, flags);
            if (times.containsKey(name)) {
                return format(sup, times.get(name), flags);
            } else if (bools.containsKey(name)) {
                return Flags.wrap(new Bool(sup, bools.get(name)), flags);
            } else if (numbers.containsKey(name)) {
                return new Num(sup, numbers.get(name), flags);
            } else if (strings.containsKey(name)) {
                return Flags.wrap(new Str(sup, nullIfEmpty(strings.get(name))), flags);
            }
        }
        return null;
    }

    private static HudElement getHudElement(String k, String v, ParseContext c) {
        if (v.startsWith(k)) {
            String[] parts = v.split(" ");
            Flags f = Flags.parse(c.profile().name, c.line(), parts);
            parts = parts[0].split(":");
            String name = parts.length >= 2 ? parts[1] : null;
            return getAttribute(null, MprisCustomHud::getCurrentPlayerInfo, name, f);
        } else {
            return null;
        }
    }
    //?}

    public static void register(HashMap<String, Function<String, List<String>>> listmap) {
        //? if <=1.21.11 {
        loadMaps();
        CustomHudRegistry.registerList("mpris_players", "p", MprisCustomHud::getPlayers, (pid, sup, name, flags, context) -> getAttribute(pid, sup, name, flags));
        CustomHudRegistry.registerParser("mpris_player_info", (v, c) -> getHudElement("mpris_player_info", v, c));

        for (String key : listmap.keySet()) {
            CustomHudRegistry.registerList(key, key.substring(7, 10), () -> listmap.get(key).apply(null), (pid, sup, name, flags, context) -> Flags.wrap(new Str(sup, s -> s), flags));
        }

        MprisCustomHud.log("Registered CustomHud variables");
        //?}
    }

    //? if <=1.21.11 {
    private static class AlbumArtElement extends IconElement {
        private final boolean formatted;
        private final Supplier<PlayerInfo> sup;

        private AlbumArtElement(UUID pid, Supplier<PlayerInfo> sup, Flags flags) {
            super(flags, 10);
            this.formatted = flags.formatted;
            this.sup = sup;
            this.providerID = pid;
        }

        @Override
        public void render(GuiGraphics context, RenderPiece piece) {
            AlbumArt albumArt = piece.value != null ? ((PlayerInfo) piece.value).metadata.album_art : sup.get() != null ? sup.get().metadata.album_art : null;
            if (albumArt == null) {
                return;
            }
            ResourceLocation id = albumArt.getId();
            int height = formatted ? (int) (width * albumArt.getHeight() / albumArt.getWidth()) : width;

            //? if >=1.21.6 {
            rotate(context.pose().pushMatrix(), width, height);
            context.blit(RenderPipelines.GUI_TEXTURED, id, piece.x + shiftX, piece.y + shiftY, 0, 0, width, height, width, height);
            context.pose().popMatrix();
            //?} else {
            /*com.mojang.blaze3d.vertex.PoseStack matrices = context.pose();
            matrices.pushPose();
            rotate(matrices, width, height);*/
            //? if >=1.21.2 {
            /*context.blit(net.minecraft.client.renderer.RenderType::guiTexturedOverlay, id, piece.x + shiftX, piece.y + shiftY, 0, 0, width, height, width, height);*/
            //?} else
            /*context.blit(id, piece.x + shiftX, piece.y + shiftY, 0, 0, width, height, width, height);*/
            //?}
        }
    }
    //?}
}
