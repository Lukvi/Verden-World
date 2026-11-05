package io.github.lukvi.verdenworld.core;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Raster de 1 canal (8 ou 16 bits) com amostragem bilinear / vizinho mais próximo. */
public final class Raster1 {
    public final int w, h;
    private final int[] v;

    private Raster1(int w, int h, int[] v) { this.w = w; this.h = h; this.v = v; }

    public static Raster1 load(InputStream in) throws IOException {
        BufferedImage img = ImageIO.read(in);
        if (img == null) throw new IOException("PNG ilegível");
        Raster r = img.getRaster();
        int w = img.getWidth(), h = img.getHeight();
        int[] v = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) v[y * w + x] = r.getSample(x, y, 0);
        return new Raster1(w, h, v);
    }

    public int raw(int x, int y) {
        x = x < 0 ? 0 : (x >= w ? w - 1 : x);
        y = y < 0 ? 0 : (y >= h ? h - 1 : y);
        return v[y * w + x];
    }

    /** Bilinear em coordenadas de pixel (centro do pixel i = i + 0.5). */
    public double bilinear(double px, double py) {
        double fx = px - 0.5, fy = py - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double a = raw(x0, y0) * (1 - tx) + raw(x0 + 1, y0) * tx;
        double b = raw(x0, y0 + 1) * (1 - tx) + raw(x0 + 1, y0 + 1) * tx;
        return a * (1 - ty) + b * ty;
    }

    public int nearest(double px, double py) { return raw((int) Math.floor(px), (int) Math.floor(py)); }
}
