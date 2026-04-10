# MprisCustomHud

## Important

- Linux only and depends on [CustomHud](https://modrinth.com/mod/customhud)

## Description

Just what you might expect from the name: Variables for CustomHud to show currently playing music using the Mpris DBus Spec.
<br>
Available for all versions CustomHud v4 is available for!
<br>
It is heavily inspired by [Hudify](https://modrinth.com/mod/hudify), so thank you for your work [Lightningtow](https://github.com/Lightningtow) ig :)

### Variables

All String variables are either not empty or `null`

| Name             |               Type               | Description                                                                                            |
| :--------------- | :------------------------------: | :----------------------------------------------------------------------------------------------------- |
| `mpris_track`    |              String              | currently playing track name                                                                           |
| `mpris_trackid`  |              String              | the unique mpris track id (mostly for debugging)                                                       |
| `mpris_album`    |              String              | name of the album the track is from                                                                    |
| `mpris_repeat`   |              String              | repeat status - "None", "Track" or "Playlist"                                                          |
| `mpris_artist`   |              String              | name of the first artist coming from mpris                                                             |
| `mpris_artists`  | String/List\<String\> for Hudder | (comma seperated) list of artists                                                                      |
| `mpris_player`   |              String              | the pretty player name from the `Identity` attribute of `org.mpris.MediaPlayer2`                       |
| `mpris_shuffle`  |             Boolean              | wether shuffle is on                                                                                   |
| `mpris_playing`  |             Boolean              | wether the song is playing or paused/stopped                                                           |
| `mpris_data_age` |              Number              | the age of the metadata information (track, trackid, album, artist, artists, duration) in milliseconds |
| `mpris_progress` |              Number              | progress in milliseconds                                                                               |
| `mpris_duration` |              Number              | duration of the track in milliseconds                                                                  |
| `mpris_rate`     |              Number              | the rate/speed the music is playing as floating point number                                           |

### Hudder exclusive things

#### Variables

| Name                |       Type        | Description                                            |
| :------------------ | :---------------: | :----------------------------------------------------- |
| `has_mpris`         |      Boolean      | always true                                            |
| `mpris_player_info` | Object/PlayerInfo | the PlayerInfo object of the currently selected player |

#### Functions

| Name            |   Arguments   | Returns                                                                           |
| :-------------- | :-----------: | :-------------------------------------------------------------------------------- |
| `getPlayerInfo` | `String name` | the PlayerInfo object with the bus name `org.mpris.MediaPlayer2.<name>` or `null` |

#### Objects

**PlayerInfo**:

- `String busname` - the busname
- `String name` - player name
- `String repeat` - repeat status - see `mpris_repeat`
- `boolean shuffle` - shuffle status
- `boolean playing` - wether music is playing
- `double rate` - the rate the music is playing at
- `Metadata metadata` - metadata
- `long progress()` - returns the current progress (in ms)

**Metadata**:

- `String track` - current track
- `String trackid` - current track id
- `String album` - current album
- `String artist` - first artist
- `List<String> artists` - all artists
- `long duration` - duration of current track (in ms)
- `long data_age()` - returns the age of the object (in ms)

### Controls

There are keybindings for play/pause, next, previous, refresh and cycle through active players that all have correcsponding commands.

### Configuration

By default a player is selected from the active ones. To cycle through the currently active ones, use the `mpriscustomhud cycle` command (only works if no filter is set).
<br>
You can also choose a mpris player if you only want to see that one with the `mpriscustomhud filter <player>` command which will also suggest currently active ones.
<br>
If you still want to see other players but prefer one of them, use `mpriscustomhud preferred <player>` so that one will always be shown if it's active.
<br>
With `mpriscustomhud player`, you get the currently active player, with `mpriscustomhud filter` and `mpriscustomhud preferred` the values for that and with `mpriscustomhud refresh`, you can refresh the variables.

### Flatpak notice

- when you're running Minecraft in a Flatpak sandbox, you have to add `org.mpris.MediaPlayer2.*` to the list of well known session bus names your launcher can talk to e.g. with [Flatseal](https://github.com/tchx84/flatseal)

### Problems/Todo

- currently there is no album art variable; maybe I will try to add it at some point
- ~~the `mpris_progress` variable is not a perfect representation of the actual progress, but should be good enough~~<br>(it's still not perfect, but much better)

### Libraries used

- [dbus-java](https://github.com/hypfvieh/dbus-java)
    - Improved version of java DBus library provided by freedesktop.org [https://dbus.freedesktop.org/doc/dbus-java](https://dbus.freedesktop.org/doc/dbus-java)

### License

This Mod is licensed under the MIT License
