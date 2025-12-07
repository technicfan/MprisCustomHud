package technicfan.mpriscustomhud;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import oshi.util.tuples.Triplet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minenash.customhud.HudElements.supplier.BooleanSupplierElement;
import com.minenash.customhud.HudElements.supplier.SpecialSupplierElement;
import com.minenash.customhud.HudElements.supplier.StringSupplierElement;

import static com.minenash.customhud.data.Flags.wrap;
import static com.minenash.customhud.registry.CustomHudRegistry.registerElement;

public class MprisCustomHud implements ModInitializer {
    public static final String MOD_ID = "mpriscustomhud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static File CONFIG_FILE;
    private static MprisCustomHudConfig CONFIG = new MprisCustomHudConfig();

    private final static long microToMs = 1000L;
    private static double rate;

    private static Map<String, String> stringmap = new HashMap<>();
    private static Map<String, Boolean> boolmap = new HashMap<>();
    private static Map<String, Triplet<String, Number, Boolean>> specialmap = new HashMap<>();

    protected static DBus dbus;
    protected static DBusConnection conn;
    private static Map<String, PlayerInfo> players = new HashMap<>();
    private static String currentBusName;

    protected static void updateMaps(PlayerInfo info) {
        if (info.getBusName().equals(currentBusName)) {
            stringmap.put("mpris_track", info.getTrack());
            stringmap.put("mpris_track_id", info.getTrackId());
            stringmap.put("mpris_album", info.getAlbum());
            stringmap.put("mpris_loop", info.getLoop());
            stringmap.put("mpris_artist", info.getArtist());
            stringmap.put("mpris_artists", info.getArtists());

            boolmap.put("mpris_shuffle", info.getShuffle());
            boolmap.put("mpris_playing", info.getPlaying());

            specialmap.put("mpris_progress", new Triplet<>(info.getProgress(), info.getPositionMs() / microToMs,
                    info.getPositionMs() / microToMs > 0));
            specialmap.put("mpris_duration", new Triplet<>(info.getDuration(), info.getLengthMs() / microToMs,
                    info.getLengthMs() / microToMs > 0));
            specialmap.put("mpris_rate", new Triplet<>(String.format("%.2f", rate), rate, rate > 0.0));
        }
    }

    protected static void updatePostionMap(String busName, String progress, long position) {
        if (busName.equals(currentBusName)) {
            specialmap.put("mpris_progress", new Triplet<>(progress, position, position > 0));
        }
    }

    private static void initCustomHud() {
        for (String key : stringmap.keySet()) {
            registerElement(key,
                    (f, c) -> wrap(
                            new StringSupplierElement(() -> stringmap.get(key).isEmpty() ? null : stringmap.get(key)),
                            f));
        }

        for (String key : boolmap.keySet()) {
            registerElement(key, (f, c) -> wrap(new BooleanSupplierElement(() -> boolmap.get(key)), f));
        }

        for (String key : specialmap.keySet()) {
            registerElement(key, (f, c) -> new SpecialSupplierElement(SpecialSupplierElement.of(
                    () -> specialmap.get(key).getA(),
                    () -> specialmap.get(key).getB(),
                    () -> specialmap.get(key).getC())));
        }
    }

    protected static void setFilter(String filter) {
        if (filter.equals("None")) {
            filter = "";
        }
        if (!CONFIG.getFilter().equals(filter)) {
            String oldFilter = "org.mpris.MediaPlayer2." + CONFIG.getFilter();
            if (players.containsKey(oldFilter) && !getActivePlayers().contains(oldFilter)) {
                players.get(oldFilter).destroy();
                players.remove(oldFilter);
            }
            CONFIG.setFilter(filter);
            CONFIG.setPreferred("");
            currentBusName = "org.mpris.MediaPlayer2." + filter;
            if (!getActivePlayers().contains(currentBusName)) {
                updateMaps(new PlayerInfo(currentBusName, true));
            } else {
                updateMaps(players.get(currentBusName));
            }
            saveToFile();
        }
    }

    protected static void setPreferred(String preferred) {
        if (preferred.equals("None")) {
            preferred = "";
        }
        if (!CONFIG.getPreferred().equals(preferred)) {
            String oldFilter = "org.mpris.MediaPlayer2." + CONFIG.getFilter();
            if (players.containsKey(oldFilter) && !getActivePlayers().contains(oldFilter)) {
                players.get(oldFilter).destroy();
                players.remove(oldFilter);
            }
            CONFIG.setPreferred(preferred);
            CONFIG.setFilter("");
            if (players.containsKey("org.mpris.MediaPlayer2." + preferred)) {
                currentBusName = "org.mpris.MediaPlayer2." + preferred;
                updateMaps(players.get(currentBusName));
            } else if (!oldFilter.endsWith(".")) {
                cyclePlayers();
            }
            saveToFile();
        }
    }

    protected static void refresh() {
        if (players.containsKey(currentBusName)) {
            players.get(currentBusName).refreshValues();
        }
    }

    protected static List<String> getActivePlayers() {
        List<String> players = new ArrayList<>();
        if (dbus != null) {
            for (String name : dbus.ListNames()) {
                if (name.startsWith("org.mpris.MediaPlayer2.")) {
                    players.add(name);
                }
            }
        }
        return players;
    }

    protected static String getPlayer() {
        return currentBusName.replace("org.mpris.MediaPlayer2.", "");
    }

    protected static String getFilter() {
        return CONFIG.getFilter().isEmpty() ? "None" : CONFIG.getFilter();
    }

    protected static String getPreferred() {
        return CONFIG.getPreferred().isEmpty() ? "None" : CONFIG.getPreferred();
    }

    private static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    CONFIG = new Gson().fromJson(reader, MprisCustomHudConfig.class);
                    LOGGER.info("MPRIS CustomHud config loaded!");
                }
            } catch (IOException e) {
                LOGGER.error(Arrays.toString(e.getStackTrace()));
            }
        }
    }

    private static void saveToFile() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(gson.toJson(CONFIG));
        } catch (IOException e) {
            LOGGER.error(Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void onInitialize() {
        CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json").toFile();
        loadConfig();
        currentBusName = "org.mpris.MediaPlayer2." + CONFIG.getFilter();

        try {
            conn = DBusConnectionBuilder.forSessionBus().build();
            dbus = conn.getRemoteObject("org.freedesktop.DBus", "/", DBus.class);
            if (CONFIG.getFilter().isEmpty() && getActivePlayers().contains(currentBusName + CONFIG.getPreferred())) {
                currentBusName += CONFIG.getPreferred();
            }
            for (String name : getActivePlayers()) {
                if (currentBusName.equals("org.mpris.MediaPlayer2.")) {
                    currentBusName = name;
                }
                players.put(name, new PlayerInfo(name, true));
            }
            if (!players.containsKey(currentBusName))
                updateMaps(new PlayerInfo(currentBusName, true));
            initCustomHud();
            // listen for name owner changes to reset the values in case the player
            // terminates
            conn.addSigHandler(NameOwnerChanged.class, new NameOwnerChangedHandler());
        } catch (DBusException e) {
            LOGGER.error(Arrays.toString(e.getStackTrace()));
        }
    }

    protected static void cyclePlayers() {
        if (players.size() > 0 && CONFIG.getFilter().isEmpty()) {
            List<String> keys = new ArrayList<>(players.keySet());
            int index = keys.indexOf(currentBusName);
            currentBusName = keys.get(index + 1 == keys.size() ? 0 : index + 1);
            updateMaps(players.get(currentBusName));
        }
    }

    private static Player getPlayerObject() {
        try {
            if (dbus != null && Arrays.asList(dbus.ListNames()).contains(currentBusName)) {
                return conn.getRemoteObject(currentBusName, "/org/mpris/MediaPlayer2", Player.class);
            }
        } catch (DBusException e) {
            LOGGER.error(Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    protected static void playPause() {
        Player player = getPlayerObject();
        if (player != null)
            player.PlayPause();
    }

    protected static void play() {
        Player player = getPlayerObject();
        if (player != null)
            player.PlayPause();
    }

    protected static void pause() {
        Player player = getPlayerObject();
        if (player != null)
            player.PlayPause();
    }

    protected static void next() {
        Player player = getPlayerObject();
        if (player != null)
            player.Next();
    }

    protected static void previous() {
        Player player = getPlayerObject();
        if (player != null)
            player.Previous();
    }

    private static class NameOwnerChangedHandler implements DBusSigHandler<DBus.NameOwnerChanged> {
        @Override
        public void handle(DBus.NameOwnerChanged signal) {
            if (signal.newOwner.isEmpty() && !signal.oldOwner.isEmpty() && players.containsKey(signal.name)) {
                players.get(signal.name).destroy();
                players.remove(signal.name);
                if (signal.name.equals(currentBusName) && CONFIG.getFilter().isEmpty()) {
                    if (players.containsKey("org.mpris.MediaPlayer2." + CONFIG.getPreferred())) {
                        currentBusName = "org.mpris.MediaPlayer2." + CONFIG.getPreferred();
                        updateMaps(players.get(currentBusName));
                    } else {
                        cyclePlayers();
                    }
                }
            } else if (!signal.newOwner.isEmpty() && signal.oldOwner.isEmpty()
                    && signal.name.startsWith("org.mpris.MediaPlayer2.")) {
                if (signal.name.equals("org.mpris.MediaPlayer2." + CONFIG.getPreferred())) {
                    currentBusName = signal.name;
                }
                players.put(signal.name, new PlayerInfo(signal.name, false));
                if (!players.containsKey(currentBusName)) {
                    cyclePlayers();
                }
            }
        }
    }
}
