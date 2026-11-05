package io.github.lukvi.verdenworld.core;

/** Resultado da amostragem de uma coluna (x,z) do mundo. Reutilizável para evitar alocação. */
public final class Column {
    public static final int NO_WATER = Integer.MIN_VALUE;
    /** Y do último bloco sólido. */
    public int surface;
    /** Y da superfície da água (último bloco de água), ou NO_WATER. */
    public int water = NO_WATER;
    /** Classe de bioma do Verden (ver biomes.csv); 0 = mar. */
    public int biomeClass;
    /** Id do bioma vanilla resolvido (ex.: minecraft:plains). */
    public String mcBiome;
    public boolean sea, river, lake, beach;
    /** Distância à costa em pixels do mapa (positivo = terra adentro). */
    public double coastPx;
    /** Quanto a coluna está “presa” a um platô de cidade (0..1). */
    public double flat;
    public String topBlock, fillerBlock;
    public int fillerDepth;

    public boolean hasWater() { return water != NO_WATER && water > surface; }
}
