package technicfan.mpriscustomhud.mod_support;

//? if >=1.21.9 {
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

//? if >=1.21.11 {
/*
import dev.ngspace.hudder.api.functionsandconsumers.FunctionAndConsumerAPI;
import dev.ngspace.hudder.api.variableregistry.DataVariableRegistry;
import dev.ngspace.hudder.api.variableregistry.VariableTypes;
*/
//?} else {
import io.github.ngspace.hudder.compilers.utils.functionandconsumerapi.FunctionAndConsumerAPI;
import io.github.ngspace.hudder.data_management.api.DataVariableRegistry;
import io.github.ngspace.hudder.data_management.api.VariableTypes;
//?}
import technicfan.mpriscustomhud.MprisCustomHud;
//?}

public class HudderSupport {
    //? if >=1.21.9 {
    public static void register(
        ConcurrentHashMap<String, String> stringmap,
        ConcurrentHashMap<String, Boolean> boolmap,
        ConcurrentHashMap<String, Number> numbermap,
        ConcurrentHashMap<String, List<String>> listmap
    ){
        DataVariableRegistry.registerVariable(k -> true, VariableTypes.BOOLEAN, "has_mpris");
        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getCurrentPlayerInfo(), VariableTypes.OBJECT, "mpris_player_info");

        for (String key : stringmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> {
                String value = stringmap.get(key);
                return value.isEmpty() ? null : value;
            }, VariableTypes.STRING, key);
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

        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getCurrentProgress(), VariableTypes.NUMBER, "mpris_progress");
        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getCurrentDataAge(), VariableTypes.NUMBER, "mpris_data_age");

        FunctionAndConsumerAPI.getInstance().registerFunction((ui, c, args) -> {
                if (args.length < 1) {
                    return null;
                } else {
                    return MprisCustomHud.getPlayerInfo(args[0].asString());
                }
        }, "getPlayerInfo");
    }
    //?}
}
