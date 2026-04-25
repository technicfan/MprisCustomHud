# MprisCustomHud

## Important

- Linux only and intended to be used with [CustomHud](https://modrinth.com/mod/customhud) or [Hudder](https://modrinth.com/mod/hudder)

## Description

Variables for CustomHud and Hudder to show currently playing music using the Mpris DBus Spec.
<br>
Available for all versions CustomHud v4 is available for!
<br>
Works with Hudder 8.x, 9.x on version 1.21.9/10 and 10.x for version 1.21.11/26.1.x
<br>
It is heavily inspired by [Hudify](https://modrinth.com/mod/hudify), so thank you for your work [Lightningtow](https://github.com/Lightningtow) ig :)

### Note

- Not all variables have to be/will be populated by the player/music application

### CustomHud specific things

- From what I can tell, it is not easily possible to have a list as a field/attribte so here are only the lists for the current player
- All String variables are either not empty or `null`

| Name                  |                          Type/Usage                           | Description                                                                                     |
| :-------------------- | :-----------------------------------------------------------: | :---------------------------------------------------------------------------------------------- |
| `mpris_player`        | `{mpris_player:<field>}` or `{mpris_player:<player>:<field>}` | exposes the fields of a PlayerInfo Object; player can be the full busname or only the last part |
| `mpris_players`       |                List of PlayerInfo like Objects                | just a list of currently active PlayerInfo Objects (same `<fields>` as above)                   |
| `mpris_artists`       |                        List of String                         | list of artists for the current player                                                          |
| `mpris_album_artists` |                        List of String                         | `xesam:albumArtist` for the current player                                                      |
| `mpris_comments`      |                        List of String                         | `xesam:comment` for the current player                                                          |
| `mpris_composers`     |                        List of String                         | `xesam:composer` for the current player                                                         |
| `mpris_genres`        |                        List of String                         | `xesam:genre` for the current player                                                            |
| `mpris_lyricists`     |                        List of String                         | `xesam:lyricist` for the current player                                                         |

**Fields for CustomHuds PlayerInfo Objects**:

| Name            |  Type   | Description                                                                                                 |
| :-------------- | :-----: | :---------------------------------------------------------------------------------------------------------- |
| `busname`       | String  | busname                                                                                                     |
| `track`         | String  | track name                                                                                                  |
| `trackid`       | String  | the unique mpris track id (mostly for debugging)                                                            |
| `album`         | String  | name of the album the track is from                                                                         |
| `repeat`        | String  | repeat status - "None", "Track" or "Playlist"                                                               |
| `artist`        | String  | name of the first artist coming from mpris                                                                  |
| `name`          | String  | the pretty player name from the `Identity` attribute of `org.mpris.MediaPlayer2`                            |
| `lyrics`        | String  | `xesam:asText`                                                                                              |
| `created_at`    | String  | `xesam:contentCreated`                                                                                      |
| `first_played`  | String  | `xesam:firstUsed`                                                                                           |
| `last_played`   | String  | `xesam:lastUsed`                                                                                            |
| `art_url`       | String  | `xesam:artUrl`                                                                                              |
| `url`           | String  | `xesam:url`                                                                                                 |
| `shuffle`       | Boolean | wether shuffle is on                                                                                        |
| `playing`       | Boolean | wether the song is playing or paused/stopped                                                                |
| `exists`        | Boolean | wether the song is playing or paused/stopped                                                                |
| `has_album_art` | Boolean | wether the track has an album art/it is loaded                                                              |
| `data_age`      | Number  | the age of the metadata information (track, trackid, album, artist, artists, duration, ...) in milliseconds |
| `progress`      | Number  | progress in milliseconds                                                                                    |
| `duration`      | Number  | duration of the track in milliseconds                                                                       |
| `rate`          | Number  | the rate/speed the music is playing as floating point number                                                |
| `volume`        | Number  | the volume the music is playing at as a floating point number (usually between 0 and 1)                     |
| `bpm`           | Number  | `xesam:audioBPM`                                                                                            |
| `disc`          | Number  | `xesam:discNumber`                                                                                          |
| `number`        | Number  | `xesam:trackNumber`                                                                                         |
| `times_played`  | Number  | `xesam:useCount`                                                                                            |
| `auto_rating`   | Number  | `xesam:autoRating`                                                                                          |
| `user_rating`   | Number  | `xesam:userRating`                                                                                          |
| `album_width`   | Number  | the absolute pixel width of the album art                                                                   |
| `album_height`  | Number  | the absolute pixel height of the album art                                                                  |
| `album_color`   | Number  | the rgb value of the dominant color of the album art                                                        |
| `album_art`     |  Icon   | draws the album art image to the screen                                                                     |

See [https://www.freedesktop.org/wiki/Specifications/mpris-spec/metadata](https://www.freedesktop.org/wiki/Specifications/mpris-spec/metadata) for details on the `xesam:<name>` and `mpris:<name>` variables.

### Hudder specific things

#### Variables

| Name            |        Type        | Description                                                      |
| :-------------- | :----------------: | :--------------------------------------------------------------- |
| `has_mpris`     |      Boolean       | always true                                                      |
| `mpris_player`  | Object/PlayerInfo  | the PlayerInfo object of the currently selected player or `null` |
| `mpris_players` | List\<PlayerInfo\> | a list of currently tracked players                              |

#### Functions/Methods

| Name              |                                     Arguments                                     | Effect                                                                                                                                                                                                             |
| :---------------- | :-------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `getPlayerInfo`   |                                   `String name`                                   | returns the PlayerInfo object with the bus name `org.mpris.MediaPlayer2.<name>` or `null`                                                                                                                          |
| `mpris_album_art` | `String name` or `AlbumArt albumArt`, `int x`, `int y`, `int width`, `int height` | draws the album art image; if the track has no album art, it draws a fallback; if width or height is 0 it will retain the original aspect ratio; if both are 0, it will use the pixel dimensions (way too big lol) |

#### Objects

**PlayerInfo**:

- `String busname` - the busname
- `String name` - player name
- `String repeat` - repeat status - see `mpris_repeat`
- `boolean shuffle` - shuffle status
- `boolean playing` - wether music is playing
- `double rate` - the rate the music is playing at
- `double volume` - the volume the music is playing at (usually between 0 and 1)
- `Metadata metadata` - metadata
- `long progress()` - returns the current progress (in ms)

**Metadata**:

- `String track` - current track - `xesam:title`
- `String trackid` - current track id - `mpris:trackid`
- `String album` - current album - `xesam:album`
- `String artist` - first artist
- `String art_url` - `mpris:artUrl`
- `String lyrics` - `xesam:asText`
- `String created_at` - `xesam:contentCreated`
- `String first_played` - `xesam:firstUsed`
- `String last_played` - `xesam:lastUsed`
- `String url` - `xesam:url`
- `List<String> artists` - all artists - `xesam:artist`
- `List<String> album_artists` - `xesam:albumArtist`
- `List<String> comments` - `xesam:comment`
- `List<String> composers` - `xesam:composer`
- `List<String> genres` - `xesam:genre`
- `List<String> lyricists` - `xesam:lyricist`
- `int bpm` - `xesam:audioBPM`
- `int disc` - `xesam:discNumber`
- `int number` - `xesam:trackNumber`
- `int times_played` - `xesam:useCount`
- `float auto_rating` - `xesam:autoRating`
- `float user_rating` - `xesam:userRating`
- `long duration` - duration of current track (in ms)
- `AlbumArt album_art` - information on the album art
- `long data_age()` - returns the age of the object (in ms)

**AlbumArt**

- `int width` - width in pixels
- `int height` - height in pixels
- `int color` - the rgb value of the dominant color of the album art
- `boolean exists()` - wether the object for the real image or the fallback one

### Controls

There are keybindings for play/pause, next, previous, refresh and cycle through active players that all have correcsponding commands.

### Configuration

By default a player is selected from the active ones. To cycle through the currently active ones, use the `mpriscustomhud cycle` command (only works `onlyPreferred` is disabled (default)).
<br>
You can choose a player to prefer over others by using `mpriscustomhud preferred <player>` so that one will always be shown if it's active.
<br>
If you don't want to see other players (only the preferred one), you can use `mpriscustomhud onlyPreferred true`. This will result in no player being selected if you didn't set a preferred one.
<br>
With `mpriscustomhud player`, you get the currently active player, with `mpriscustomhud onlyPreferred` and `mpriscustomhud preferred` the values for that and with `mpriscustomhud refresh`, you can refresh the variables.

### Flatpak notice

- when you're running Minecraft in a Flatpak sandbox, you have to add `org.mpris.MediaPlayer2.*` to the list of well known session bus names your launcher can talk to e.g. with [Flatseal](https://github.com/tchx84/flatseal)

### Problems/Todo

- ~~currently there is no album art variable; maybe I will try to add it at some point~~ done

### Libraries used

- [dbus-java](https://github.com/hypfvieh/dbus-java)
    - Improved version of java DBus library provided by freedesktop.org [https://dbus.freedesktop.org/doc/dbus-java](https://dbus.freedesktop.org/doc/dbus-java)

### License

This Mod is licensed under the MIT License
