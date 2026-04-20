package technicfan.mpriscustomhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import technicfan.mpriscustomhud.mod_support.ModSupport;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MprisCustomHud implements ClientModInitializer {
    public static final String MOD_ID = "mpriscustomhud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json").toFile();
    private static Config CONFIG = new Config();

    private static final long microToMs = 1000L;
    private static final String busPrefix = "org.mpris.MediaPlayer2.";
    private static final PlayerInfo emptyPlayerInfo = new PlayerInfo();

    protected static DBus dbus;
    protected static DBusConnection conn;
    private static AutoCloseable nameHandler, propertiesHandler, seekedHandler;
    private static ConcurrentHashMap<String, PlayerInfo> players = new ConcurrentHashMap<>();
    private static PlayerInfo currentPlayerInfo = emptyPlayerInfo;

    public static interface Function<T> {
        T run();
    }

    @Override
    public void onInitializeClient() {
        loadConfig();

        try {
            conn = DBusConnectionBuilder.forSessionBus().build();
            dbus = conn.getRemoteObject("org.freedesktop.DBus", "/", DBus.class);
            for (String name : dbus.ListNames()) {
                if (name.startsWith(busPrefix)) {
                    PlayerInfo player = PlayerInfo.of(name, true);
                    if (name.equals(CONFIG.preferred)
                            || (currentPlayerInfo.isEmpty() && !CONFIG.onlyPreferred)) {
                        currentPlayerInfo = player;
                    }
                    players.put(name, player);
                    CompletableFuture.runAsync(() -> AlbumArtManager.loadAlbumArt(name, player.metadata.art_url));
                }
            }
            ModSupport.register(getStringMap(), getBoolMap(), getNumberMap(), getListMap());
            // listen for name owner changes to reset the values in case the player
            // terminates
            nameHandler = conn.addSigHandler(NameOwnerChanged.class, new NameOwnerChangedHandler());
            // listen for music Properties changess (like metadata, shuffle status, ...)
            propertiesHandler = conn.addSigHandler(PropertiesChanged.class, new PropChangedHandler());
            // listen for progress jumps
            seekedHandler = conn.addSigHandler(Player.Seeked.class, new SeekedHandler());
        } catch (Exception e) {
            LOGGER.error(e.toString(), e.fillInStackTrace());
        }
    }

    public static PlayerInfo getPlayerInfo(String name) {
        return players.get(busPrefix + name);
    }

    public static PlayerInfo getCurrentPlayerInfo() {
        return currentPlayerInfo;
    }

    public static List<String> getAvailablePlayers() {
        return players.keySet().stream().map(s -> s.substring(23)).toList();
    }

    public static String formatMicro(Number ms) {
        long sec = ms.longValue() / microToMs;
        return String.format("%s%02d:%02d", sec / 3600 > 0 ? String.format("%02d:", sec / 3600) : "",
                sec / 60 % 60, sec % 60);
    }

    public static void log(String msg) {
        LOGGER.info("[MprisCustomHud] {}", msg);
    }

    protected static void setPreferred(String preferred) {
        if (preferred.equals("None")) {
            preferred = "";
        }
        if (!CONFIG.preferred.equals(busPrefix + preferred)) {
            CONFIG = CONFIG.setPreferred(busPrefix + preferred);
            if (players.containsKey(CONFIG.preferred)) {
                currentPlayerInfo = players.get(CONFIG.preferred);
            } else if (currentPlayerInfo.isEmpty()) {
                if (!CONFIG.onlyPreferred)
                    cyclePlayers();
            } else {
                if (CONFIG.onlyPreferred) {
                    currentPlayerInfo = emptyPlayerInfo;
                }
            }
            saveConfig();
        }
    }

    protected static void setOnlyPreferred(boolean onlyPreferred) {
        if (CONFIG.onlyPreferred != onlyPreferred) {
            CONFIG = CONFIG.setOnlyPreferred(onlyPreferred);
            if (!CONFIG.preferred.equals(currentPlayerInfo.busname)) {
                if (players.containsKey(CONFIG.preferred)) {
                    currentPlayerInfo = players.get(CONFIG.preferred);
                } else {
                    if (CONFIG.onlyPreferred) {
                        currentPlayerInfo = emptyPlayerInfo;
                    } else {
                        cyclePlayers();
                    }
                }
            }
            saveConfig();
        }
    }

    protected static String getCurrentPlayerName() {
        return currentPlayerInfo.isEmpty() ? "None" : currentPlayerInfo.name;
    }

    protected static String getPreferred() {
        return CONFIG.preferred.length() > 23 ? CONFIG.preferred.substring(23) : "None";
    }

    protected static boolean getOnlyPreferred() {
        return CONFIG.onlyPreferred;
    }

    protected static void close() {
        try {
            log("Closing DBus connection and signal listeners");
            if (nameHandler != null)
                nameHandler.close();
            if (propertiesHandler != null)
                propertiesHandler.close();
            if (seekedHandler != null)
                seekedHandler.close();
            if (conn != null)
                conn.close();
        } catch (Exception e) {
            LOGGER.error(e.toString(), e.fillInStackTrace());
        }
    }

    protected static void refresh() {
        if (!currentPlayerInfo.isEmpty()) {
            currentPlayerInfo = currentPlayerInfo.refresh();
            players.put(currentPlayerInfo.busname, currentPlayerInfo);
        }
    }

    protected static void cyclePlayers() {
        if (players.size() > 0 && !CONFIG.onlyPreferred) {
            List<String> keys = new ArrayList<>(players.keySet());
            int index = keys.indexOf(currentPlayerInfo.busname);
            currentPlayerInfo = players.get(keys.get(index + 1 == keys.size() ? 0 : index + 1));
        } else {
            currentPlayerInfo = emptyPlayerInfo;
        }
    }

    private static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    CONFIG = new Gson().fromJson(reader, Config.class);
                    log("Loaded config");
                }
            } catch (IOException e) {
                LOGGER.error(e.toString(), e.fillInStackTrace());
            }
        }
    }

    private static void saveConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(gson.toJson(CONFIG));
        } catch (IOException e) {
            LOGGER.error(e.toString(), e.fillInStackTrace());
        }
    }

    private static HashMap<String, Function<String>> getStringMap() {
        HashMap<String, Function<String>> map = new HashMap<>();
        map.put("mpris_player", () -> currentPlayerInfo.name);
        map.put("mpris_track", () -> currentPlayerInfo.metadata.track);
        map.put("mpris_trackid", () -> currentPlayerInfo.metadata.trackid);
        map.put("mpris_album", () -> currentPlayerInfo.metadata.album);
        map.put("mpris_repeat", () -> currentPlayerInfo.repeat);
        map.put("mpris_artist", () -> currentPlayerInfo.metadata.artist);
        map.put("mpris_lyrics", () -> currentPlayerInfo.metadata.lyrics);
        map.put("mpris_created_at", () -> currentPlayerInfo.metadata.created_at);
        map.put("mpris_first_played", () -> currentPlayerInfo.metadata.first_played);
        map.put("mpris_last_played", () -> currentPlayerInfo.metadata.last_played);
        map.put("mpris_art_url", () -> currentPlayerInfo.metadata.art_url);
        map.put("mpris_url", () -> currentPlayerInfo.metadata.url);
        return map;
    }

    private static HashMap<String, Function<Boolean>> getBoolMap() {
        HashMap<String, Function<Boolean>> map = new HashMap<>();
        map.put("mpris_shuffle", () -> currentPlayerInfo.shuffle);
        map.put("mpris_playing", () -> currentPlayerInfo.playing);
        return map;
    }

    private static HashMap<String, Function<Number>> getNumberMap() {
        HashMap<String, Function<Number>> map = new HashMap<>();
        map.put("mpris_progress", () -> currentPlayerInfo.progress());
        map.put("mpris_duration", () -> currentPlayerInfo.metadata.duration);
        map.put("mpris_data_age", () -> currentPlayerInfo.isEmpty() ? 0 : currentPlayerInfo.metadata.data_age());
        map.put("mpris_rate", () -> currentPlayerInfo.rate);
        map.put("mpris_volume", () -> currentPlayerInfo.volume);
        map.put("mpris_bpm", () -> currentPlayerInfo.metadata.bpm);
        map.put("mpris_disc", () -> currentPlayerInfo.metadata.disc);
        map.put("mpris_number", () -> currentPlayerInfo.metadata.number);
        map.put("mpris_times_played", () -> currentPlayerInfo.metadata.times_played);
        map.put("mpris_auto_rating", () -> currentPlayerInfo.metadata.auto_rating);
        map.put("mpris_user_rating", () -> currentPlayerInfo.metadata.user_rating);
        return map;
    }

    private static HashMap<String, Function<List<String>>> getListMap() {
        HashMap<String, Function<List<String>>> map = new HashMap<>();
        map.put("mpris_artists", () -> currentPlayerInfo.metadata.artists);
        map.put("mpris_album_artists", () -> currentPlayerInfo.metadata.album_artists);
        map.put("mpris_comments", () -> currentPlayerInfo.metadata.comments);
        map.put("mpris_composers", () -> currentPlayerInfo.metadata.composers);
        map.put("mpris_genres", () -> currentPlayerInfo.metadata.genres);
        map.put("mpris_lyricists", () -> currentPlayerInfo.metadata.lyricists);
        return map;
    }

    private static class NameOwnerChangedHandler implements DBusSigHandler<DBus.NameOwnerChanged> {
        @Override
        public void handle(DBus.NameOwnerChanged signal) {
            if (signal.newOwner.isEmpty() && !signal.oldOwner.isEmpty() && players.containsKey(signal.name)) {
                players.remove(signal.name);
                AlbumArtManager.loadAlbumArt(signal.name, "");
                if (signal.name.equals(currentPlayerInfo.busname)) {
                    if (!CONFIG.onlyPreferred) {
                        if (players.containsKey(CONFIG.preferred)) {
                            currentPlayerInfo = players.get(CONFIG.preferred);
                        } else {
                            cyclePlayers();
                        }
                    } else {
                        currentPlayerInfo = emptyPlayerInfo;
                    }
                }
            } else if (!signal.newOwner.isEmpty() && signal.oldOwner.isEmpty()
                    && signal.name.startsWith(busPrefix)) {
                PlayerInfo player = PlayerInfo.of(signal.name, false);
                players.put(signal.name, player);
                if (signal.name.equals(CONFIG.preferred)) {
                    currentPlayerInfo = player;
                }
                if (currentPlayerInfo.isEmpty() && !CONFIG.onlyPreferred) {
                    cyclePlayers();
                }
            }
        }
    }

    private class SeekedHandler implements DBusSigHandler<Player.Seeked> {
        public void handle(Player.Seeked signal) {
            for (String busName : players.keySet()) {
                // check if signal came from the current player
                try {
                    if (dbus.GetNameOwner(busName).equals(signal.getSource())) {
                        PlayerInfo player = players.get(busName).seeked(signal);
                        players.put(busName, player);
                        if (busName.equals(currentPlayerInfo.busname)) {
                            currentPlayerInfo = player;
                        }
                        break;
                    }
                } catch (DBusExecutionException e) {
                }
            }
        }
    }

    private class PropChangedHandler extends AbstractPropertiesChangedHandler {
        @Override
        public void handle(PropertiesChanged signal) {
            for (String busName : players.keySet()) {
                // check if signal came from the current player
                try {
                    if (dbus.GetNameOwner(busName).equals(signal.getSource())) {
                        String art_url = players.get(busName).metadata.art_url;
                        PlayerInfo player = players.get(busName).propertiesChanged(signal);
                        players.put(busName, player);
                        if (busName.equals(currentPlayerInfo.busname)) {
                            currentPlayerInfo = player;
                        }
                        if (!player.metadata.art_url.equals(art_url)) {
                            AlbumArtManager.loadAlbumArt(busName, player.metadata.art_url);
                        }
                        break;
                    }
                } catch (DBusExecutionException e) {
                }
            }
        }
    }
}
