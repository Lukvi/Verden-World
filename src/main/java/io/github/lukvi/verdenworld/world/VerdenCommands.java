package io.github.lukvi.verdenworld.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.lukvi.verdenworld.VerdenWorldMod;
import io.github.lukvi.verdenworld.core.Column;
import io.github.lukvi.verdenworld.core.Location;
import io.github.lukvi.verdenworld.core.VerdenTerrain;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Ferramentas de validação da geografia: /verden tp <local>, /verden list, /verden where. */
public final class VerdenCommands {
    private VerdenCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("verden")
                .then(CommandManager.literal("tp").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("local", StringArgumentType.word())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(VerdenWorldMod.terrain().byId.keySet(), b))
                                .executes(ctx -> tp(ctx.getSource(), StringArgumentType.getString(ctx, "local")))))
                .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(CommandManager.literal("where").executes(ctx -> where(ctx.getSource()))));
    }

    private static int tp(ServerCommandSource src, String id) {
        VerdenTerrain t = VerdenWorldMod.terrain();
        Location l = t.byId.get(id);
        if (l == null) { src.sendError(Text.literal("Local desconhecido: " + id + " (use /verden list)")); return 0; }
        try {
            ServerPlayerEntity p = src.getPlayerOrThrow();
            ServerWorld w = src.getServer().getOverworld();
            Column c = t.sample(l.x, l.z);
            int y = Math.max(c.surface, c.hasWater() ? c.water : c.surface) + 3;
            p.teleport(w, l.x + 0.5, y, l.z + 0.5, p.getYaw(), p.getPitch());
            src.sendFeedback(() -> Text.literal(l.name + " (" + l.x + ", " + l.z + ")" + (l.pending ? "  [posição pendente de confirmação]" : "")), false);
            return 1;
        } catch (Exception e) { src.sendError(Text.literal("Apenas jogadores podem usar /verden tp")); return 0; }
    }

    private static int list(ServerCommandSource src) {
        StringBuilder sb = new StringBuilder("Locais: ");
        for (Location l : VerdenWorldMod.terrain().locations) sb.append(l.id).append(", ");
        src.sendFeedback(() -> Text.literal(sb.toString()), false);
        return 1;
    }

    private static int where(ServerCommandSource src) {
        VerdenTerrain t = VerdenWorldMod.terrain();
        int x = (int) Math.floor(src.getPosition().x), z = (int) Math.floor(src.getPosition().z);
        Column c = t.sample(x, z); Location n = t.nearestLocation(x, z);
        double km = Math.hypot(n.x - x, n.z - z) / 1000.0;
        src.sendFeedback(() -> Text.literal(String.format("Bioma: %s | Região mais próxima: %s (%s), a %.1f km | X=%d Z=%d (origem: Interseção dos Rios)", c.mcBiome, n.name, n.region, km, x, z)), false);
        return 1;
    }
}
