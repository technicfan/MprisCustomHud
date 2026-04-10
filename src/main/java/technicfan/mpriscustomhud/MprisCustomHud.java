package technicfan.mpriscustomhud;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
//? if <=1.21.10 {
/*import technicfan.mpriscustomhud.mod_support.CustomHudSupport;*/
//?}
import technicfan.mpriscustomhud.mod_support.HudderSupport;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MprisCustomHud implements ModInitializer {
    public static final String MOD_ID = "mpriscustomhud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json").toFile();
    private static Config CONFIG = new Config();

    private static final long microToMs = 1000L;
    public static final String busPrefix = "org.mpris.MediaPlayer2.";
    private static final PlayerInfo emptyPlayerInfo = PlayerInfo.empty();

    private static ConcurrentHashMap<String, String> stringmap = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Boolean> boolmap = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Number> numbermap = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, List<String>> listmap = new ConcurrentHashMap<>();

    protected static DBus dbus;
    protected static DBusConnection conn;
    private static AutoCloseable nameHandler, propertiesHandler, seekedHandler;
    private static HashMap<String, PlayerInfo> players = new HashMap<>();
    private static String currentBusName;

    private static void updateMaps(PlayerInfo info) {
        stringmap.put("mpris_player", info.name);
        stringmap.put("mpris_track", info.metadata.track);
        stringmap.put("mpris_trackid", info.metadata.trackid);
        stringmap.put("mpris_album", info.metadata.album);
        stringmap.put("mpris_repeat", info.repeat);
        stringmap.put("mpris_artist", info.metadata.artist);

        boolmap.put("mpris_shuffle", info.shuffle);
        boolmap.put("mpris_playing", info.playing);

        numbermap.put("mpris_duration", info.metadata.duration);
        numbermap.put("mpris_rate", info.rate);

        listmap.put("mpris_artists", info.metadata.artists);
    }

    public static PlayerInfo getPlayerInfo(String name) {
        return players.get(busPrefix + name);
    }

    public static PlayerInfo getCurrentPlayerInfo() {
        return currentBusName != null ? players.get(currentBusName) : null;
    }

    public static long getCurrentProgress() {
        return currentBusName != null ? players.get(currentBusName).progress() : 0;
    }

    public static long getCurrentDataAge() {
        return currentBusName != null ? players.get(currentBusName).metadata.data_age() : 0;
    }

    public static boolean hasCurrentPlayerInfo() {
        return currentBusName != null;
    }

    public static String formatMicro(Number micro) {
        long ms = micro.longValue() / microToMs;
        return String.format("%s%02d:%02d", ms / 3600 > 0 ? String.format("%02d:", ms / 3600) : "",
                ms / 60 % 60, ms % 60);
    }

    protected static void setPreferred(String preferred) {
        if (preferred.equals("None")) {
            preferred = "";
        }
        if (!CONFIG.preferred.equals(busPrefix + preferred)) {
            CONFIG = CONFIG.setPreferred(busPrefix + preferred);
            if (players.containsKey(CONFIG.preferred)) {
                currentBusName = CONFIG.preferred;
                updateMaps(players.get(currentBusName));
            } else if (!CONFIG.onlyPreferred && currentBusName == null) {
                cyclePlayers();
            } else {
                updateMaps(emptyPlayerInfo);
            }
            saveConfig();
        }
    }

    protected static void setOnlyPreferred(boolean onlyPreferred) {
        if (CONFIG.onlyPreferred != onlyPreferred) {
            CONFIG = CONFIG.setOnlyPreferred(onlyPreferred);
            if (!CONFIG.preferred.equals(currentBusName)) {
                if (players.containsKey(CONFIG.preferred)) {
                    currentBusName = CONFIG.preferred;
                    updateMaps(players.get(currentBusName));
                } else {
                    if (CONFIG.onlyPreferred) {
                        currentBusName = null;
                        updateMaps(emptyPlayerInfo);
                    } else {
                        cyclePlayers();
                    }
                }
            }
            saveConfig();
        }
    }

    protected static String getPlayer() {
        return currentBusName == null ? "None" : players.get(currentBusName).name;
    }

    protected static String getPreferred() {
        return CONFIG.preferred.length() > 23 ? CONFIG.preferred.substring(23) : "None";
    }

    protected static boolean getOnlyPreferred() {
        return CONFIG.onlyPreferred;
    }

    protected static List<String> getActivePlayers() {
        List<String> players = new ArrayList<>();
        if (dbus != null) {
            for (String name : dbus.ListNames()) {
                if (name.startsWith(busPrefix)) {
                    players.add(name);
                }
            }
        }
        return players;
    }

    private static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    CONFIG = new Gson().fromJson(reader, Config.class);
                    LOGGER.info("MPRIS CustomHud config loaded");
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

    @Override
    public void onInitialize() {
        loadConfig();

        try {
            conn = DBusConnectionBuilder.forSessionBus().build();
            dbus = conn.getRemoteObject("org.freedesktop.DBus", "/", DBus.class);
            List<String> activePlayers = getActivePlayers();
            if (!CONFIG.onlyPreferred && activePlayers.contains(CONFIG.preferred)) {
                currentBusName = CONFIG.preferred;
            }
            for (String name : activePlayers) {
                if (currentBusName == null && !CONFIG.onlyPreferred) {
                    currentBusName = name;
                }
                players.put(name, PlayerInfo.of(name, true));
            }
            if (currentBusName != null) {
                updateMaps(players.get(currentBusName));
            } else {
                updateMaps(emptyPlayerInfo);
            }
            //? if <=1.21.10 {
            /*
            if (FabricLoader.getInstance().getModContainer("custom_hud").isPresent())
                CustomHudSupport.register(stringmap, boolmap, numbermap, listmap);
            */
            //?}
            //? if >=1.21.9 {
            if (FabricLoader.getInstance().getModContainer("hudder").isPresent())
                HudderSupport.register(stringmap, boolmap, numbermap, listmap);
            //?}
            // listen for name owner changes to reset the values in case the player
            // terminates
            nameHandler = conn.addSigHandler(NameOwnerChanged.class, new NameOwnerChangedHandler());
            // listen for music Properties (like metadata, shuffle status, ...)
            propertiesHandler = conn.addSigHandler(PropertiesChanged.class, new PropChangedHandler());
            // listen for progress jumps for the current track
            seekedHandler = conn.addSigHandler(Player.Seeked.class, new SeekedHandler());
        } catch (DBusException e) {
            LOGGER.error(e.toString(), e.fillInStackTrace());
        }
    }

    protected static void close() {
        try {
            LOGGER.info("Closing DBus connection and signal listeners");
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
        if (currentBusName != null) {
            players.put(currentBusName, players.get(currentBusName).refresh());
            updateMaps(players.get(currentBusName));
        }
    }

    protected static void cyclePlayers() {
        if (players.size() > 0 && !CONFIG.onlyPreferred) {
            List<String> keys = new ArrayList<>(players.keySet());
            int index = currentBusName != null ? keys.indexOf(currentBusName) : -1;
            currentBusName = keys.get(index + 1 == keys.size() ? 0 : index + 1);
            updateMaps(players.get(currentBusName));
        } else {
            currentBusName = null;
            updateMaps(emptyPlayerInfo);
        }
    }

    protected static void playPause() {
        if (currentBusName != null) {
            Player player = players.get(currentBusName).getPlayer();
            if (player != null)
                player.PlayPause();
        }
    }

    protected static void play() {
        if (currentBusName != null) {
            Player player = players.get(currentBusName).getPlayer();
            if (player != null)
                player.Play();
        }
    }

    protected static void pause() {
        if (currentBusName != null) {
            Player player = players.get(currentBusName).getPlayer();
            if (player != null)
                player.Pause();
        }
    }

    protected static void next() {
        if (currentBusName != null) {
            Player player = players.get(currentBusName).getPlayer();
            if (player != null)
                player.Next();
        }
    }

    protected static void previous() {
        if (currentBusName != null) {
            Player player = players.get(currentBusName).getPlayer();
            if (player != null)
                player.Previous();
        }
    }

    private static class NameOwnerChangedHandler implements DBusSigHandler<DBus.NameOwnerChanged> {
        @Override
        public void handle(DBus.NameOwnerChanged signal) {
            if (signal.newOwner.isEmpty() && !signal.oldOwner.isEmpty() && players.containsKey(signal.name)) {
                players.remove(signal.name);
                if (signal.name.equals(currentBusName)) {
                    if (!CONFIG.onlyPreferred) {
                        if (players.containsKey(CONFIG.preferred)) {
                            currentBusName = CONFIG.preferred;
                            updateMaps(players.get(currentBusName));
                        } else {
                            cyclePlayers();
                        }
                    } else {
                        currentBusName = null;
                        updateMaps(emptyPlayerInfo);
                    }
                }
            } else if (!signal.newOwner.isEmpty() && signal.oldOwner.isEmpty()
                    && signal.name.startsWith(busPrefix)) {
                if (signal.name.equals(CONFIG.preferred)) {
                    currentBusName = signal.name;
                }
                players.put(signal.name, PlayerInfo.of(signal.name, false));
                if (currentBusName == null) {
                    cyclePlayers();
                } else if (currentBusName == signal.name) {
                    updateMaps(players.get(currentBusName));
                }
            }
        }
    }

    private class SeekedHandler implements DBusSigHandler<Player.Seeked> {
        public void handle(Player.Seeked signal) {
            for (String busName : players.keySet()) {
                // check if signal came from the current player
                if (dbus.GetNameOwner(busName).equals(signal.getSource())) {
                    players.put(busName, players.get(busName).seeked(signal));
                }
            }
        }
    }

    private class PropChangedHandler extends AbstractPropertiesChangedHandler {
        @Override
        public void handle(PropertiesChanged signal) {
            for (String busName : players.keySet()) {
                // check if signal came from the current player
                if (dbus.GetNameOwner(busName).equals(signal.getSource())) {
                    players.put(busName, players.get(busName).propertiesChanged(signal));
                    if (busName.equals(currentBusName)) {
                        updateMaps(players.get(busName));
                    }
                }
            }
        }
    }
}
