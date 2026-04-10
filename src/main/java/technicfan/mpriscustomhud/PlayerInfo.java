package technicfan.mpriscustomhud;

import java.util.List;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;

public class PlayerInfo {
    private final static long microToMs = 1000L;
    private final String name, loop;
    private final boolean shuffle, playing, existing;
    private final long startPosition, startTime;
    private final double rate;
    private final Metadata metadata;

    private final Player player;
    private final String busName;

    public Player getPlayer() {
        return player;
    }

    public String getBusName() {
        return busName;
    }

    public String getName() {
        return name;
    }

    public String getTrack() {
        return metadata.track;
    }

    public String getTrackId() {
        return metadata.trackId;
    }

    public String getAlbum() {
        return metadata.album;
    }

    public String getLoop() {
        return loop;
    }

    public String getArtist() {
        return metadata.artist;
    }

    public List<String> getArtists() {
        return metadata.artists;
    }

    public boolean getShuffle() {
        return shuffle;
    }

    public boolean getPlaying() {
        return playing;
    }

    public long getPosition() {
        return playing ? currentPosition() : startPosition;
    }

    public long getDuration() {
        return metadata.duration;
    }

    public double getRate() {
        return rate;
    }

    private PlayerInfo(
        String busName,
        String name,
        String loop,
        boolean shuffle,
        boolean playing,
        boolean existing,
        long startPosition,
        long startTime,
        double rate,
        Player player,
        Metadata metadata
    ) {
        this.busName = busName;
        this.name = name;
        this.loop = loop;
        this.shuffle = shuffle;
        this.playing = playing;
        this.existing = existing;
        this.startPosition = startPosition;
        this.startTime = startTime;
        this.rate = rate;
        this.player = player;
        this.metadata = metadata;
    }

    PlayerInfo(String name) {
        this.busName = name;
        this.name = "";
        this.loop = "";
        this.shuffle = false;
        this.playing = false;
        this.existing = false;
        this.startPosition = 0;
        this.startTime = System.currentTimeMillis();
        this.rate = 1.0;
        this.player = null;
        this.metadata = new Metadata();
    }

    protected static PlayerInfo of(String name) {
        return new PlayerInfo(name, "", "", false, false, false, 0, 0, 0, null, new Metadata());
    }

    private static PlayerInfo of(String name, Player player) {
        return new PlayerInfo(name, "", "", false, false, false, 0, 0, 0, player, new Metadata());
    }

    protected static PlayerInfo of(String name, boolean existing) {
        Player player;
        try {
            player = MprisCustomHud.conn.getRemoteObject(name, "/org/mpris/MediaPlayer2", Player.class);
        } catch (DBusException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
            player = null;
        }
        if (existing) {
            return of(name, player).refresh();
        } else {
            return of(name, player);
        }
    }

    private PlayerInfo updateData(Map<String, Variant<?>> data, List<String> removed, boolean init) {
        String name = this.name, loop = this.loop;
        boolean playing = this.playing, shuffle = this.shuffle, existing = true;
        long startPosition = this.startPosition, startTime = this.startTime;
        double rate = this.rate;
        Metadata metadata = this.metadata;
        Player player = this.player;
        if (removed != null) {
            for (String property : removed) {
                switch (property) {
                    case "Identity":
                        name = "";
                    case "LoopStatus":
                        loop = "";
                    case "Shuffle":
                        shuffle = false;
                    case "Rate":
                        rate = 1.0;
                    case "PlaybackStatus": {
                        playing = false;
                    }
                    case "Metadata": {
                        metadata = new Metadata();
                    }
                }
            }
        }
        if (data.containsKey("Identity")) {
            name = (String) data.get("Identity").getValue();
        }
        if (data.containsKey("LoopStatus")) {
            loop = (String) data.get("LoopStatus").getValue();
        }
        if (data.containsKey("Shuffle")) {
            shuffle = (boolean) data.get("Shuffle").getValue();
        }
        if (data.containsKey("Rate")) {
            startPosition = currentPosition();
            startTime = System.currentTimeMillis();
            rate = (double) data.get("Rate").getValue();
        }
        if (data.containsKey("PlaybackStatus")) {
            if (!init && data.get("PlaybackStatus").getValue().toString().equals("Stopped")) {
                return of(busName, player);
            }
            boolean tempPlaying = data.get("PlaybackStatus").getValue().toString().equals("Playing");
            if (!playing && tempPlaying) {
                startTime = System.currentTimeMillis();
            } else if (playing && !tempPlaying) {
                startPosition = currentPosition();
            }
            playing = tempPlaying;
        }
        if (data.containsKey("Metadata")) {
            Map<?, ?> newMetadata = (Map<?, ?>) data
                    .get("Metadata")
                    .getValue();
            Map<String, Object> metaData = new HashMap<>();
            for (Map.Entry<?, ?> entry : newMetadata.entrySet()) {
                metaData.put((String) entry.getKey(), ((Variant<?>) entry.getValue()).getValue());
            }
            metadata = new Metadata(metaData);
        }
        if (init && data.containsKey("Position")) {
            long positionLong = (long) data.get("Position").getValue();
            startTime = System.currentTimeMillis();
            startPosition = positionLong / microToMs;
        } else if (!this.metadata.trackId.equals(metadata.trackId)) {
            // needed as the Seeked signal doesn't have to be
            // emitted when tracks change
            startTime = System.currentTimeMillis();
            startPosition = 0;
        }

        return new PlayerInfo(busName, name, loop, shuffle, playing, existing, startPosition, startTime, rate, player, metadata);
    }

    private long currentPosition() {
        return startPosition + (long) ((System.currentTimeMillis() - startTime) * rate);
    }

    protected PlayerInfo refresh() {
        try {
            if (Arrays.asList(MprisCustomHud.dbus.ListNames()).contains(busName)) {
                synchronized (MprisCustomHud.conn) {
                    Properties properties = MprisCustomHud.conn
                            .getRemoteObject(busName, "/org/mpris/MediaPlayer2", Properties.class);
                    Map<String, Variant<?>> data = properties.GetAll("org.mpris.MediaPlayer2.Player");
                    data.putAll(properties.GetAll("org.mpris.MediaPlayer2"));
                    return updateData(data, null, true);
                }
            }
        } catch (DBusException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
        }
        return new PlayerInfo(busName);
    }

    protected PlayerInfo seeked(Player.Seeked signal) {
        return new PlayerInfo(busName, name, loop, shuffle, playing, existing, signal.getPosition() / microToMs, System.currentTimeMillis(), rate, player, metadata);
    }

    protected PlayerInfo propertiesChanged(PropertiesChanged signal) {
        if (existing) {
            Map<String, Variant<?>> changed = signal.getPropertiesChanged();
            return updateData(changed, signal.getPropertiesRemoved(), false);
        } else {
            return refresh();
        }
    }

    private static class Metadata {
        final String track, trackId, album, artist;
        final List<String> artists;
        final long duration;

        Metadata() {
            track = "";
            trackId = "";
            album = "";
            artist = "";
            artists = List.of();
            duration = 0;
        }

        Metadata(Map<String, ?> metadata) {
            Object lengthObj, trackObj, trackIdObj, albumObj, artistsObj;
            lengthObj = metadata.get("mpris:length");
            trackIdObj = metadata.get("mpris:trackid");
            artistsObj = metadata.get("xesam:artist");
            trackObj = metadata.get("xesam:title");
            albumObj = metadata.get("xesam:album");
            if (lengthObj != null) {
                if (lengthObj instanceof UInt64) {
                    duration = ((UInt64) lengthObj).longValue() / microToMs;
                } else {
                    duration = (long) lengthObj / microToMs;
                }
            } else {
                duration = 0;
            }
            if (artistsObj != null && artistsObj instanceof List) {
                List<?> tempList = (List<?>) artistsObj;
                String[] list = new String[tempList.size()];
                for (int i = 0; i < tempList.size(); i++) {
                    list[i] = (String) tempList.get(i);
                }
                artist = list.length == 0 ? "" : list[0];
                artists = List.of(list);
            } else {
                artist = "";
                artists = List.of();
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
        }
    }
}
