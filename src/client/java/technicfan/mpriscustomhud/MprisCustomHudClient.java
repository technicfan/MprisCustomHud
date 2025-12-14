package technicfan.mpriscustomhud;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MprisCustomHudClient implements ClientModInitializer {
    private final static String[] version = MinecraftClient.getInstance().getGameVersion().split("\\.");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal(MprisCustomHud.MOD_ID)
                        .then(ClientCommandManager.literal("filter")
                                .then(ClientCommandManager.argument("filter", StringArgumentType.string())
                                        .suggests(new PlayerSuggestionProvider())
                                        .executes(MprisCustomHudClient::updateFilter))
                                .executes(MprisCustomHudClient::queryFilter))
                        .then(ClientCommandManager.literal("preferred")
                                .then(ClientCommandManager.argument("preferred", StringArgumentType.string())
                                        .suggests(new PlayerSuggestionProvider())
                                        .executes(MprisCustomHudClient::updatePreferred))
                                .executes(MprisCustomHudClient::queryPreferred))
                        .then(ClientCommandManager.literal("player")
                                .executes(MprisCustomHudClient::queryPlayer))
                        .then(ClientCommandManager.literal("cycle")
                                .executes(MprisCustomHudClient::cyclePlayers))
                        .then(ClientCommandManager.literal("refresh")
                                .executes(MprisCustomHudClient::refreshPlayer))
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

        Object MOD_CATEGORY;
        Class<?> categoryClass;
        Constructor<KeyBinding> keybindingCtor;
        KeyBinding playPauseBinding, nextBinding, prevBinding, refreshBinding, cycleBinding;
        // Keybinding category with Keybinding.Category for Minecraft >= 1.21.9
        if (version.length == 3 && Integer.parseInt(version[0]) >= 1 && Integer.parseInt(version[1]) >= 21
                && Integer.parseInt(version[2]) >= 9) {
            try {
                MOD_CATEGORY = KeyBinding.Category.create(Identifier.of(MprisCustomHud.MOD_ID, MprisCustomHud.MOD_ID));
                keybindingCtor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class,
                        KeyBinding.Category.class);
                categoryClass = KeyBinding.Category.class;
            } catch (NoClassDefFoundError | NoSuchMethodException | NoSuchMethodError e) {
                MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
                return;
            }
            // Keybinding category with String for Minecraft < 1.21.9
        } else {
            try {
                MOD_CATEGORY = "key.category.mpriscustomhud.mpriscustomhud";
                keybindingCtor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class,
                        String.class);
                categoryClass = String.class;
            } catch (NoSuchMethodException | NoSuchMethodError e) {
                MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
                return;
            }
        }

        try {
            playPauseBinding = KeyBindingHelper.registerKeyBinding(keybindingCtor.newInstance(
                    "mpriscustomhud.key.playpause",
                    InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(),
                    categoryClass.cast(MOD_CATEGORY)));
            nextBinding = KeyBindingHelper.registerKeyBinding(keybindingCtor.newInstance(
                    "mpriscustomhud.key.next",
                    InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(),
                    categoryClass.cast(MOD_CATEGORY)));
            prevBinding = KeyBindingHelper.registerKeyBinding(keybindingCtor.newInstance(
                    "mpriscustomhud.key.prev",
                    InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(),
                    categoryClass.cast(MOD_CATEGORY)));
            refreshBinding = KeyBindingHelper.registerKeyBinding(keybindingCtor.newInstance(
                    "mpriscustomhud.key.refresh",
                    InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(),
                    categoryClass.cast(MOD_CATEGORY)));
            cycleBinding = KeyBindingHelper.registerKeyBinding(keybindingCtor.newInstance(
                    "mpriscustomhud.key.cycle",
                    InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(),
                    categoryClass.cast(MOD_CATEGORY)));
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
            MprisCustomHud.LOGGER.error(e.toString(), e.fillInStackTrace());
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (playPauseBinding.wasPressed()) {
                MprisCustomHud.playPause();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (nextBinding.wasPressed()) {
                MprisCustomHud.next();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (prevBinding.wasPressed()) {
                MprisCustomHud.previous();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (refreshBinding.wasPressed()) {
                MprisCustomHud.refresh();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (cycleBinding.wasPressed()) {
                MprisCustomHud.cyclePlayers();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MprisCustomHud.close();
        });
    }

    private static int refreshPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.refresh();
        });
        return 1;
    }

    private static int queryPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource()
                .sendFeedback(Text.translatable("mpriscustomhud.command.current_player", MprisCustomHud.getPlayer()));
        return 1;
    }

    private static int cyclePlayers(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.cyclePlayers();
        });
        return 1;
    }

    private static int updateFilter(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.setFilter(StringArgumentType.getString(commandContext, "filter"));
            commandContext.getSource()
                    .sendFeedback(Text.translatable("mpriscustomhud.command.new_filter", MprisCustomHud.getFilter()));
        });
        return 1;
    }

    private static int queryFilter(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource()
                .sendFeedback(Text.translatable("mpriscustomhud.command.current_filter", MprisCustomHud.getFilter()));
        return 1;
    }

    private static int updatePreferred(CommandContext<FabricClientCommandSource> commandContext) {
        CompletableFuture.runAsync(() -> {
            MprisCustomHud.setPreferred(StringArgumentType.getString(commandContext, "preferred"));
            commandContext.getSource()
                    .sendFeedback(
                            Text.translatable("mpriscustomhud.command.new_preferred", MprisCustomHud.getPreferred()));
        });
        return 1;
    }

    private static int queryPreferred(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource()
                .sendFeedback(
                        Text.translatable("mpriscustomhud.command.current_preferred", MprisCustomHud.getPreferred()));
        return 1;
    }

    private static int playPausePlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.playPause();
        return 1;
    }

    private static int playPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.play();
        return 1;
    }

    private static int pausePlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.pause();
        return 1;
    }

    private static int nextPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.next();
        return 1;
    }

    private static int previousPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        MprisCustomHud.previous();
        return 1;
    }
}
