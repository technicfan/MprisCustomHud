package technicfan.mpriscustomhud.mod_support;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import technicfan.mpriscustomhud.MprisCustomHud;
import technicfan.mpriscustomhud.PlayerInfo;

public class ModSupport {
    protected static HashMap<String, Function<PlayerInfo, String>> strings = new HashMap<>();
    protected static HashMap<String, Function<PlayerInfo, Boolean>> bools = new HashMap<>();
    protected static HashMap<String, Function<PlayerInfo, Number>> numbers = new HashMap<>();
    protected static HashMap<String, Function<PlayerInfo, Number>> times = new HashMap<>();
    protected static HashMap<String, Supplier<List<String>>> lists = new HashMap<>();

    private static void init() {
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
        lists.put("mpris_artists", () -> MprisCustomHud.getCurrentPlayerInfo().metadata.artists);
        lists.put("mpris_album_artists", () -> MprisCustomHud.getCurrentPlayerInfo().metadata.album_artists);
        lists.put("mpris_comments", () -> MprisCustomHud.getCurrentPlayerInfo().metadata.comments);
        lists.put("mpris_composers", () -> MprisCustomHud.getCurrentPlayerInfo().metadata.composers);
        lists.put("mpris_genres", () -> MprisCustomHud.getCurrentPlayerInfo().metadata.genres);
        lists.put("mpris_lyricists", () -> MprisCustomHud.getCurrentPlayerInfo().metadata.lyricists);
    }

    protected static Function<PlayerInfo, String> nullIfEmpty(Function<PlayerInfo, String> s) {
        return p -> {
            String v = s.apply(p);
            return v.isEmpty() ? null : v;
        };
    }

    public static void register() {
        init();
        if (FabricLoader.getInstance().getModContainer("custom_hud").isPresent()) {
            CustomHudSupport.register();
        }
        if (FabricLoader.getInstance().getModContainer("hudder").isPresent()) {
            HudderSupport.register();
        }
    }
}
