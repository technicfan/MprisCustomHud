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
                        .then(ClientCommandManager.literal("player")
                                .then(ClientCommandManager.argument("player", StringArgumentType.string())
                                        .suggests(new PlayerSuggestionProvider())
                                        .executes(MprisCustomHudClient::updatePlayer))
                                .executes(MprisCustomHudClient::queryPlayer))
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
        KeyBinding playPauseBinding, nextBinding, prevBinding, refreshBinding;
        // Keybinding category with Keybinding.Category for Minecraft >= 1.21.9
        if (version.length == 3 && Integer.parseInt(version[0]) >= 1 && Integer.parseInt(version[1]) >= 21
                && Integer.parseInt(version[2]) >= 9) {
            try {
                MOD_CATEGORY = KeyBinding.Category.create(Identifier.of(MprisCustomHud.MOD_ID, MprisCustomHud.MOD_ID));
                keybindingCtor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class,
                        KeyBinding.Category.class);
                categoryClass = KeyBinding.Category.class;
            } catch (NoClassDefFoundError | NoSuchMethodException | NoSuchMethodError e) {
                return;
            }
            // Keybinding category with String for Minecraft < 1.21.9
        } else {
            try {
                MOD_CATEGORY = "key.category.mpriscustomhud.mpriscustomhud";
                keybindingCtor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class,
                        String.class);
                categoryClass = String.class;
            } catch (NoSuchMethodException | NoSuchMethodError f) {
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
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
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
                MprisCustomHud.refreshValues();
            }
        });
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
            commandContext.getSource()
                    .sendFeedback(Text.translatable("mpriscustomhud.command.new_player", MprisCustomHud.getPlayer()));
        });
        return 1;
    }

    private static int queryPlayer(CommandContext<FabricClientCommandSource> commandContext) {
        commandContext.getSource()
                .sendFeedback(Text.translatable("mpriscustomhud.command.current_player", MprisCustomHud.getPlayer()));
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
