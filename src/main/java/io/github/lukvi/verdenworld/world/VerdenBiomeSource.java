package io.github.lukvi.verdenworld.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.lukvi.verdenworld.VerdenWorldMod;
import io.github.lukvi.verdenworld.core.Column;
import io.github.lukvi.verdenworld.core.VerdenTerrain;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryLookup;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

/** Bioma de cada ponto = o que o mapa de Verden manda (mesma função VerdenTerrain usada pelo terreno). */
public class VerdenBiomeSource extends BiomeSource {
    public static final MapCodec<VerdenBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME)).apply(instance, VerdenBiomeSource::new));

    private final RegistryEntryLookup<Biome> lookup;
    private final Map<String, RegistryEntry<Biome>> entries = new HashMap<>();
    private final RegistryEntry<Biome> fallback;
    private final ThreadLocal<Column> scratch = ThreadLocal.withInitial(Column::new);

    public VerdenBiomeSource(RegistryEntryLookup<Biome> lookup) {
        this.lookup = lookup;
        for (String id : VerdenWorldMod.terrain().allBiomeIds())
            entries.put(id, lookup.getOrThrow(RegistryKey.of(RegistryKeys.BIOME, Identifier.of(id))));
        this.fallback = entries.get("minecraft:plains");
    }

    @Override protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }

    @Override protected Stream<RegistryEntry<Biome>> biomeStream() { return entries.values().stream(); }

    /** x, y, z em coordenadas de quarto de bloco (bloco >> 2). */
    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        Column c = scratch.get();
        VerdenWorldMod.terrain().sample((x << 2) + 2, (z << 2) + 2, c);
        RegistryEntry<Biome> e = entries.get(c.mcBiome);
        return e != null ? e : fallback;
    }
}
