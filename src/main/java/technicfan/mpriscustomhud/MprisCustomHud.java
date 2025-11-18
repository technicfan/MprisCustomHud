package technicfan.mpriscustomhud;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import oshi.util.tuples.Triplet;

import org.freedesktop.dbus.types.Variant;

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
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.UInt64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minenash.customhud.HudElements.supplier.BooleanSupplierElement;
import com.minenash.customhud.HudElements.supplier.SpecialSupplierElement;
import com.minenash.customhud.HudElements.supplier.StringSupplierElement;

import static com.minenash.customhud.data.Flags.wrap;
import static com.minenash.customhud.registry.CustomHudRegistry.registerElement;

@SuppressWarnings("unchecked")
public class MprisCustomHud implements ModInitializer {
    public static final String MOD_ID = "mpriscustomhud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static File CONFIG_FILE;
    private static MprisCustomHudConfig CONFIG = new MprisCustomHudConfig();

    private static String track, album, progress, duration, loop, artist, artists;
    private static boolean shuffle, playing;
    private static int position, length;
    private static long positionMs;
    private static double rate;
    private final static long microToMs = 1000L;

    private static HashMap<String, String> stringmap = new HashMap<>();
    private static HashMap<String, Boolean> boolmap = new HashMap<>();
    private static HashMap<String, Triplet<String, Number, Boolean>> specialmap = new HashMap<>();

    private static DBus dbus;
    private static DBusConnection conn;
    private static Thread positionTimer;
    private static String currentBusName;
    private static boolean positionReset = false;
    private static Object positionResetLock = new Object();

    private static void resetValues() {
        track = "";
        album = "";
        progress = "";
        duration = "";
        loop = "";
        artist = "";
        artists = "";
        shuffle = false;
        playing = false;
        position = 0;
        positionMs = 0;
        length = 0;
        rate = 1.0;

        updateMaps();
    }

    private static void updateMaps() {
        stringmap.put("mpris_track", track);
        stringmap.put("mpris_album", album);
        stringmap.put("mpris_loop", loop);
        stringmap.put("mpris_artist", artist);
        stringmap.put("mpris_artists", artists);

        boolmap.put("mpris_shuffle", shuffle);
        boolmap.put("mpris_playing", playing);

        Triplet<String, Number, Boolean> progressTriplet = new Triplet<>(progress, position, position > 0);
        Triplet<String, Number, Boolean> durationTriplet = new Triplet<>(duration, length, length > 0);
        Triplet<String, Number, Boolean> rateTriplet = new Triplet<>(Double.toString(rate), rate, rate > 0.0);
        specialmap.put("mpris_progress", progressTriplet);
        specialmap.put("mpris_duration", durationTriplet);
        specialmap.put("mpris_rate", rateTriplet);
    }

    private static void updateMetadata(Map<String, ?> metadata) {
        Object lengthObj, trackObj, albumObj, artistsObj;
        lengthObj = metadata.get("mpris:length");
        artistsObj = metadata.get("xesam:artist");
        trackObj = metadata.get("xesam:title");
        albumObj = metadata.get("xesam:album");
        if (lengthObj != null) {
            length = (int) (((UInt64) lengthObj).longValue() / microToMs / microToMs);
            duration = String.format("%s%02d:%02d", length / 3600 > 0 ? String.format("%02d:", length / 3600) : "",
                    length / 60 % 60, length % 60);
        } else {
            length = 0;
            duration = "00:00";
        }
        if (artistsObj != null) {
            List<String> list = (List<String>) artistsObj;
            artist = list.get(0);
            artists = String.join(", ", list);
        } else {
            artist = "";
            artists = "";
        }
        if (trackObj != null) {
            track = (String) trackObj;
        } else {
            track = "";
        }
        if (albumObj != null) {
            album = (String) albumObj;
        } else {
            album = "";
        }
    }

    private static void updatePosition(long newPosition, boolean restart) {
        positionMs = newPosition;
        if (restart) {
            positionMs -= 100;
            positionTimer.interrupt();
        }
        position = Math.round(positionMs / microToMs);
        progress = String.format("%s%02d:%02d", position / 3600 > 0 ? String.format("%02d:", position / 3600) : "",
                position / 60 % 60, position % 60);
        Triplet<String, Number, Boolean> progressTriplet = new Triplet<>(progress, position, position > 0);
        specialmap.put("mpris_progress", progressTriplet);
    }

    private static void refreshValues() {
        try {
            if (Arrays.asList(dbus.ListNames()).contains(currentBusName)) {
                Properties data = conn.getRemoteObject(currentBusName, "/org/mpris/MediaPlayer2", Properties.class);
                Map<String, ?> metadata = data.Get("org.mpris.MediaPlayer2.Player", "Metadata");

                if (metadata != null)
                    updateMetadata(metadata);
                loop = data.Get("org.mpris.MediaPlayer2.Player", "LoopStatus");
                shuffle = data.Get("org.mpris.MediaPlayer2.Player", "Shuffle");
                playing = data.Get("org.mpris.MediaPlayer2.Player", "PlaybackStatus").toString().equals("Playing");
                rate = data.Get("org.mpris.MediaPlayer2.Player", "Rate");
                long positionLong = data.Get("org.mpris.MediaPlayer2.Player", "Position");
                updatePosition(positionLong / microToMs, true);

                updateMaps();
            }
        } catch (DBusException e) {
            LOGGER.error(Arrays.toString(e.getStackTrace()));
        }
    }

    protected static void setPlayer(String newPlayer) {
        if (!CONFIG.getPlayer().equals(newPlayer)) {
            resetValues();
            CONFIG.setPlayer(newPlayer);
            currentBusName = "org.mpris.MediaPlayer2." + CONFIG.getPlayer();
            saveToFile();
        }
        ;
        refreshValues();
    }

    protected static List<String> getActivePlayers() {
        List<String> players = new ArrayList<>();
        if (dbus != null) {
            for (String name : dbus.ListNames()) {
                if (name.startsWith("org.mpris.MediaPlayer2.")) {
                    players.add(name.replace("org.mpris.MediaPlayer2.", ""));
                }
            }
        }
        return players;
    }

    protected static String getPlayer() {
        return CONFIG.getPlayer();
    }

    private static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    CONFIG = new Gson().fromJson(reader, MprisCustomHudConfig.class);
                    LOGGER.info("Mpris player is now " + CONFIG.getPlayer());
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
        resetValues();
        initCustomHud();
        currentBusName = "org.mpris.MediaPlayer2." + CONFIG.getPlayer();

        try {
            conn = DBusConnectionBuilder.forSessionBus().build();
            dbus = conn.getRemoteObject("org.freedesktop.DBus", "/", DBus.class);
            positionTimer = new Thread(() -> {
                while (true) {
                    try {
                        if (playing && position < length) {
                            updatePosition(positionMs + 100L, false);
                            Thread.sleep((long) (100 * rate));
                        } else {
                            // sleep forever until the thread is interrupted
                            // (ik this is not supposed to be done like this)
                            Thread.sleep(Long.MAX_VALUE);
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            });
            // listen for music Properties (like metadata, shuffle status, ...)
            conn.addSigHandler(PropertiesChanged.class, new PropChangedHandler());
            // listen for progress jumps for the current track
            conn.addSigHandler(Player.Seeked.class, new SeekedHandler());
            // listen for name owner changes to reset the values in case the player
            // terminates
            conn.addSigHandler(NameOwnerChanged.class, new NameOwnerChangedHandler());
            positionTimer.start();
            refreshValues();
        } catch (DBusException e) {
            LOGGER.error(Arrays.toString(e.getStackTrace()));
            return;
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

    private static class SeekedHandler implements DBusSigHandler<Player.Seeked> {
        public void handle(Player.Seeked signal) {
            synchronized (positionResetLock) {
                // check if signal came from the currently selected player
                if (dbus.GetNameOwner(currentBusName).equals(signal.getSource())) {
                    if (positionReset) {
                        try {
                            positionResetLock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    updatePosition(signal.getPosition() / microToMs, true);
                }
            }
        }
    }

    private static class NameOwnerChangedHandler implements DBusSigHandler<DBus.NameOwnerChanged> {
        @Override
        public void handle(DBus.NameOwnerChanged signal) {
            if (signal.name.equals(currentBusName) && signal.newOwner.isEmpty()) {
                resetValues();
            }
        }
    }

    private static class PropChangedHandler extends AbstractPropertiesChangedHandler {
        @Override
        public void handle(PropertiesChanged signal) {
            synchronized (positionResetLock) {
                positionReset = true;
                // check if signal came from the currently selected player
                if (dbus.GetNameOwner(currentBusName).equals(signal.getSource())) {
                    Map<String, Variant<?>> changed = signal.getPropertiesChanged();
                    if (changed.get("PlaybackStatus") != null) {
                        if (changed.get("PlaybackStatus").getValue().toString().equals("Stopped")) {
                            resetValues();
                            return;
                        }
                        playing = changed.get("PlaybackStatus").getValue().toString().equals("Playing");
                        if (playing)
                            positionTimer.interrupt();
                    }
                    if (changed.get("LoopStatus") != null) {
                        loop = (String) changed.get("LoopStatus").getValue();
                    }
                    if (changed.get("Shuffle") != null) {
                        shuffle = (boolean) changed.get("Shuffle").getValue();
                    }
                    if (changed.get("Rate") != null) {
                        rate = (double) changed.get("Rate").getValue();
                    }
                    if (changed.get("Metadata") != null) {
                        Map<String, Variant<Object>> newMetadata = (Map<String, Variant<Object>>) changed
                                .get("Metadata")
                                .getValue();
                        Map<String, Object> metadata = new HashMap<>();
                        for (Map.Entry<String, Variant<Object>> entry : newMetadata.entrySet()) {
                            metadata.put(entry.getKey(), entry.getValue().getValue());
                        }
                        // probably not the best solution
                        // but needed as the Seeked signal doesn't have to be
                        // emitted when tracks change
                        updatePosition(0, true);
                        updateMetadata(metadata);
                    }
                    updateMaps();
                }
                positionReset = false;
                positionResetLock.notify();
            }
        }
    }
}
