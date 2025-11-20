# MprisCustomHud

## Linux only and depends on [CustomHud](https://modrinth.com/mod/customhud)

Just what you might expect from the name: Variables for CustomHud to show currently playing music using the Mpris DBus Spec.
<br>
Available for all versions CustomHud v4 is available for!
<br>
It is heavily inspired by [Hudify](https://modrinth.com/mod/hudify), so thank you for your work [Lightningtow](https://github.com/Lightningtow) ig :)

### Variables

| Name             |  Type   | Description                                                                     |
| :--------------- | :-----: | :------------------------------------------------------------------------------ |
| `mpris_track`    | String  | currently playing track name                                                    |
| `mpris_track_id` | String  | the unique mpris track id (mostly for debugging)                                |
| `mpris_album`    | String  | name of the album the track is from                                             |
| `mpris_loop`     | String  | loop status - "None", "Track" or "Playlist"                                     |
| `mpris_artist`   | String  | name of the first artist coming from mpris                                      |
| `mpris_artists`  | String  | comma seperated list of artists                                                 |
| `mpris_shuffle`  | Boolean | wether shuffle is on                                                            |
| `mpris_playing`  | Boolean | wether the song is playing or paused/stopped                                    |
| `mpris_progress` | Special | progress in (HH:)?MM:SS format, number of seconds, progress > 0                 |
| `mpris_duration` | Special | duration of the track in (HH:)?MM:SS format, number of seconds, duration > 0    |
| `mpris_rate`     | Special | the rate/speed the music is playing at, rate as floating point number, rate > 0 |

### Configuration

You can choose a mpris player (default is "spotify") you want to see with the `mpriscustomhud player <player>` command which will also suggest currently active ones.
<br>
With `mpriscustomhud player`, you get the chosen one and with `mpriscustomhud refresh`, you can refresh the variables for the selected player.

### Problems

- ~~I could not find a way to filter Mpris signals for a specific player, so the variables update in weird ways, when multiple Mpris players are active on the system~~
    - ~~when this happens, use the refresh command~~
- ~~the `mpris_progress` variable is not a perfect representation of the actual progress, but should be good enough~~<br>(it's still not perfect, but much better)

### Libraries used

- [dbus-java](https://github.com/hypfvieh/dbus-java)
    - Improved version of java DBus library provided by freedesktop.org [https://dbus.freedesktop.org/doc/dbus-java](https://dbus.freedesktop.org/doc/dbus-java)

### License

This Mod is licensed under the MIT License
