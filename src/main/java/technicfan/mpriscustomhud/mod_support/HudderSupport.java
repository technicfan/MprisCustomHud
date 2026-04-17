package technicfan.mpriscustomhud.mod_support;

import java.util.List;
import java.util.HashMap;

//? if >=1.21.11 {
/*
import dev.ngspace.hudder.api.functionsandconsumers.FunctionAndConsumerAPI;
import dev.ngspace.hudder.api.variableregistry.DataVariableRegistry;
import dev.ngspace.hudder.api.variableregistry.VariableTypes;
*/
//?} else {
import io.github.ngspace.hudder.compilers.utils.functionandconsumerapi.FunctionAndConsumerAPI;
//? if >=1.21.9 {
import io.github.ngspace.hudder.data_management.api.DataVariableRegistry;
import io.github.ngspace.hudder.data_management.api.VariableTypes;
//?} else {
/*import io.github.ngspace.hudder.data_management.ObjectDataAPI;*/
//?}
//?}
import technicfan.mpriscustomhud.MprisCustomHud;

public class HudderSupport {
    public static void register(
        HashMap<String, MprisCustomHud.Function<String>> stringmap,
        HashMap<String, MprisCustomHud.Function<Boolean>> boolmap,
        HashMap<String, MprisCustomHud.Function<Number>> numbermap,
        HashMap<String, MprisCustomHud.Function<List<String>>> listmap
    ) {
        //? if >=1.21.9 {
        DataVariableRegistry.registerVariable(k -> true, VariableTypes.BOOLEAN, "has_mpris");
        DataVariableRegistry.registerVariable(k ->
            MprisCustomHud.getCurrentPlayerInfo().isEmpty() ? null : MprisCustomHud.getCurrentPlayerInfo(),
                VariableTypes.OBJECT, "mpris_player_info");
        DataVariableRegistry.registerVariable(k -> MprisCustomHud.getAvailablePlayers(), VariableTypes.OBJECT, "mpris_players");

        for (String key : stringmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> {
                String value = stringmap.get(key).run();
                return value.isEmpty() ? null : value;
            }, VariableTypes.STRING, key);
        }

        for (String key : boolmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> boolmap.get(key).run(), VariableTypes.BOOLEAN, key);
        }

        for (String key : listmap.keySet()) {
            DataVariableRegistry.registerVariable(k -> listmap.get(key).run(), VariableTypes.OBJECT, key);
        }

        for (String key : numbermap.keySet()) {
            DataVariableRegistry.registerVariable(k -> numbermap.get(key).run(), VariableTypes.NUMBER, key);
        }
        //?} else {
        /*
        ObjectDataAPI.addObjectGetter(k -> k.equals("has_mpris") ? true : null);
        ObjectDataAPI.addObjectGetter(k ->
            k.equals("mpris_player_info") && !MprisCustomHud.getCurrentPlayerInfo().isEmpty() ? MprisCustomHud.getCurrentPlayerInfo() : null);
        ObjectDataAPI.addObjectGetter(k -> k.equals("mpris_players") ? MprisCustomHud.getAvailablePlayers() : null);

        for (String key : stringmap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> {
                if (!k.equals(key)) return null;
                String value = stringmap.get(key).run();
                return value.isEmpty() ? null : value;
            });
        }

        for (String key : boolmap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> k.equals(key) ? boolmap.get(key).run() : null);
        }

        for (String key : listmap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> k.equals(key) ? listmap.get(key).run() : null);
        }

        for (String key : numbermap.keySet()) {
            ObjectDataAPI.addObjectGetter(k -> k.equals(key) ? numbermap.get(key).run() : null);
        }
        */
        //?}

        FunctionAndConsumerAPI.getInstance().registerFunction((ui, c, args) -> {
                if (args.length < 1) {
                    return null;
                } else {
                    return MprisCustomHud.getPlayerInfo(args[0].asString());
                }
        }, "getPlayerInfo");

        MprisCustomHud.log("Registered Hudder variables and functions");
    }
}
