package de.example.whold;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class WHoldClient implements ClientModInitializer {
    private enum Move {
        W, A, D, SNEAK
    }

    private static Move activeMove = null;
    private static long startAtNanos = 0L;
    private static long releaseAtNanos = 0L;
    private static boolean waiting = false;
    private static boolean holding = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                literal("move")
                    .then(moveArgument("w", Move.W))
                    .then(moveArgument("a", Move.A))
                    .then(moveArgument("d", Move.D))
                    .then(moveArgument("sneak", Move.SNEAK))
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.nanoTime();

            if (waiting && now >= startAtNanos) {
                press(client, activeMove);
                waiting = false;
                holding = true;
            }

            if (holding && now >= releaseAtNanos) {
                release(client, activeMove);
                holding = false;
                activeMove = null;
            }
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
    moveArgument(String name, Move move) {
        return literal(name)
            .then(argument("laufzeit", FloatArgumentType.floatArg(0.05f, 3600.0f))
                .then(argument("verzoegerung", FloatArgumentType.floatArg(0.0f, 3600.0f))
                    .executes(context -> {
                        float duration = FloatArgumentType.getFloat(context, "laufzeit");
                        float delay = FloatArgumentType.getFloat(context, "verzoegerung");
                        Minecraft client = Minecraft.getInstance();

                        if (activeMove != null) {
                            release(client, activeMove);
                        }

                        activeMove = move;
                        startAtNanos = System.nanoTime() + (long) (delay * 1_000_000_000L);
                        releaseAtNanos = startAtNanos + (long) (duration * 1_000_000_000L);
                        waiting = delay > 0.0f;
                        holding = false;

                        if (!waiting) {
                            press(client, activeMove);
                            holding = true;
                        }

                        context.getSource().sendFeedback(Component.literal(
                            "Halte " + displayName(move) + " für " + duration +
                            " Sekunden" + (delay > 0.0f ? " nach " + delay + " Sekunden Verzögerung." : ".")
                        ));
                        return 1;
                    })
                )
            );
    }

    private static String displayName(Move move) {
        return switch (move) {
            case W -> "W";
            case A -> "A";
            case D -> "D";
            case SNEAK -> "Sneak";
        };
    }

    private static void press(Minecraft client, Move move) {
        switch (move) {
            case W -> {
                client.options.keyUp.setDown(true);
                client.options.keySprint.setDown(true);
            }
            case A -> client.options.keyLeft.setDown(true);
            case D -> client.options.keyRight.setDown(true);
            case SNEAK -> client.options.keyShift.setDown(true);
        }
    }

    private static void release(Minecraft client, Move move) {
        if (move == null) return;
        switch (move) {
            case W -> {
                client.options.keyUp.setDown(false);
                client.options.keySprint.setDown(false);
            }
            case A -> client.options.keyLeft.setDown(false);
            case D -> client.options.keyRight.setDown(false);
            case SNEAK -> client.options.keyShift.setDown(false);
        }
    }
}
