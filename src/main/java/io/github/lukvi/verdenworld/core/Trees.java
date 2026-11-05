package io.github.lukvi.verdenworld.core;

/**
 * Vegetação arbórea determinística (Java puro). Cada árvore depende só da sua célula 4x4 global, então qualquer janela
 * (um chunk, ou duas vizinhas) desenha a mesma árvore do mesmo jeito e as copas cruzam as bordas de chunk sem emenda.
 * É uma expansão ambiental: densidade e espécies por bioma são escolhas minhas, não canon.
 */
public final class Trees {
    private Trees() { }

    /** Recebe cada bloco da árvore; quem implementa decide se escreve (o gerador só escreve sobre ar). */
    public interface Sink { void set(int x, int y, int z, String blockId); }

    private static final int CELL = 4, REACH = 3;
    private enum Kind { OAK, BIRCH, DARK_OAK, SPRUCE, JUNGLE }

    private static double density(int biomeClass) {
        switch (biomeClass) {
            case VerdenTerrain.FOREST_BROTTUS: return 0.30;
            case VerdenTerrain.FOREST_MORKE: return 0.55;
            case VerdenTerrain.FOREST_GRON: return 0.40;
            case VerdenTerrain.FOREST_KARTOFFEL: return 0.32;
            case VerdenTerrain.FOREST_VERLAAF: return 0.28;
            case VerdenTerrain.SWAMP: return 0.07;
            case VerdenTerrain.JUNGLE: return 0.50;
            case VerdenTerrain.SPARSE_JUNGLE: return 0.14;
            case VerdenTerrain.TAIGA: return 0.22;
            case VerdenTerrain.SNOW_TAIGA: return 0.20;
            case VerdenTerrain.PLAINS: return 0.004;
            case VerdenTerrain.MEADOW: return 0.006;
            case VerdenTerrain.SNOW_PLAINS: return 0.003;
            default: return 0;
        }
    }

    private static Kind kind(int biomeClass) {
        switch (biomeClass) {
            case VerdenTerrain.FOREST_MORKE: return Kind.DARK_OAK;
            case VerdenTerrain.FOREST_GRON: case VerdenTerrain.FOREST_VERLAAF: return Kind.BIRCH;
            case VerdenTerrain.FOREST_KARTOFFEL: case VerdenTerrain.TAIGA: case VerdenTerrain.SNOW_TAIGA: case VerdenTerrain.SNOW_PLAINS: return Kind.SPRUCE;
            case VerdenTerrain.JUNGLE: case VerdenTerrain.SPARSE_JUNGLE: return Kind.JUNGLE;
            default: return Kind.OAK;
        }
    }

    static long mix(long a, long b, long salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L ^ 0x5645524445L;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
        return h;
    }

    /** Desenha todas as árvores que tocam a janela [minX..maxX] x [minZ..maxZ], recortando o que cai fora dela. */
    public static void render(VerdenTerrain t, int minX, int minZ, int maxX, int maxZ, Sink out) {
        Column c = new Column();
        int cx0 = Math.floorDiv(minX - REACH - CELL, CELL), cx1 = Math.floorDiv(maxX + REACH, CELL);
        int cz0 = Math.floorDiv(minZ - REACH - CELL, CELL), cz1 = Math.floorDiv(maxZ + REACH, CELL);
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            long h = mix(cx, cz, 1);
            int x = cx * CELL + (int) ((h >>> 8) & 3), z = cz * CELL + (int) ((h >>> 12) & 3);
            if (x < minX - REACH || x > maxX + REACH || z < minZ - REACH || z > maxZ + REACH) continue;
            t.sample(x, z, c);
            double d = density(c.biomeClass);
            if (d <= 0 || ((h >>> 20) & 0xFFFF) / 65536.0 >= d) continue;
            if (c.sea || c.river || c.lake || c.beach || c.hasWater() || c.flat > 0.02 || !"minecraft:grass_block".equals(c.topBlock)) continue;
            grow(kind(c.biomeClass), x, c.surface + 1, z, mix(cx, cz, 2), minX, minZ, maxX, maxZ, out);
        }
    }

    private static void put(Sink o, int x, int y, int z, String id, int minX, int minZ, int maxX, int maxZ) {
        if (x < minX || x > maxX || z < minZ || z > maxZ || y >= VerdenTerrain.MAX_Y) return;
        o.set(x, y, z, id);
    }

    private static void grow(Kind k, int x, int y, int z, long r, int minX, int minZ, int maxX, int maxZ, Sink o) {
        int rv = (int) ((r >>> 4) & 0xFF);
        String log, leaf; int h;
        switch (k) {
            case BIRCH: log = "minecraft:birch_log"; leaf = "minecraft:birch_leaves"; h = 5 + rv % 3; break;
            case DARK_OAK: log = "minecraft:dark_oak_log"; leaf = "minecraft:dark_oak_leaves"; h = 5 + rv % 3; break;
            case SPRUCE: log = "minecraft:spruce_log"; leaf = "minecraft:spruce_leaves"; h = 7 + rv % 5; break;
            case JUNGLE: log = "minecraft:jungle_log"; leaf = "minecraft:jungle_leaves"; h = 8 + rv % 7; break;
            default: log = "minecraft:oak_log"; leaf = "minecraft:oak_leaves"; h = 4 + rv % 3; break;
        }
        for (int dy = 0; dy < h; dy++) put(o, x, y + dy, z, log, minX, minZ, maxX, maxZ);
        if (k == Kind.SPRUCE) {
            // cone: raio 0 no topo, crescendo para baixo (1,2,1,2,3...)
            int[] rad = {0, 1, 1, 2, 2, 3, 2, 3, 3, 3, 3, 3};
            for (int i = 0; i <= h - 2; i++) {
                int rr = Math.min(rad[Math.min(i, rad.length - 1)], 3), ly = y + h - i;
                disc(o, x, ly, z, rr, false, leaf, minX, minZ, maxX, maxZ);
            }
            put(o, x, y + h, z, leaf, minX, minZ, maxX, maxZ);
        } else if (k == Kind.DARK_OAK) {
            disc(o, x, y + h - 3, z, 3, true, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h - 2, z, 3, true, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h - 1, z, 2, false, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h, z, 1, true, leaf, minX, minZ, maxX, maxZ);
        } else if (k == Kind.JUNGLE) {
            disc(o, x, y + h - 2, z, 3, true, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h - 1, z, 3, true, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h, z, 2, false, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h + 1, z, 1, true, leaf, minX, minZ, maxX, maxZ);
        } else { // carvalho / bétula
            disc(o, x, y + h - 2, z, 2, true, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h - 1, z, 2, true, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h, z, 1, false, leaf, minX, minZ, maxX, maxZ);
            disc(o, x, y + h + 1, z, 1, true, leaf, minX, minZ, maxX, maxZ);
        }
    }

    /** Quadrado de lado 2r+1; cut=true tira os cantos (copa arredondada). */
    private static void disc(Sink o, int x, int y, int z, int r, boolean cut, String id, int minX, int minZ, int maxX, int maxZ) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (cut && r > 0 && Math.abs(dx) == r && Math.abs(dz) == r) continue;
            put(o, x + dx, y, z + dz, id, minX, minZ, maxX, maxZ);
        }
    }
}
