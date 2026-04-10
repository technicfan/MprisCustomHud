package technicfan.mpriscustomhud.mod_support;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.github.ngspace.hudder.data_management.api.DataVariableRegistry;
import io.github.ngspace.hudder.data_management.api.VariableTypes;
import technicfan.mpriscustomhud.MprisCustomHud;

public class HudderSupport {
    public static void register(
        ConcurrentHashMap<String, String> stringmap,
        ConcurrentHashMap<String, Boolean> boolmap,
        ConcurrentHashMap<String, Number> numbermap,
        ConcurrentHashMap<String, List<String>> listmap
    ) {
        DataVariableRegistry.registerVariable(k -> true, VariableTypes.BOOLEAN, "hash_mpris");

        for (String key : stringmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> stringmap.get(key), VariableTypes.STRING, key);
        }

        for (String key : boolmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> boolmap.get(key), VariableTypes.BOOLEAN, key);
        }

        for (String key : listmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> listmap.get(key).toArray(), VariableTypes.OBJECT, key);
        }

        for (String key : numbermap.keySet()) {
            DataVariableRegistry.registerVariable(k -> numbermap.get(key), VariableTypes.NUMBER, key);
        }

        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getCurrentPosition(), VariableTypes.NUMBER, "mpris_progress");
    }
}
