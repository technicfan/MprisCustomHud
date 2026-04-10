package technicfan.mpriscustomhud.mod_support;

//? if <=1.21.10 {
import static com.minenash.customhud.data.Flags.wrap;
import static com.minenash.customhud.registry.CustomHudRegistry.registerElement;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.minenash.customhud.HudElements.supplier.BooleanSupplierElement;
import com.minenash.customhud.HudElements.supplier.NumberSupplierElement;
import com.minenash.customhud.HudElements.supplier.StringSupplierElement;

import technicfan.mpriscustomhud.MprisCustomHud;
//?}

public class CustomHudSupport {
    //? if <=1.21.10 {
    public static void register(
        ConcurrentHashMap<String, String> stringmap,
        ConcurrentHashMap<String, Boolean> boolmap,
        ConcurrentHashMap<String, Number> numbermap,
        ConcurrentHashMap<String, List<String>> listmap
    ) {
        for (String key : stringmap.keySet()) {
            registerElement(key,
                (f, c) -> wrap(new StringSupplierElement(() -> {
                    String value = stringmap.get(key);
                    return value.isEmpty() ? null : value;
                }), f));
        }

        for (String key : boolmap.keySet()) {
            registerElement(key, (f, c) -> wrap(new BooleanSupplierElement(() -> boolmap.get(key)), f));
        }

        for (String key : listmap.keySet()) {
            registerElement(key, (f, c) -> wrap(new StringSupplierElement(() -> String.join(", ", listmap.get(key))), f));
        }

        for (String key : numbermap.keySet()) {
            registerElement(key, (f, c) -> {
                if (f.formatted) {
                    return new StringSupplierElement(() -> MprisCustomHud.formatMicro(numbermap.get(key)));
                } else {
                    return new NumberSupplierElement(() -> numbermap.get(key), f);
                }
            });
        }

        registerElement("mpris_progress", (f, c) -> {
            if (f.formatted) {
                return new StringSupplierElement(() -> {
                    return MprisCustomHud.formatMicro(MprisCustomHud.getCurrentProgress());
                });
            } else {
                return new NumberSupplierElement(() -> MprisCustomHud.getCurrentProgress(), f);
            }
        });
        registerElement("mpris_data_age", (f, c) -> {
            if (f.formatted) {
                return new StringSupplierElement(() -> {
                    return MprisCustomHud.formatMicro(MprisCustomHud.getCurrentDataAge());
                });
            } else {
                return new NumberSupplierElement(() -> MprisCustomHud.getCurrentDataAge(), f);
            }
        });
    }
    //?}
}
