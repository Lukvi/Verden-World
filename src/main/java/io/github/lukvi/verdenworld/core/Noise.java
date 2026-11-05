package io.github.lukvi.verdenworld.core;

import java.util.Random;

/** Ruído simplex 2D determinístico (permutação fixa: o mundo não depende da seed do save). */
public final class Noise {
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0), G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final int[][] GRAD = {{1,1},{-1,1},{1,-1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}};
    private final int[] perm = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    /** Simplex 2D, resultado aproximadamente em [-1, 1]. */
    public double noise(double x, double y) {
        double s = (x + y) * F2;
        int i = (int) Math.floor(x + s), j = (int) Math.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t), y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0, j1 = x0 > y0 ? 0 : 1;
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2, x2 = x0 - 1 + 2 * G2, y2 = y0 - 1 + 2 * G2;
        int ii = i & 255, jj = j & 255;
        return 70.0 * (corner(x0, y0, perm[ii + perm[jj]]) + corner(x1, y1, perm[ii + i1 + perm[jj + j1]])
                + corner(x2, y2, perm[ii + 1 + perm[jj + 1]]));
    }

    private static double corner(double x, double y, int h) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) return 0;
        int[] g = GRAD[h & 7];
        t *= t;
        return t * t * (g[0] * x + g[1] * y);
    }

    /** fBm normalizado para ~[-1, 1]. */
    public double fbm(double x, double y, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) { sum += amp * noise(x * f, y * f); norm += amp; amp *= 0.5; f *= 2.0; }
        return sum / norm;
    }

    /** Ruído em cristas, ~[0, 1] (1 = crista). */
    public double ridged(double x, double y, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1, w = 1;
        for (int o = 0; o < octaves; o++) {
            double n = 1.0 - Math.abs(noise(x * f, y * f));
            n *= n; n *= w; w = Math.min(1.0, Math.max(0.0, n * 2.0));
            sum += amp * n; norm += amp; amp *= 0.5; f *= 2.0;
        }
        return sum / norm;
    }
}
