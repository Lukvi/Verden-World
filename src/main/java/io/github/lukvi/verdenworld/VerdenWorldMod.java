package io.github.lukvi.verdenworld;

import io.github.lukvi.verdenworld.core.Location;
import io.github.lukvi.verdenworld.core.VerdenTerrain;
import io.github.lukvi.verdenworld.world.VerdenBiomeSource;
import io.github.lukvi.verdenworld.world.VerdenChunkGenerator;
import io.github.lukvi.verdenworld.world.VerdenCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verden-World: o continente de Verden em escala 1:1. Mod independente (não depende de nenhum outro projeto Verden). */
public class VerdenWorldMod implements ModInitializer {
    public static final String ID = "verden_world";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    private static VerdenTerrain terrain;

    /** Terreno único e imutável do mundo (carregado uma vez). */
    public static synchronized VerdenTerrain terrain() {
        if (terrain == null) {
            try { terrain = VerdenTerrain.loadFromClasspath(); }
            catch (Exception e) { throw new IllegalStateException("Falha ao carregar os mapas do Verden", e); }
        }
        return terrain;
    }

    public static Identifier id(String path) { return Identifier.of(ID, path); }

    @Override
    public void onInitialize() {
        Registry.register(Registries.CHUNK_GENERATOR, id("continent"), VerdenChunkGenerator.CODEC);
        Registry.register(Registries.BIOME_SOURCE, id("continent"), VerdenBiomeSource.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VerdenCommands.register(dispatcher));

        // Mundo novo de Verden: nasce em Jord. (Só quando o mundo ainda não tem tempo de jogo.)
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD || !(world.getChunkManager().getChunkGenerator() instanceof VerdenChunkGenerator)) return;
            if (world.getTime() > 20) return;
            Location jord = terrain().byId.get("jord");
            int y = terrain().sample(jord.x, jord.z).surface + 1;
            world.setSpawnPos(new BlockPos(jord.x, y, jord.z), 0.0f);
            LOG.info("Spawn de Verden definido em Jord ({}, {}, {})", jord.x, y, jord.z);
        });
        LOG.info("Verden-World carregado: {} localidades, escala 1:1", terrain().locations.size());
    }
}
