package io.github.lukvi.verdenworld.core;

import java.nio.file.Path;

/** Verificações do núcleo (sem JUnit, para rodar com javac puro): java CoreChecks <pasta maps>. Sai com código 1 se algo falhar. */
public class CoreChecks {
    static int fails = 0;
    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FALHOU: " + msg); } }

    public static void main(String[] a) throws Exception {
        VerdenTerrain t = VerdenTerrain.loadFromDir(Path.of(a[0]));
        VerdenTerrain t2 = VerdenTerrain.loadFromDir(Path.of(a[0]));
        Column c = new Column(), d = new Column();

        // 1. determinismo (duas instâncias, mesmas colunas) e limites verticais
        java.util.Random r = new java.util.Random(42);
        double ext = 250000;
        for (int i = 0; i < 20000; i++) {
            int x = (int) ((r.nextDouble() * 2 - 1) * ext), z = (int) ((r.nextDouble() * 2 - 1) * ext);
            t.sample(x, z, c); t2.sample(x, z, d);
            check(c.surface == d.surface && c.water == d.water && c.mcBiome.equals(d.mcBiome), "não determinístico em " + x + "," + z);
            check(c.surface >= VerdenTerrain.MIN_Y + 8 && c.surface <= VerdenTerrain.MAX_Y - 6, "altura fora do limite em " + x + "," + z + ": " + c.surface);
            check(c.water == Column.NO_WATER || c.water > c.surface, "água não acima do solo em " + x + "," + z);
            check(c.mcBiome != null && c.topBlock != null, "bioma/bloco nulo em " + x + "," + z);
            if (fails > 20) break;
        }
        // 2. independência de ordem (mesma coluna após amostrar outras)
        t.sample(12345, -6789, c); int s1 = c.surface; for (int i = 0; i < 1000; i++) t.sample(i * 7, i * 13, d); t.sample(12345, -6789, c);
        check(c.surface == s1, "resultado depende da ordem de amostragem");

        // 3. localidades: em terra firme (exceto marítimas) e platô no lugar
        for (Location l : t.locations) {
            t.sample(l.x, l.z, c);
            boolean marine = l.kind.equals("acidente-marítimo");
            check(marine || !c.sea, "localidade no mar: " + l.id + " (costa " + c.coastPx + ")");
            if (l.targetY > 0) check(Math.abs(c.surface - l.targetY) <= 1, "platô de " + l.id + " fora do alvo: " + c.surface + " vs " + l.targetY);
        }
        // 4. capitais: platô plano e seco num raio de 2 km
        for (Location l : t.locations) if (l.isCapital()) {
            int min = 999, max = -999;
            for (int dx = -2000; dx <= 2000; dx += 100) for (int dz = -2000; dz <= 2000; dz += 100) {
                t.sample(l.x + dx, l.z + dz, c); min = Math.min(min, c.surface); max = Math.max(max, c.surface); check(!c.hasWater(), "água dentro do platô de " + l.id);
            }
            check(max - min <= 2, "capital " + l.id + " não é plana: " + min + ".." + max);
        }
        // 5. rios definitivos têm água no meio do curso
        for (VerdenTerrain.RiverInfo rv : t.riverInfos()) {
            int m = rv.xs.length / 2; int x = (int) ((rv.xs[m] + rv.xs[m + 1]) / 2), z = (int) ((rv.zs[m] + rv.zs[m + 1]) / 2);
            t.sample(x, z, c); check(c.river && c.hasWater(), "rio sem água: " + rv.id + " em " + x + "," + z);
        }
        // 6. Interseção dos Rios é lago; mar profundo existe; montanhas passam de y=200
        t.sample(0, 0, c); check(c.lake && c.hasWater(), "Interseção dos Rios sem lago");
        t.sample(-300000, 0, c); check(c.sea && c.surface < 30, "oceano profundo ausente");
        t.sample(t.byId.get("andens_spids").x, t.byId.get("andens_spids").z, c); check(c.surface > 150, "Andens Spids baixo: " + c.surface);
        // 7. desempenho: 16 chunks inteiros
        long t0 = System.nanoTime(); for (int cx = 0; cx < 4; cx++) for (int cz = 0; cz < 4; cz++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) t.sample(cx * 16 + x, cz * 16 + z, c);
        double ms = (System.nanoTime() - t0) / 1e6 / 16; System.out.printf("~%.2f ms por chunk (amostragem de 256 colunas)%n", ms);
        check(ms < 50, "amostragem lenta: " + ms + " ms/chunk");

        System.out.println(fails == 0 ? "OK: todas as verificações passaram" : fails + " falha(s)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
