package io.github.lukvi.verdenworld.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.lukvi.verdenworld.VerdenWorldMod;
import io.github.lukvi.verdenworld.core.Column;
import io.github.lukvi.verdenworld.core.Location;
import io.github.lukvi.verdenworld.core.Trees;
import io.github.lukvi.verdenworld.core.VerdenTerrain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Gerador do continente de Verden. Cada coluna vem de VerdenTerrain (determinístico, igual ao das imagens de pré-visualização).
 * v0.1: relevo, oceanos, rios, lagos, biomas e platôs das cidades. Sem cavernas e sem estruturas vanilla.
 */
public class VerdenChunkGenerator extends ChunkGenerator {
    public static final MapCodec<VerdenChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
                    .apply(instance, instance.stable(VerdenChunkGenerator::new)));

    private final Map<String, BlockState> states = new java.util.concurrent.ConcurrentHashMap<>();
    private final boolean markers = !"false".equals(System.getProperty("verden.markers", "true"));

    public VerdenChunkGenerator(BiomeSource biomeSource) { super(biomeSource); }

    @Override protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

    private BlockState state(String id) {
        return states.computeIfAbsent(id, k -> Registries.BLOCK.get(Identifier.of(k)).getDefaultState());
    }

    /** Folhas geradas fora de ordem precisam de persistent=true, senão decaem no primeiro tick aleatório. */
    private BlockState treeState(String id) {
        return states.computeIfAbsent(id, k -> {
            BlockState s = Registries.BLOCK.get(Identifier.of(k)).getDefaultState();
            return s.contains(net.minecraft.block.LeavesBlock.PERSISTENT) ? s.with(net.minecraft.block.LeavesBlock.PERSISTENT, true) : s;
        });
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        VerdenTerrain t = VerdenWorldMod.terrain();
        ChunkPos cp = chunk.getPos();
        Column col = new Column();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = cp.getStartX() + lx, z = cp.getStartZ() + lz;
            t.sample(x, z, col);
            int top = Math.max(col.surface, col.hasWater() ? col.water : col.surface);
            for (int y = VerdenTerrain.MIN_Y; y <= top; y++) {
                String id = t.blockAt(col, x, y, z);
                if ("minecraft:air".equals(id)) continue;
                chunk.setBlockState(pos.set(x, y, z), state(id), false);
            }
        }
        Trees.render(t, cp.getStartX(), cp.getStartZ(), cp.getEndX(), cp.getEndZ(), (x, y, z, id) -> {
            pos.set(x, y, z);
            if (chunk.getBlockState(pos).isAir()) chunk.setBlockState(pos, treeState(id), false);
        });
        io.github.lukvi.verdenworld.core.GroundCover.render(t, cp.getStartX(), cp.getStartZ(), cp.getEndX(), cp.getEndZ(), (x, y, z, id) -> {
            pos.set(x, y, z);
            if (chunk.getBlockState(pos).isAir()) chunk.setBlockState(pos, state(id), false);
        });
        if (markers) placeMarkers(t, chunk, cp, col, pos);
        Heightmap.populateHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /** Marcadores de topografia (provisórios, v0.1): um obelisco de pedra em cada localidade para validar a posição. Desligue com -Dverden.markers=false. */
    private void placeMarkers(VerdenTerrain t, Chunk chunk, ChunkPos cp, Column col, BlockPos.Mutable pos) {
        for (Location l : t.locations) {
            if (!isSettlement(l) || l.x < cp.getStartX() || l.x > cp.getEndX() || l.z < cp.getStartZ() || l.z > cp.getEndZ()) continue;
            t.sample(l.x, l.z, col);
            if (col.sea || col.hasWater()) continue;
            int h = l.isCapital() ? 48 : 24;
            BlockState body = state(l.pending || l.status.equals("TEXTO") || l.status.equals("GUIA-POS?") ? "minecraft:orange_terracotta" : "minecraft:stone_bricks");
            for (int dy = 1; dy <= h; dy++) chunk.setBlockState(pos.set(l.x, col.surface + dy, l.z), body, false);
            chunk.setBlockState(pos.set(l.x, col.surface + h + 1, l.z), state(l.isCapital() ? "minecraft:gold_block" : "minecraft:glowstone"), false);
        }
    }
    private static boolean isSettlement(Location l) {
        switch (l.kind) {
            case "floresta": case "campos": case "pântano": case "caverna": case "lago": case "lago/confluência": case "deserto":
            case "tundra-geleira": case "reservas": case "cordilheira": case "acidente-marítimo": return false;
            default: return true;
        }
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        Column c = VerdenWorldMod.terrain().sample(x, z);
        boolean floor = heightmap == Heightmap.Type.OCEAN_FLOOR || heightmap == Heightmap.Type.OCEAN_FLOOR_WG;
        return (!floor && c.hasWater() ? c.water : c.surface) + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        VerdenTerrain t = VerdenWorldMod.terrain();
        Column c = t.sample(x, z);
        BlockState[] arr = new BlockState[world.getHeight()];
        for (int i = 0; i < arr.length; i++) arr[i] = state(t.blockAt(c, x, world.getBottomY() + i, z));
        return new VerticalBlockSample(world.getBottomY(), arr);
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        VerdenTerrain t = VerdenWorldMod.terrain();
        Column c = t.sample(pos.getX(), pos.getZ());
        Location n = t.nearestLocation(pos.getX(), pos.getZ());
        double km = Math.hypot(n.x - pos.getX(), n.z - pos.getZ()) / 1000.0;
        text.add(String.format("Verden: %s | mais próximo: %s (%.1f km)", c.mcBiome, n.name, km));
    }

    // --- v0.1: sem cavernas, sem entidades geradas, superfície já montada em populateNoise
    @Override public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) { }
    @Override public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) { }
    @Override public void populateEntities(ChunkRegion region) { }
    @Override public int getWorldHeight() { return 384; }
    @Override public int getSeaLevel() { return VerdenTerrain.SEA; }
    @Override public int getMinimumY() { return VerdenTerrain.MIN_Y; }
}
