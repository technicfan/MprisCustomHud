package technicfan.mpriscustomhud;

import java.util.concurrent.CompletableFuture;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MprisCustomHudClient implements ClientModInitializer {
    //? if <1.21.9 {
    /*private static final String MOD_CATEGORY
              = String.format("key.category.%s.%s", MprisCustomHud.MOD_ID, MprisCustomHud.MOD_ID);*/
    //?} else
    private static final KeyMapping.Category MOD_CATEGORY = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MprisCustomHud.MOD_ID, MprisCustomHud.MOD_ID));

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal(MprisCustomHud.MOD_ID)
                        .then(ClientCommandManager.literal("preferred")
                                .then(ClientCommandManager.argument("preferred", StringArgumentType.string())
                                        .suggests(new PlayerSuggestionProvider())
                                        .executes(MprisCustomHudClient::updatePreferred))
                                .executes(MprisCustomHudClient::queryPreferred))
                        .then(ClientCommandManager.literal("onlyPreferred")
                                .then(ClientCommandManager.argument("onlyPreferred", BoolArgumentType.bool())
                                        .executes(MprisCustomHudClient::updateOnlyPreferred))
                                .executes(MprisCustomHudClient::queryOnlyPreferred))
                        .then(ClientCommandManager.literal("player")
                                .executes(MprisCustomHudClient::queryPlayer))
                        .then(ClientCommandManager.literal("cycle")
                                .executes(MprisCustomHudClient::cyclePlayers))
                        .then(ClientCommandManager.literal("refresh")
                                .executes(MprisCustomHudClient::refresh))
                        .then(ClientCommandManager.literal("clearCache")
                                .executes(MprisCustomHudClient::clearCache))
                        .then(ClientCommandManager.literal("playpause")
                                .executes(MprisCustomHudClient::playPausePlayer))
                        .then(ClientCommandManager.literal("play")
                                .executes(MprisCustomHudClient::playPlayer))
                        .then(ClientCommandManager.literal("pause")
                                .executes(MprisCustomHudClient::pausePlayer))
                        .then(ClientCommandManager.literal("next")
                                .executes(MprisCustomHudClient::nextPlayer))
                        .then(ClientCommandManager.literal("previous")
                                .executes(MprisCustomHudClient::previousPlayer))));

        KeyMapping playPauseBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "mpriscustomhud.key.playpause",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                MOD_CATEGORY));
        KeyMapping nextBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "mpriscustomhud.key.next",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                MOD_CATEGORY));
        KeyMapping prevBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "mpriscustomhud.key.prev",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                MOD_CATEGORY));
        KeyMapping refreshBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "mpriscustomhud.key.refresh",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                MOD_CATEGORY));
        KeyMapping cycleBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "mpriscustomhud.key.cycle",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                MOD_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (playPauseBinding.consumeClick()) {
                MprisCustomHud.getCurrentPlayerInfo().playpause();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (nextBinding.consumeClick()) {
                MprisCustomHud.getCurrentPlayerInfo().next();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (prevBinding.consumeClick()) {
                MprisCustomHud.getCurrentPlayerInfo().previous();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (refreshBinding.consumeClick()) {
                MprisCustomHud.refresh();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (cycleBinding.consumeClick()) {
                MprisCustomHud.cyclePlayers();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MprisCustomHud.close();
        });
    }

    private static int clearCache(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            AlbumArtManager.clear();;
        });
        return 1;
    }

    private static int refresh(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.refresh();
        });
        return 1;
    }

    private static int queryPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource()
                .sendFeedback(Component.translatable("mpriscustomhud.command.current_player", MprisCustomHud.getCurrentPlayerName()));
        return 1;
    }

    private static int cyclePlayers(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.cyclePlayers();
        });
        return 1;
    }

    private static int updateOnlyPreferred(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.setOnlyPreferred(BoolArgumentType.getBool(commandContext, "onlyPreferred"));
            queryOnlyPreferred(commandContext);
        });
        return 1;
    }

    private static int updatePreferred(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.setPreferred(StringArgumentType.getString(commandContext, "preferred"));
            commandContext.getSource()
                    .sendFeedback(
                            Component.translatable("mpriscustomhud.command.new_preferred", MprisCustomHud.getPreferred()));
        });
        return 1;
    }

    private static int queryOnlyPreferred(CommandContext<FabricClientCommandSource> commandContext)  {
        if (MprisCustomHud.getOnlyPreferred()) {
            commandContext.getSource().sendFeedback(Component.translatable("mpriscustomhud.command.only_preferred.true"));
        } else {
            commandContext.getSource().sendFeedback(Component.translatable("mpriscustomhud.command.only_preferred.false"));
        }
        return 1;
    }

    private static int queryPreferred(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource()
                .sendFeedback(
                        Component.translatable("mpriscustomhud.command.current_preferred", MprisCustomHud.getPreferred()));
        return 1;
    }

    private static int playPausePlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.getCurrentPlayerInfo().playpause();
        return 1;
    }

    private static int playPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.getCurrentPlayerInfo().play();
        return 1;
    }

    private static int pausePlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.getCurrentPlayerInfo().pause();
        return 1;
    }

    private static int nextPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.getCurrentPlayerInfo().next();
        return 1;
    }

    private static int previousPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.getCurrentPlayerInfo().previous();
        return 1;
    }
}
