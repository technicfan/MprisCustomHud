package technicfan.mpriscustomhud;

import java.util.List;
import java.math.BigInteger;
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
    private final long startPosition, startTime;
    private final boolean existing;
    private final Player player;

    public final String name, busname, repeat;
    public final boolean shuffle, playing;
    public final double rate, volume;
    public final Metadata metadata;

    public long progress() {
        return Math.min(playing ? currentPosition() : startPosition, metadata.duration);
    }

    public void play() {
        if (player != null)
            player.Play();
    }

    public void pause() {
        if (player != null)
            player.Pause();
    }

    public void playpause() {
        if (player != null)
            player.PlayPause();
    }

    public void next() {
        if (player != null)
            player.Next();
    }

    public void previous() {
        if (player != null)
            player.Previous();
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
        double volume,
        Player player,
        Metadata metadata
    ) {
        this.busname = busName;
        this.name = name;
        this.repeat = loop;
        this.shuffle = shuffle;
        this.playing = playing;
        this.existing = existing;
        this.startPosition = startPosition;
        this.startTime = startTime;
        this.rate = rate;
        this.volume = volume;
        this.player = player;
        this.metadata = metadata;
    }

    protected PlayerInfo() {
        this("", "", "", false, false, false, 0, 0, 0, 0, null, new Metadata());
    }

    private PlayerInfo(String busname, Player player) {
        this(busname, "", "", false, false, false, 0, 0, 0, 0, player, new Metadata());
    }

    protected static PlayerInfo of(String busname, boolean existing) {
        Player player;
        try {
            player = MprisCustomHud.conn.getRemoteObject(busname, "/org/mpris/MediaPlayer2", Player.class);
        } catch (DBusException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
            player = null;
        }
        if (existing) {
            return new PlayerInfo(busname, player).refresh();
        } else {
            return new PlayerInfo(busname, player);
        }
    }

    private PlayerInfo updateData(Map<String, Variant<?>> data, List<String> removed, boolean init) {
        String name = this.name, loop = this.repeat;
        boolean playing = this.playing, shuffle = this.shuffle, existing = true;
        long startPosition = this.startPosition, startTime = this.startTime;
        double rate = this.rate, volume = this.volume;
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
                    case "Volume":
                        volume = 1.0;
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
        if (data.containsKey("Volume")) {
            volume = (double) data.get("Volume").getValue();
        }
        if (data.containsKey("PlaybackStatus")) {
            if (!init && data.get("PlaybackStatus").getValue().toString().equals("Stopped")) {
                return new PlayerInfo(busname, player);
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
        } else if (!this.metadata.trackid.equals(metadata.trackid)) {
            // needed as the Seeked signal doesn't have to be
            // emitted when tracks change
            startTime = System.currentTimeMillis();
            startPosition = 0;
        }

        return new PlayerInfo(busname, name, loop, shuffle, playing, existing, startPosition, startTime, rate, volume, player, metadata);
    }

    private long currentPosition() {
        return startPosition + (long) ((System.currentTimeMillis() - startTime) * rate);
    }

    protected PlayerInfo refresh() {
        try {
            if (Arrays.asList(MprisCustomHud.dbus.ListNames()).contains(busname)) {
                synchronized (MprisCustomHud.conn) {
                    Properties properties = MprisCustomHud.conn
                            .getRemoteObject(busname, "/org/mpris/MediaPlayer2", Properties.class);
                    Map<String, Variant<?>> data = properties.GetAll("org.mpris.MediaPlayer2.Player");
                    data.putAll(properties.GetAll("org.mpris.MediaPlayer2"));
                    return updateData(data, null, true);
                }
            }
        } catch (DBusException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
        }
        return new PlayerInfo(busname, player);
    }

    protected PlayerInfo seeked(Player.Seeked signal) {
        return new PlayerInfo(busname, name, repeat, shuffle, playing, existing, signal.getPosition() / microToMs, System.currentTimeMillis(), rate, volume, player, metadata);
    }

    protected PlayerInfo propertiesChanged(PropertiesChanged signal) {
        if (existing) {
            Map<String, Variant<?>> changed = signal.getPropertiesChanged();
            return updateData(changed, signal.getPropertiesRemoved(), false);
        } else {
            return refresh();
        }
    }

    public boolean isEmpty() {
        return busname.isEmpty();
    }

    public static class Metadata {
        public final String track, trackid, album, artist, lyrics, created_at, first_played, last_played, art_url, url;
        public final List<String> artists, album_artists, comments, composers, genres, lyricists;
        public final int bpm, disc, number, times_played;
        public final float auto_rating, user_rating;
        public final long duration;
        private final long creationTime;

        @Override
        public String toString() {
            // yeah, I'm to lazy
            return String.format(
                "{track: %s, trackid: %s, album: %s, artist: %s, artists: %s, duration: %s, ...}",
                track, trackid, album, artist, artists, duration
            );
        }

        Metadata() {
            this(Map.of());
        }

        Metadata(Map<String, ?> metadata) {
            trackid = getString(metadata.get("mpris:trackid"));
            Object length = metadata.get("mpris:length");
            if (length != null) {
                if (length instanceof UInt64) {
                    duration = ((UInt64) length).value().divide(BigInteger.valueOf(microToMs)).longValue();
                } else {
                    duration = (long) length / microToMs;
                }
            } else {
                duration = 0;
            }
            art_url = getString(metadata.get("mpris:artUrl"));
            album = getString(metadata.get("xesam:album"));
            album_artists = getList(metadata.get("xesam:albumArtist"));
            artists = getList(metadata.get("xesam:artist"));
            artist = artists.size() > 0 ? artists.get(0) : "";
            lyrics = getString(metadata.get("xesam:asText"));
            bpm = getNumber(metadata.get("xesam:audioBPM")).intValue();
            auto_rating = getNumber(metadata.get("xesam:autoRating")).floatValue();
            comments = getList(metadata.get("xesam:comment"));
            composers = getList(metadata.get("xesam:composer"));
            created_at = getString(metadata.get("xesam:contentCreated"));
            disc = getNumber(metadata.get("xesam:discNumber")).intValue();
            first_played = getString(metadata.get("xesam:firstUsed"));
            genres = getList(metadata.get("xesam:genre"));
            last_played = getString(metadata.get("xesam:lastUsed"));
            lyricists = getList(metadata.get("xesam:lyricist"));
            track = getString(metadata.get("xesam:title"));
            number = getNumber(metadata.get("xesam:trackNumber")).intValue();
            url = getString(metadata.get("xesam:url"));
            times_played = getNumber(metadata.get("xesam:useCount")).intValue();
            user_rating = getNumber(metadata.get("xesam:userRating")).floatValue();
            creationTime = System.currentTimeMillis();
        }

        private static String getString(Object obj) {
            if (obj instanceof String) {
                return (String) obj;
            } else {
                return "";
            }
        }

        private static Number getNumber(Object obj) {
            if (obj instanceof Number) {
                return (Number) obj;
            } else {
                return 0;
            }
        }

        private static List<String> getList(Object obj) {
            if (obj instanceof List) {
                List<?> tempList = (List<?>) obj;
                String[] list = new String[tempList.size()];
                for (int i = 0; i < tempList.size(); i++) {
                    list[i] = (String) tempList.get(i);
                }
                return List.of(list);
            } else {
                return List.of();
            }
        }

        public long data_age() {
            return System.currentTimeMillis() - creationTime;
        }
    }

    @Override
    public String toString() {
        return String.format(
            "{busname: %s, name: %s, repeat: %s, shuffle: %s, playing: %s, rate: %s, volume: %s, metadata: %s}",
            busname, name, repeat, shuffle, playing, rate, volume, metadata
        );
    }
}
