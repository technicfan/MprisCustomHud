package technicfan.mpriscustomhud.mod_support;

import java.util.HashMap;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import technicfan.mpriscustomhud.MprisCustomHud;

public class ModSupport {
    public static void register(
        HashMap<String, MprisCustomHud.Function<String>> stringmap,
        HashMap<String, MprisCustomHud.Function<Boolean>> boolmap,
        HashMap<String, MprisCustomHud.Function<Number>> numbermap,
        HashMap<String, MprisCustomHud.Function<List<String>>> listmap
    ) {
        if (FabricLoader.getInstance().getModContainer("custom_hud").isPresent()) {
            CustomHudSupport.register(stringmap, boolmap, numbermap, listmap);
        }
        if (FabricLoader.getInstance().getModContainer("hudder").isPresent()) {
            HudderSupport.register(stringmap, boolmap, numbermap, listmap);
        }
    }
}
