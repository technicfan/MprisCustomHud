package technicfan.mpriscustomhud.mod_support;

//? if <=1.21.10 {
import static com.minenash.customhud.data.Flags.wrap;
import static com.minenash.customhud.registry.CustomHudRegistry.registerElement;

import java.util.HashMap;
import java.util.List;

import com.minenash.customhud.HudElements.supplier.BooleanSupplierElement;
import com.minenash.customhud.HudElements.supplier.NumberSupplierElement;
import com.minenash.customhud.HudElements.supplier.StringSupplierElement;

import technicfan.mpriscustomhud.MprisCustomHud;
//?}

public class CustomHudSupport {
    //? if <=1.21.10 {
    public static void register(
        HashMap<String, MprisCustomHud.Function<String>> stringmap,
        HashMap<String, MprisCustomHud.Function<Boolean>> boolmap,
        HashMap<String, MprisCustomHud.Function<Number>> numbermap,
        HashMap<String, MprisCustomHud.Function<List<String>>> listmap
    ) {
        for (String key : stringmap.keySet()) {
            registerElement(key,
                (f, c) -> wrap(new StringSupplierElement(() -> {
                    String value = stringmap.get(key).run();
                    return value.isEmpty() ? null : value;
                }), f));
        }

        for (String key : boolmap.keySet()) {
            registerElement(key, (f, c) -> wrap(new BooleanSupplierElement(() -> boolmap.get(key).run()), f));
        }

        for (String key : listmap.keySet()) {
            registerElement(key, (f, c) -> wrap(new StringSupplierElement(() -> String.join(", ", listmap.get(key).run())), f));
        }

        for (String key : numbermap.keySet()) {
            registerElement(key, (f, c) -> {
                if (f.formatted) {
                    return new StringSupplierElement(() -> MprisCustomHud.formatMicro(numbermap.get(key).run()));
                } else {
                    return new NumberSupplierElement(() -> numbermap.get(key).run(), f);
                }
            });
        }
    }
    //?}
}
