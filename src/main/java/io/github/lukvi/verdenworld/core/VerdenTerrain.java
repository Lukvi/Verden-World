package io.github.lukvi.verdenworld.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Terreno determinístico do continente de Verden. Sem dependências do Minecraft.
 * Mesma entrada (rasters em data/verden/maps) => mesmo mundo, sempre.
 * Unidades: 1 bloco = 1 m; X leste, Z sul; origem = Interseção dos Rios.
 */
public final class VerdenTerrain {
    public static final int SEA = 63, MIN_Y = -64, MAX_Y = 319;
    // ids de bioma (data/biomes.csv)
    public static final int OCEAN = 0, PLAINS = 1, MEADOW = 2, FOREST_BROTTUS = 3, FOREST_MORKE = 4, FOREST_GRON = 5,
            FOREST_KARTOFFEL = 6, FOREST_VERLAAF = 7, SWAMP = 8, MANGROVE = 9, DESERT = 10, BADLANDS = 11,
            SNOW_PLAINS = 12, SNOW_TAIGA = 13, MOUNTAIN = 14, JUNGLE = 15, SPARSE_JUNGLE = 16, TAIGA = 17, ICE_SPIKES = 18;

    private static final String BEDROCK = "minecraft:bedrock", DEEPSLATE = "minecraft:deepslate", STONE = "minecraft:stone";

    public final double bpp, ox, oy;
    public final int width, height;
    private final Raster1 sdf, hgt, rgh, bio, cld;
    private final Noise nz = new Noise(0x5645524445L);
    private final String[] mcBiome, topBlock, fillerBlock;
    public final List<Location> locations = new ArrayList<>();
    public final Map<String, Location> byId = new HashMap<>();
    private final List<River> rivers = new ArrayList<>();
    private final List<Lake> lakes = new ArrayList<>();
    private final boolean includePending;

    // ------------------------------------------------------------------ carregamento
    public static VerdenTerrain loadFromClasspath() throws IOException {
        return load(n -> VerdenTerrain.class.getResourceAsStream("/verden_world/maps/" + n));
    }
    public static VerdenTerrain loadFromDir(Path dir) throws IOException {
        return load(n -> { try { return Files.newInputStream(dir.resolve(n)); } catch (IOException e) { return null; } });
    }
    public static VerdenTerrain load(Function<String, InputStream> open) throws IOException {
        return new VerdenTerrain(open, !"false".equals(System.getProperty("verden.pendingRivers", "true")));
    }

    private static List<String> lines(Function<String, InputStream> open, String name) throws IOException {
        try (InputStream in = open.apply(name)) {
            if (in == null) throw new IOException("recurso ausente: " + name);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> out = new ArrayList<>(); String l;
            while ((l = r.readLine()) != null) { l = l.trim(); if (!l.isEmpty() && !l.startsWith("#")) out.add(l); }
            return out;
        }
    }
    private static Raster1 raster(Function<String, InputStream> open, String name) throws IOException {
        try (InputStream in = open.apply(name)) {
            if (in == null) throw new IOException("recurso ausente: " + name);
            return Raster1.load(in);
        }
    }

    private VerdenTerrain(Function<String, InputStream> open, boolean includePending) throws IOException {
        this.includePending = includePending;
        Map<String, String> meta = new HashMap<>();
        for (String l : lines(open, "meta.txt")) { String[] kv = l.split("=", 2); meta.put(kv[0], kv[1]); }
        bpp = Double.parseDouble(meta.get("blocks_per_px"));
        ox = Double.parseDouble(meta.get("origin_px_x")); oy = Double.parseDouble(meta.get("origin_px_y"));
        sdf = raster(open, "sdf.png"); hgt = raster(open, "height.png"); rgh = raster(open, "rough.png");
        bio = raster(open, "biome.png"); cld = raster(open, "cold.png");
        width = sdf.w; height = sdf.h;
        int n = 32; mcBiome = new String[n]; topBlock = new String[n]; fillerBlock = new String[n];
        for (String l : lines(open, "biomes.csv")) {
            String[] p = l.split(",");
            if (p[0].equals("id")) continue;
            int id = Integer.parseInt(p[0]); mcBiome[id] = p[2]; topBlock[id] = p[3]; fillerBlock[id] = p[4];
        }
        for (String l : lines(open, "locations.txt")) {
            String[] p = l.split("\\|");
            Location loc = new Location(p[0], p[1], p[2], p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    Integer.parseInt(p[6]), Integer.parseInt(p[7]), p[8], p[9].equals("1"));
            locations.add(loc); byId.put(loc.id, loc);
        }
        for (String l : lines(open, "rivers.txt")) rivers.add(new River(l.split("\\|")));
        for (String l : lines(open, "lakes.txt")) { String[] p = l.split("\\|"); lakes.add(new Lake(p)); }
        for (River r : rivers) r.init(this);
        for (Location loc : locations) {   // platôs só onde o ponto está em terra firme
            if (loc.flatRadius <= 0) continue;
            double px = ox + loc.x / bpp, py = oy + loc.z / bpp;
            double t = (sdf.bilinear(px, py) - 32768) / 512.0;
            if (t < 1.0) continue;
            loc.targetY = (int) Math.max(66, Math.round(baseHeight(px, py)));
        }
    }

    // ------------------------------------------------------------------ amostragem
    double baseHeight(double px, double py) { return hgt.bilinear(px, py) / 128.0 - 64.0; }
    private static double smooth(double t) { t = t < 0 ? 0 : (t > 1 ? 1 : t); return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public Column sample(int x, int z) { Column c = new Column(); sample(x, z, c); return c; }
    public int surfaceY(int x, int z) { return sample(x, z).surface; }

    public void sample(int x, int z, Column c) {
        // deformação de domínio: costa e regiões ficam orgânicas, sem mudar a geografia (< 0,8 px = 320 m)
        double wx = 0.55 * nz.fbm(x / 1700.0 + 11.3, z / 1700.0 - 4.1, 3) + 0.14 * nz.noise(x / 330.0, z / 330.0 + 7.7);
        double wy = 0.55 * nz.fbm(x / 1700.0 - 9.2, z / 1700.0 + 2.8, 3) + 0.14 * nz.noise(x / 330.0 + 3.1, z / 330.0);
        double px = ox + x / bpp + wx, py = oy + z / bpp + wy;
        boolean inside = px >= 1 && py >= 1 && px < width - 1 && py < height - 1;
        double t = inside ? (sdf.bilinear(px, py) - 32768) / 512.0 : -63.0;
        double base = inside ? baseHeight(px, py) : 19.0;
        double rough = inside ? rgh.bilinear(px, py) / 2.0 : 4.0;
        int cls = inside ? bio.nearest(px, py) : 0;
        if (cls == 0 && t > 0 && inside) {            // vizinho de terra mais próximo (diferença de interpolação na costa)
            int[] d = {1, 0, -1, 0, 0, 1, 0, -1};
            cls = PLAINS;
            for (int i = 0; i < 8; i += 2) { int k = bio.raw((int) px + d[i], (int) py + d[i + 1]); if (k != 0) { cls = k; break; } }
        }
        double cold = inside ? cld.bilinear(px, py) / 255.0 : 0;
        c.coastPx = t; c.sea = t <= 0; c.river = false; c.lake = false; c.beach = false; c.flat = 0; c.water = Column.NO_WATER;

        // --- relevo
        double n = nz.fbm(x / 900.0, z / 900.0, 5);
        double r = nz.ridged(x / 1400.0 + 50, z / 1400.0 - 30, 5);
        double wr = smooth((rough - 35) / 50.0);
        double d = rough * ((1 - wr) * 0.5 * n + wr * (r * 1.15 - 0.3)) + 0.25 * rough * nz.fbm(x / 220.0, z / 220.0, 3)
                + 1.3 * nz.fbm(x / 24.0, z / 24.0, 2);
        if (cls == DESERT) d += 5.0 * Math.pow(0.5 + 0.5 * Math.sin((x * 0.8 + z * 0.6) / 46.0 + 3.0 * nz.noise(x / 700.0, z / 700.0)), 2);
        double h = t > 0 ? base + d * smooth(t / 4.0) + 7.0 * nz.fbm(x / 15000.0 + 3, z / 15000.0 - 8, 3) * smooth(t / 6.0) * (1 - wr) : base + d * 0.25;

        // --- platôs das localidades
        if (t > 0) for (Location loc : locations) {
            if (loc.targetY == 0) continue;
            double dx = x - loc.x, dz = z - loc.z, reach = loc.flatRadius + loc.flatBlend;
            if (Math.abs(dx) > reach || Math.abs(dz) > reach) continue;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist >= reach) continue;
            double w = 1 - smooth((dist - loc.flatRadius) / Math.max(1, loc.flatBlend));
            h = lerp(h, loc.targetY + 0.3 * nz.fbm(x / 40.0, z / 40.0, 2), w);
            if (w > c.flat) c.flat = w;
        }

        // --- rios
        double bestScore = 1e18; River br = null; double bs = 0, bd = 0, bhRef = 0, bw2 = 0, bbank = 0;
        if (t > -2) for (River rv : rivers) {
            if (rv.pending && !includePending) continue;
            if (x < rv.minX || x > rv.maxX || z < rv.minZ || z > rv.maxZ) continue;
            rv.nearest(x, z);
            double wm = 1 + 0.25 * nz.noise(x / 500.0, z / 500.0);
            double w2 = 0.5 * rv.width * wm, bank = Math.max(70, rv.width * 1.1);
            if (rv.qd < w2 + bank) {
                double score = rv.qd / (w2 + bank);
                if (score < bestScore) { bestScore = score; br = rv; bs = rv.qs; bd = rv.qd; bw2 = w2; bbank = bank;
                    bhRef = baseHeight(ox + rv.qx / bpp, oy + rv.qz / bpp); }
            }
        }
        if (br != null) {
            double e = smooth(bs);
            double wl = Math.min(lerp(br.wl0, br.wl1, e), bhRef - 1.5);
            int WL = (int) Math.floor(Math.max(SEA, wl));
            if (bd < bw2) {
                double q = bd / bw2;
                h = Math.min(h, WL - 1 - Math.round(2.0 * (1 - q * q)));
                c.water = WL; c.river = true;
            } else {
                double u = smooth((bd - bw2) / bbank);
                double target = WL + 0.5 + (h - (WL + 0.5)) * u;
                h = Math.min(h, target);
                if (u < 0.35) h = Math.max(h, WL + 1);
            }
        }
        // --- lagos
        for (Lake lk : lakes) {
            double dx = (x - lk.x) / lk.rx, dz = (z - lk.z) / lk.rz;
            if (Math.abs(dx) > 1.3 || Math.abs(dz) > 1.3) continue;
            double q = Math.sqrt(dx * dx + dz * dz) * (1 + 0.10 * nz.fbm(x / 2600.0 + lk.x * 0.001, z / 2600.0, 2));
            if (q < 1) {
                h = Math.min(h, lk.level - 1 - Math.floor(7 * Math.pow(1 - q, 1.4)));
                c.water = Math.max(c.water == Column.NO_WATER ? lk.level : c.water, lk.level); c.lake = true;
            } else if (q < 1.22) {
                double u = smooth((q - 1) / 0.22);
                h = Math.max(Math.min(h, lk.level + 0.5 + (h - (lk.level + 0.5)) * u), lk.level + 1);
            }
        }
        // --- poças de pântano
        if (t > 0 && (cls == SWAMP || cls == MANGROVE)) {
            double p = nz.fbm(x / 110.0, z / 110.0, 3);
            if (p > 0.18) { h = Math.min(h, SEA - 1 - Math.floor((p - 0.18) * 6)); c.water = Math.max(c.water == Column.NO_WATER ? SEA : c.water, SEA); }
        }
        int surface = (int) Math.round(h);
        surface = Math.max(MIN_Y + 8, Math.min(MAX_Y - 6, surface));
        if (surface < SEA && c.water == Column.NO_WATER) c.water = SEA;
        if (c.water != Column.NO_WATER && c.water <= surface) c.water = Column.NO_WATER;
        c.surface = surface; c.biomeClass = (t <= 0) ? OCEAN : cls;

        // --- bioma vanilla e blocos de superfície
        resolve(c, x, z, cold, cls, t, py);
    }

    private void resolve(Column c, int x, int z, double cold, int cls, double t, double py) {
        int s = c.surface;
        if (c.sea) {
            int depth = SEA - s; boolean frozen = cold > 0.5;
            if (frozen) c.mcBiome = depth >= 30 ? "minecraft:deep_frozen_ocean" : "minecraft:frozen_ocean";
            else c.mcBiome = depth >= 30 ? "minecraft:deep_ocean" : "minecraft:ocean";
            c.topBlock = depth < 22 ? "minecraft:sand" : ((nz.noise(x / 60.0, z / 60.0) > 0.2) ? "minecraft:clay" : "minecraft:gravel");
            c.fillerBlock = depth < 22 ? "minecraft:sand" : "minecraft:gravel"; c.fillerDepth = 3; return;
        }
        if (c.river && c.water != Column.NO_WATER && s < c.water) {
            c.mcBiome = cold > 0.5 ? "minecraft:frozen_river" : "minecraft:river";
            c.topBlock = nz.noise(x / 45.0, z / 45.0) > 0.1 ? "minecraft:gravel" : "minecraft:sand"; c.fillerBlock = "minecraft:sand"; c.fillerDepth = 3; return;
        }
        c.mcBiome = mcBiome[cls]; c.topBlock = topBlock[cls]; c.fillerBlock = fillerBlock[cls]; c.fillerDepth = 4;
        if (cls == MOUNTAIN) {
            c.mcBiome = s >= 230 ? "minecraft:jagged_peaks" : s >= 185 ? "minecraft:frozen_peaks" : s >= 140 ? "minecraft:snowy_slopes" : "minecraft:stony_peaks";
            boolean snow = s >= 205 || (s >= 160 && nz.noise(x / 17.0, z / 17.0) > 0.0);
            c.topBlock = snow ? "minecraft:snow_block" : STONE; c.fillerBlock = snow ? "minecraft:snow_block" : STONE; c.fillerDepth = 2;
        }
        boolean wet = c.lake || c.water != Column.NO_WATER;
        if (!wet && t < 2.0 && s <= SEA + 4 && cls != SWAMP && cls != MANGROVE && cls != DESERT && cls != BADLANDS && cls != MOUNTAIN) {
            c.beach = true; c.mcBiome = cold > 0.5 ? "minecraft:snowy_beach" : "minecraft:beach";
            c.topBlock = "minecraft:sand"; c.fillerBlock = "minecraft:sand"; c.fillerDepth = 3;
        }
        if (wet && !c.river) { c.topBlock = "minecraft:sand"; c.fillerBlock = "minecraft:sand"; c.fillerDepth = 3; }
        if (c.flat > 0.6 && (cls == DESERT)) { /* cidades do deserto mantêm areia compactada: sem mudança no v0.1 */ }
    }

    /** Bloco (id) em (x,y,z) dado a coluna amostrada. "minecraft:air" acima da superfície/água. */
    public String blockAt(Column c, int x, int y, int z) {
        if (y <= MIN_Y) return BEDROCK;
        if (y < MIN_Y + 5 && hash(x, y, z) % 5 >= (y - MIN_Y)) return BEDROCK;
        if (y > c.surface) return (c.water != Column.NO_WATER && y <= c.water) ? "minecraft:water" : "minecraft:air";
        int depth = c.surface - y;
        if (depth == 0) return c.topBlock;
        if (depth <= c.fillerDepth) {
            if (c.biomeClass == BADLANDS || "minecraft:terracotta".equals(c.fillerBlock)) return stripe(y);
            return c.fillerBlock;
        }
        if (y < 0 && (y < -8 || hash(x, y, z) % 9 < -y)) return DEEPSLATE;
        return STONE;
    }
    private static final String[] STRIPES = {"minecraft:terracotta", "minecraft:orange_terracotta", "minecraft:yellow_terracotta",
            "minecraft:terracotta", "minecraft:white_terracotta", "minecraft:brown_terracotta", "minecraft:red_terracotta"};
    private static String stripe(int y) { return STRIPES[((y / 2) % 7 + 7) % 7]; }
    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177; h = (h ^ (h >>> 13)) * 1274126177; return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    /** Dados de um rio para ferramentas/visualização. */
    public static final class RiverInfo {
        public final String id, name, status; public final double width; public final double[] xs, zs; public final boolean pending;
        RiverInfo(River r) { id = r.id; name = r.name; status = r.status; width = r.width; xs = r.xs; zs = r.zs; pending = r.pending; }
    }
    public List<RiverInfo> riverInfos() { List<RiverInfo> o = new ArrayList<>(); for (River r : rivers) o.add(new RiverInfo(r)); return o; }

    /** Todos os ids de bioma vanilla que este terreno pode devolver (o BiomeSource precisa registrá-los de antemão). */
    public java.util.Set<String> allBiomeIds() {
        java.util.Set<String> o = new java.util.TreeSet<>();
        for (String b : mcBiome) if (b != null) o.add(b);
        java.util.Collections.addAll(o, "minecraft:ocean", "minecraft:deep_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:river", "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:jagged_peaks",
                "minecraft:frozen_peaks", "minecraft:snowy_slopes", "minecraft:stony_peaks");
        return o;
    }

    // ------------------------------------------------------------------ consultas auxiliares
    public Location nearestLocation(int x, int z) {
        Location best = null; double bd = Double.MAX_VALUE;
        for (Location l : locations) { double dx = l.x - x, dz = l.z - z, d = dx * dx + dz * dz; if (d < bd) { bd = d; best = l; } }
        return best;
    }
    public String biomeName(int cls) { return mcBiome[cls]; }
    public double pixelX(int x) { return ox + x / bpp; }
    public double pixelY(int z) { return oy + z / bpp; }

    // ------------------------------------------------------------------ rios e lagos
    private static final class River {
        final String id, name, status; final double width; final boolean pending;
        final double[] xs, zs, cum; final double total; double minX = 1e18, maxX = -1e18, minZ = 1e18, maxZ = -1e18;
        double wl0, wl1;
        double qd, qs, qx, qz;   // resultado de nearest()
        River(String[] p) {
            id = p[0]; name = p[1]; status = p[2]; width = Double.parseDouble(p[3]); pending = status.equals("PENDENTE");
            String[] pts = p[4].split(";"); xs = new double[pts.length]; zs = new double[pts.length]; cum = new double[pts.length];
            for (int i = 0; i < pts.length; i++) { String[] xy = pts[i].split(","); xs[i] = Double.parseDouble(xy[0]); zs[i] = Double.parseDouble(xy[1]); }
            for (int i = 1; i < xs.length; i++) cum[i] = cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            total = cum[cum.length - 1];
            double m = width * 1.1 + 200 + width;
            for (int i = 0; i < xs.length; i++) { minX = Math.min(minX, xs[i] - m); maxX = Math.max(maxX, xs[i] + m); minZ = Math.min(minZ, zs[i] - m); maxZ = Math.max(maxZ, zs[i] + m); }
        }
        void init(VerdenTerrain t) {
            double hs = t.baseHeight(t.ox + xs[0] / t.bpp, t.oy + zs[0] / t.bpp);
            double he = t.baseHeight(t.ox + xs[xs.length - 1] / t.bpp, t.oy + zs[zs.length - 1] / t.bpp);
            wl1 = Math.max(SEA, he - 1.5); wl0 = Math.max(wl1, hs - 2.0);
        }
        void nearest(double x, double z) {
            qd = 1e18;
            for (int i = 0; i + 1 < xs.length; i++) {
                double ax = xs[i], az = zs[i], bx = xs[i + 1] - ax, bz = zs[i + 1] - az;
                double L2 = bx * bx + bz * bz, u = L2 == 0 ? 0 : ((x - ax) * bx + (z - az) * bz) / L2;
                u = u < 0 ? 0 : (u > 1 ? 1 : u);
                double cx = ax + u * bx, cz = az + u * bz, d = Math.hypot(x - cx, z - cz);
                if (d < qd) { qd = d; qx = cx; qz = cz; qs = (cum[i] + u * Math.sqrt(L2)) / total; }
            }
        }
    }
    private static final class Lake {
        final String id; final double x, z, rx, rz; final int level;
        Lake(String[] p) { id = p[0]; x = Double.parseDouble(p[1]); z = Double.parseDouble(p[2]); rx = Double.parseDouble(p[3]); rz = Double.parseDouble(p[4]); level = Integer.parseInt(p[5]); }
    }
}
