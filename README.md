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

| Name             |  Type   | Description                                                                      |
| :--------------- | :-----: | :------------------------------------------------------------------------------- |
| `mpris_track`    | String  | currently playing track name                                                     |
| `mpris_track_id` | String  | the unique mpris track id (mostly for debugging)                                 |
| `mpris_album`    | String  | name of the album the track is from                                              |
| `mpris_loop`     | String  | loop status - "None", "Track" or "Playlist"                                      |
| `mpris_artist`   | String  | name of the first artist coming from mpris                                       |
| `mpris_artists`  | String  | comma seperated list of artists                                                  |
| `mpris_player`   | String  | the pretty player name from the `Identity` attribute of `org.mpris.MediaPlayer2` |
| `mpris_shuffle`  | Boolean | wether shuffle is on                                                             |
| `mpris_playing`  | Boolean | wether the song is playing or paused/stopped                                     |
| `mpris_progress` | Special | progress in (HH:)?MM:SS format, number of seconds, progress > 0                  |
| `mpris_duration` | Special | duration of the track in (HH:)?MM:SS format, number of seconds, duration > 0     |
| `mpris_rate`     | Special | the rate/speed the music is playing at, rate as floating point number, rate > 0  |

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
