package technicfan.mpriscustomhud;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.concurrent.CompletableFuture;

public class PlayerSuggestionProvider implements SuggestionProvider<FabricClientCommandSource> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        if (MprisCustomHud.dbus != null) {
            for (String name : MprisCustomHud.dbus.ListNames()) {
                if (name.startsWith("org.mpris.MediaPlayer2."))
                    builder.suggest(name.replace("org.mpris.MediaPlayer2.", ""));
            }
        }

        return builder.buildFuture();
    }
}
