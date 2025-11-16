package technicfan.mpriscustomhud;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import oshi.util.tuples.Triplet;

import org.freedesktop.dbus.types.Variant;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
    public static DBus dbus;

    private static File CONFIG_FILE;
    private static MprisCustomHudConfig CONFIG = new MprisCustomHudConfig();

    private static String track, album, progress, duration, loop, artist, artists;
    private static boolean shuffle, playing;
    private static int position, length;
    private static double rate;
    private static long refresh = 1000L;

    private static HashMap<String, String> stringmap = new HashMap<>();
    private static HashMap<String, Boolean> boolmap = new HashMap<>();
    private static HashMap<String, Triplet<String, Integer, Boolean>> specialmap = new HashMap<>();

    private static DBusConnection conn;
    private static String addedHandler = "";
    private static boolean looping = false;
    private static boolean positionReset = false;

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

        Triplet<String, Integer, Boolean> progressTriplet = new Triplet<>(progress, position, position > 0);
        Triplet<String, Integer, Boolean> durationTriplet = new Triplet<>(duration, length, length > 0);
        specialmap.put("mpris_progress", progressTriplet);
        specialmap.put("mpris_duration", durationTriplet);
    }

    private static void updateMetadata(Map<String, ?> metadata) {
        Object lengthObj, trackObj, albumObj, artistsObj;
        lengthObj = metadata.get("mpris:length");
        artistsObj = metadata.get("xesam:artist");
        trackObj = metadata.get("xesam:title");
        albumObj = metadata.get("xesam:album");
        if (lengthObj != null) {
            length = (int) (((UInt64) lengthObj).longValue() / 1e6);
            duration = String.format("%02d:%02d", length / 60, length % 60);
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

    private static void updatePosition(int newPosition) {
        position = newPosition;
        progress = String.format("%02d:%02d", position / 60, position % 60);
        Triplet<String, Integer, Boolean> progressTriplet = new Triplet<>(progress, position, position > 0);
        specialmap.put("mpris_progress", progressTriplet);
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

    private static void connect() {
        looping = true;
        try {
            while (!Arrays.asList(dbus.ListNames()).contains("org.mpris.MediaPlayer2." + CONFIG.getPlayer())) {
                try {
                    Thread.sleep(refresh);
                } catch (InterruptedException e) {
                }
                if (!looping) {
                    return;
                }
            }

            Properties data = conn.getRemoteObject("org.mpris.MediaPlayer2." + CONFIG.getPlayer(),
                    "/org/mpris/MediaPlayer2",
                    Properties.class);
            Map<String, ?> metadata = data.Get("org.mpris.MediaPlayer2.Player", "Metadata");

            if (metadata != null)
                updateMetadata(metadata);
            long positionLong = data.Get("org.mpris.MediaPlayer2.Player", "Position");
            position = (int) (positionLong * 1e-6);
            progress = String.format("%02d:%02d", position / 60, position % 60);
            loop = data.Get("org.mpris.MediaPlayer2.Player", "LoopStatus");
            shuffle = data.Get("org.mpris.MediaPlayer2.Player", "Shuffle");
            playing = data.Get("org.mpris.MediaPlayer2.Player", "PlaybackStatus").toString().equals("Playing");
            rate = data.Get("org.mpris.MediaPlayer2.Player", "Rate");

            updateMaps();

            // When dbus-java 5.2.0 releases
            // I will be able to properly filter the signals, as it's somewhat broken right now
            // (if i understand it correctly at least...)

            // if (!addedHandler.isEmpty()) {
            //     conn.removeSigHandler(PropertiesChanged.class, addedHandler, new PropChangedHandler());
            //     conn.removeSigHandler(Player.Seeked.class, addedHandler, new SeekedHandler());
            // }

            if (addedHandler.isEmpty()) {
                addedHandler = "org.mpris.MediaPlayer2." + CONFIG.getPlayer();
                conn.addSigHandler(PropertiesChanged.class, new PropChangedHandler());
                conn.addSigHandler(Player.Seeked.class, new SeekedHandler());
            }
        } catch (DBusException e) {
            LOGGER.error(Arrays.toString(e.getStackTrace()));
        }
    }

    public static void setPlayer(String newPlayer) {
        if (!CONFIG.getPlayer().equals(newPlayer)) resetValues();
        CONFIG.setPlayer(newPlayer);
        saveToFile();
        looping = false;
        connect();
    }

    public static String getPlayer() {
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

        Thread main = new Thread(() -> {
            try {
                conn = DBusConnectionBuilder.forSessionBus().build();
                dbus = conn.getRemoteObject("org.freedesktop.DBus", "/", DBus.class);
                connect();
            } catch (DBusException e) {
                LOGGER.error(Arrays.toString(e.getStackTrace()));
                return;
            }

            while (true) {
                if (playing && position < length) {
                    updatePosition(position + 1);
                    try {
                        Thread.sleep((long) (1000 * rate));
                    } catch (InterruptedException e) {}
                } else {
                    try {
                        Thread.sleep(refresh);
                    } catch (InterruptedException e) {}
                }
            }
        });
        main.start();
    }

    private static class SeekedHandler implements DBusSigHandler<Player.Seeked> {
        public void handle(Player.Seeked signal) {
            while (true) {
                if (!positionReset) {
                    updatePosition((int) (signal.getPosition() * 1e-6));
                    break;
                }
            }
        }
    }

    private static class PropChangedHandler extends AbstractPropertiesChangedHandler {
        @Override
        public void handle(PropertiesChanged signal) {
            Map<String, Variant<?>> changed = signal.getPropertiesChanged();
            if (changed.get("ActiveState") != null
                    && changed.get("ActiveState").getValue().toString().equals("inactive")) {
                resetValues();
                return;
            }
            if (changed.get("PlaybackStatus") != null) {
                if (changed.get("PlaybackStatus").getValue().toString().equals("Stopped")) {
                    resetValues();
                    return;
                }
                playing = changed.get("PlaybackStatus").getValue().toString().equals("Playing");
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
                positionReset = true;
                Map<String, Variant<Object>> newMetadata = (Map<String, Variant<Object>>) changed.get("Metadata")
                        .getValue();
                Map<String, Object> metadata = new HashMap<>();
                for (Map.Entry<String, Variant<Object>> entry : newMetadata.entrySet()) {
                    metadata.put(entry.getKey(), entry.getValue().getValue());
                }
                // probably not the best solution
                // but needed as the Seeked signal doesn't have to be
                // emitted when tracks change
                updatePosition(0);
                positionReset = false;
                updateMetadata(metadata);
            }
            updateMaps();
        }
    }
}
