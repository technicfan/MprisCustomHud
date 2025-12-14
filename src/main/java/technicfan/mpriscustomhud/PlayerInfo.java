package technicfan.mpriscustomhud;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

public class PlayerInfo {
    private final static long microToMs = 1000L, positionUpdateTime = 100L;
    private String track, trackId, album, progress, duration, loop, artist, artists;
    private boolean shuffle, playing, tempPlaying, existing, running = true;
    private long positionMs, lengthMs;
    private double rate;

    private String busName;
    private Thread positionTimer;
    private boolean positionReset = false;
    private AutoCloseable propertiesHandler, seekedHandler;
    private static Object positionResetLock = new Object(), positionUpdateLock = new Object();

    public String getBusName() {
        return busName;
    }

    public String getTrack() {
        return track;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getAlbum() {
        return album;
    }

    public String getProgress() {
        return progress;
    }

    public String getDuration() {
        return duration;
    }

    public String getLoop() {
        return loop;
    }

    public String getArtist() {
        return artist;
    }

    public String getArtists() {
        return artists;
    }

    public boolean getShuffle() {
        return shuffle;
    }

    public boolean getPlaying() {
        return playing;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getLengthMs() {
        return lengthMs;
    }

    public double getRate() {
        return rate;
    }

    PlayerInfo(String name) {
        busName = name;
        resetValues();
    }

    PlayerInfo(String name, boolean existing) {
        busName = name;
        this.existing = existing;
        resetValues();

        try {
            positionTimer = new Thread(() -> {
                while (running) {
                    try {
                        long loopStart = System.currentTimeMillis();
                        Thread.sleep(positionUpdateTime);
                        if (playing) {
                            if (positionMs < lengthMs) {
                                updatePosition(positionMs + (long) ((System.currentTimeMillis() - loopStart) * rate),
                                        false);
                            }
                        } else {
                            synchronized (positionUpdateLock) {
                                positionUpdateLock.wait();
                            }
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            });
            positionTimer.setName("Track progress timer for " + busName);
            // listen for music Properties (like metadata, shuffle status, ...)
            propertiesHandler = MprisCustomHud.conn.addSigHandler(PropertiesChanged.class, new PropChangedHandler());
            // listen for progress jumps for the current track
            seekedHandler = MprisCustomHud.conn.addSigHandler(Player.Seeked.class, new SeekedHandler());
            positionTimer.start();
            if (existing) {
                refreshValues();
            }
        } catch (DBusException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
        }
    }

    protected void close() {
        running = false;
        positionTimer.interrupt();
        try {
            propertiesHandler.close();
            seekedHandler.close();
        } catch (Exception e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
        }
        resetValues();
    }

    protected void resetValues() {
        track = "";
        trackId = "";
        album = "";
        progress = "";
        duration = "";
        loop = "";
        artist = "";
        artists = "";
        shuffle = false;
        playing = false;
        tempPlaying = false;
        positionMs = 0;
        lengthMs = 0;
        rate = 1.0;

        MprisCustomHud.updateMaps(this);
    }

    private void updateData(Map<String, Variant<?>> data, boolean init) {
        if (data.get("LoopStatus") != null) {
            loop = (String) data.get("LoopStatus").getValue();
        }
        if (data.get("Shuffle") != null) {
            shuffle = (boolean) data.get("Shuffle").getValue();
        }
        if (data.get("Rate") != null) {
            rate = (double) data.get("Rate").getValue();
            if (!playing && tempPlaying && rate > 0.0) {
                playing = true;
                if (!init)
                    positionTimer.interrupt();
            }
        }
        if (data.get("PlaybackStatus") != null) {
            if (!init && data.get("PlaybackStatus").getValue().toString().equals("Stopped")) {
                resetValues();
                return;
            }
            tempPlaying = data.get("PlaybackStatus").getValue().toString().equals("Playing");
            playing = tempPlaying && rate > 0.0;
            if (!init && playing)
                positionTimer.interrupt();
        }
        if (data.get("Metadata") != null) {
            Map<?, ?> newMetadata = (Map<?, ?>) data
                    .get("Metadata")
                    .getValue();
            Map<String, Object> metadata = new HashMap<>();
            for (Map.Entry<?, ?> entry : newMetadata.entrySet()) {
                metadata.put((String) entry.getKey(), ((Variant<?>) entry.getValue()).getValue());
            }
            updateMetadata(metadata);
        }
        if (init && data.get("Position") != null) {
            long positionLong = (long) data.get("Position").getValue();
            updatePosition(positionLong / microToMs, true);
        }

        MprisCustomHud.updateMaps(this);
    }

    private void updateMetadata(Map<String, ?> metadata) {
        String tempId = trackId;
        Object lengthObj, trackObj, trackIdObj, albumObj, artistsObj;
        lengthObj = metadata.get("mpris:length");
        trackIdObj = metadata.get("mpris:trackid");
        artistsObj = metadata.get("xesam:artist");
        trackObj = metadata.get("xesam:title");
        albumObj = metadata.get("xesam:album");
        if (lengthObj != null) {
            if (lengthObj instanceof UInt64) {
                lengthMs = ((UInt64) lengthObj).longValue() / microToMs;
            } else {
                lengthMs = (long) lengthObj / microToMs;
            }
            long length = lengthMs / microToMs;
            duration = String.format("%s%02d:%02d", length / 3600 > 0 ? String.format("%02d:", length / 3600) : "",
                    length / 60 % 60, length % 60);
        } else {
            lengthMs = 0;
            duration = "";
        }
        if (artistsObj != null && artistsObj instanceof List) {
            List<?> tempList = (List<?>) artistsObj;
            List<String> list = new ArrayList<>();
            for (Object name : tempList) {
                list.add((String) name);
            }
            artist = list.isEmpty() ? "" : list.get(0);
            artists = String.join(", ", list);
        } else {
            artist = "";
            artists = "";
        }
        if (trackObj != null && trackObj instanceof String) {
            track = (String) trackObj;
        } else {
            track = "";
        }
        if (trackIdObj != null && trackIdObj instanceof String) {
            trackId = (String) trackIdObj;
        } else {
            trackId = "";
        }
        if (albumObj != null && albumObj instanceof String) {
            album = (String) albumObj;
        } else {
            album = "";
        }
        if (!trackId.equals(tempId)) {
            // needed as the Seeked signal doesn't have to be
            // emitted when tracks change
            updatePosition(0, true);
        }
    }

    private void updatePosition(long newPosition, boolean restart) {
        positionMs = newPosition;
        if (restart) {
            positionTimer.interrupt();
        }
        long position = positionMs / microToMs;
        progress = String.format("%s%02d:%02d", position / 3600 > 0 ? String.format("%02d:", position / 3600) : "",
                position / 60 % 60, position % 60);
        MprisCustomHud.updatePostionMap(busName, progress, position);
    }

    protected void refreshValues() {
        try {
            if (Arrays.asList(MprisCustomHud.dbus.ListNames()).contains(busName)) {
                synchronized (MprisCustomHud.conn) {
                    Map<String, Variant<?>> data = MprisCustomHud.conn
                            .getRemoteObject(busName, "/org/mpris/MediaPlayer2", Properties.class)
                            .GetAll("org.mpris.MediaPlayer2.Player");
                    updateData(data, true);
                }
            }
        } catch (DBusException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
        }
    }

    private class SeekedHandler implements DBusSigHandler<Player.Seeked> {
        public void handle(Player.Seeked signal) {
            synchronized (positionResetLock) {
                // check if signal came from the currently selected player
                if (MprisCustomHud.dbus.GetNameOwner(busName).equals(signal.getSource())) {
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

    private class PropChangedHandler extends AbstractPropertiesChangedHandler {
        @Override
        public void handle(PropertiesChanged signal) {
            synchronized (positionResetLock) {
                positionReset = true;
                // check if signal came from the currently selected player
                if (MprisCustomHud.dbus.GetNameOwner(busName).equals(signal.getSource())) {
                    if (existing) {
                        Map<String, Variant<?>> changed = signal.getPropertiesChanged();
                        updateData(changed, false);
                    } else {
                        refreshValues();
                        existing = true;
                    }
                }
                positionReset = false;
                positionResetLock.notify();
            }
        }
    }
}
