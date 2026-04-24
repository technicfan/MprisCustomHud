package technicfan.mpriscustomhud.mod_support;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;

public class ModSupport {
    public static void register(
        HashMap<String, Function<String, List<String>>> listmap
    ) {
        if (FabricLoader.getInstance().getModContainer("custom_hud").isPresent()) {
            CustomHudSupport.register(listmap);
        }
        if (FabricLoader.getInstance().getModContainer("hudder").isPresent()) {
            HudderSupport.register();
        }
    }
}
