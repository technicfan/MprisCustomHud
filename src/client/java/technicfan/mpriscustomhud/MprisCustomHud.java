package technicfan.mpriscustomhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
import java.util.function.Function;
import java.util.function.Supplier;

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

    protected static DBus dbus;
    protected static DBusConnection conn;
    private static AutoCloseable nameHandler, propertiesHandler, seekedHandler;
    private static ConcurrentHashMap<String, PlayerInfo> players = new ConcurrentHashMap<>();
    private static PlayerInfo currentPlayerInfo = PlayerInfo.EMPTY;

    @Override
    public void onInitializeClient() {
        loadConfig();
        AlbumArtManager.init(Minecraft.getInstance());
        // this is to prevent theoretical missing texture that could
        // show up in hud if an album art would be unloaded in the middle of a tick
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            AlbumArtManager.release(client.getTextureManager());
        });

        try {
            conn = DBusConnectionBuilder.forSessionBus().build();
            dbus = conn.getRemoteObject("org.freedesktop.DBus", "/", DBus.class);
            loadPlayers(players);
            ModSupport.register(getListMap());
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

    private static void loadPlayers(ConcurrentHashMap<String, PlayerInfo> players) {
        for (String name : dbus.ListNames()) {
            if (name.startsWith(busPrefix)) {
                PlayerInfo player = PlayerInfo.of(name, true);
                if (name.equals(CONFIG.preferred)
                        || (currentPlayerInfo.isEmpty() && !CONFIG.onlyPreferred)) {
                    currentPlayerInfo = player;
                }
                players.put(name, player);
                CompletableFuture.runAsync(() -> {
                    PlayerInfo updatedPlayer = AlbumArtManager.loadAlbumArt(player);
                    players.put(name, updatedPlayer);
                    if (player == currentPlayerInfo) {
                        currentPlayerInfo = updatedPlayer;
                    }
                });
            }
        }
    }

    public static PlayerInfo getPlayerInfo(String name) {
        return players.get(name.length() > 23 ? name : busPrefix + name);
    }

    public static PlayerInfo getPlayerInfoOrEmpty(String name) {
        return players.getOrDefault(name.length() > 23 ? name : busPrefix + name, PlayerInfo.EMPTY);
    }

    public static PlayerInfo getCurrentPlayerInfo() {
        return currentPlayerInfo;
    }

    public static List<String> getAvailablePlayers() {
        return players.keySet().stream().map(s -> s.substring(23)).toList();
    }

    public static List<PlayerInfo> getPlayers() {
        return players.values().stream().toList();
    }

    public static String formatMicro(Number ms) {
        long sec = ms.longValue() / microToMs;
        return String.format("%s%02d:%02d", sec / 3600 > 0 ? String.format("%02d:", sec / 3600) : "",
                sec / 60 % 60, sec % 60);
    }

    public static void log(String msg) {
        LOGGER.info("[MprisCustomHud] {}", msg);
    }

    public static void warn(String msg) {
        LOGGER.warn("[MprisCustomHud] {}", msg);
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
                    currentPlayerInfo = PlayerInfo.EMPTY;
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
                        currentPlayerInfo = PlayerInfo.EMPTY;
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
        ConcurrentHashMap<String, PlayerInfo> refreshed = new ConcurrentHashMap<>();
        loadPlayers(refreshed);
        players = refreshed;
    }

    protected static void cyclePlayers() {
        if (players.mappingCount() > 0 && !CONFIG.onlyPreferred) {
            List<String> keys = new ArrayList<>(players.keySet());
            int index = keys.indexOf(currentPlayerInfo.busname);
            currentPlayerInfo = players.get(keys.get(index + 1 == keys.size() ? 0 : index + 1));
        } else {
            currentPlayerInfo = PlayerInfo.EMPTY;
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

    private static HashMap<String, Supplier<List<String>>> getListMap() {
        HashMap<String, Supplier<List<String>>> map = new HashMap<>();
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
                AlbumArtManager.remove(players.remove(signal.name).metadata.album_art);
                if (signal.name.equals(currentPlayerInfo.busname)) {
                    if (!CONFIG.onlyPreferred) {
                        if (players.containsKey(CONFIG.preferred)) {
                            currentPlayerInfo = players.get(CONFIG.preferred);
                        } else {
                            cyclePlayers();
                        }
                    } else {
                        currentPlayerInfo = PlayerInfo.EMPTY;
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
                            player = AlbumArtManager.loadAlbumArt(player);
                            if (players.containsKey(busName)) {
                                if (players.put(busName, player) == currentPlayerInfo) {
                                    currentPlayerInfo = player;
                                }
                            }
                        }
                        break;
                    }
                } catch (DBusExecutionException e) {
                }
            }
        }
    }
}
