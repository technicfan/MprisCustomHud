package technicfan.mpriscustomhud;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class MprisCustomHudClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal(MprisCustomHud.MOD_ID)
                        .then(ClientCommandManager.literal("player")
                                .then(ClientCommandManager.argument("player", StringArgumentType.string())
                                        .suggests(new PlayerSuggestionProvider())
                                        .executes(MprisCustomHudClient::updatePlayer))
                                .executes(MprisCustomHudClient::queryPlayer))
                .then(ClientCommandManager.literal("refresh")
            .executes(MprisCustomHudClient::refreshPlayer))
        ));
    }

    private static int refreshPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.setPlayer(MprisCustomHud.getPlayer());
        });
        return 1;
    }

    private static int updatePlayer(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.setPlayer(StringArgumentType.getString(commandContext, "player"));
        });
        return 1;
    }

    private static int queryPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource().sendFeedback(Text.literal("Your current player is " + MprisCustomHud.getPlayer()));
        return 1;
    }
}
