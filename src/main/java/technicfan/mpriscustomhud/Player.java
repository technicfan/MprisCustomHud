package technicfan.mpriscustomhud;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.interfaces.DBusInterface;

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
public interface Player extends DBusInterface {
    public class Seeked extends DBusSignal {
        private final long position;

        public Seeked(String path, long x) throws DBusException {
            super(path, x);

            this.position = x;
        }

        public long getPosition() {
            return position;
        }
    }
}
