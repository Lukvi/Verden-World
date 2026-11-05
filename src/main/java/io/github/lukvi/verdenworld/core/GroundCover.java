package io.github.lukvi.verdenworld.core;

/** Cobertura de chão determinística (grama, samambaia, flores, arbustos secos). Expansão ambiental: espécies e densidades por bioma são minhas. */
public final class GroundCover {
    private GroundCover() { }

    private static final String[] FLOWERS = {"minecraft:dandelion", "minecraft:poppy", "minecraft:azure_bluet", "minecraft:cornflower", "minecraft:oxeye_daisy"};

    /** Desenha a planta (no máximo uma por coluna, em y = superfície+1) de cada coluna da janela. */
    public static void render(VerdenTerrain t, int minX, int minZ, int maxX, int maxZ, Trees.Sink out) {
        Column c = new Column();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            long h = Trees.mix(x, z, 7);
            double r = ((h >>> 11) & 0xFFFF) / 65536.0;
            if (r > 0.45) continue; // maior densidade usada abaixo; evita amostrar o terreno à toa
            t.sample(x, z, c);
            if (c.sea || c.hasWater() || c.surface + 1 >= VerdenTerrain.MAX_Y) continue;
            String id = pick(c, r, h);
            if (id != null) out.set(x, c.surface + 1, z, id);
        }
    }

    private static String pick(Column c, double r, long h) {
        boolean grass = "minecraft:grass_block".equals(c.topBlock);
        switch (c.biomeClass) {
            case VerdenTerrain.PLAINS: return grass ? (r < 0.30 ? "minecraft:short_grass" : r < 0.315 ? flower(h) : null) : null;
            case VerdenTerrain.MEADOW: return grass ? (r < 0.25 ? "minecraft:short_grass" : r < 0.40 ? flower(h) : null) : null;
            case VerdenTerrain.FOREST_BROTTUS: case VerdenTerrain.FOREST_VERLAAF: case VerdenTerrain.FOREST_GRON:
                return grass ? (r < 0.22 ? "minecraft:short_grass" : r < 0.235 ? flower(h) : null) : null;
            case VerdenTerrain.FOREST_MORKE: return grass ? (r < 0.10 ? "minecraft:short_grass" : null) : null;
            case VerdenTerrain.FOREST_KARTOFFEL: case VerdenTerrain.TAIGA: case VerdenTerrain.SNOW_TAIGA:
                return grass ? (r < 0.15 ? "minecraft:short_grass" : r < 0.30 ? "minecraft:fern" : null) : null;
            case VerdenTerrain.JUNGLE: case VerdenTerrain.SPARSE_JUNGLE:
                return grass ? (r < 0.25 ? "minecraft:short_grass" : r < 0.40 ? "minecraft:fern" : null) : null;
            case VerdenTerrain.SWAMP: return grass ? (r < 0.25 ? "minecraft:short_grass" : r < 0.255 ? "minecraft:blue_orchid" : null) : null;
            case VerdenTerrain.DESERT: return r < 0.004 ? "minecraft:dead_bush" : null;
            case VerdenTerrain.BADLANDS: return r < 0.012 ? "minecraft:dead_bush" : null;
            default: return null;
        }
    }

    private static String flower(long h) { return FLOWERS[(int) ((h >>> 40) % FLOWERS.length)]; }
}
