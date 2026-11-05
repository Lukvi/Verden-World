import io.github.lukvi.verdenworld.core.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Renderiza o terreno exatamente como o gerador o calcula (mesma classe VerdenTerrain). Uso: Preview <maps_dir> <out_dir> [diag] */
public class Preview {
    static final Map<String, Integer> PAL = new HashMap<>();
    static void p(String k, int rgb) { PAL.put("minecraft:" + k, rgb); }
    static {
        p("ocean", 0x2d6a9f); p("deep_ocean", 0x1b4373); p("frozen_ocean", 0x9fc7e0); p("deep_frozen_ocean", 0x7fa9c8);
        p("lukewarm_ocean", 0x2f8fb0); p("deep_lukewarm_ocean", 0x216f93); p("river", 0x3b86c4); p("frozen_river", 0xb8dcee);
        p("beach", 0xe8d9a0); p("snowy_beach", 0xe6eef2); p("plains", 0x7dbb5a); p("meadow", 0x86d46a); p("forest", 0x3f8f3a);
        p("dark_forest", 0x3b2a4a); p("old_growth_birch_forest", 0x5fb878); p("taiga", 0x4c8a63); p("birch_forest", 0x9ccf6b);
        p("swamp", 0x4d6b4a); p("mangrove_swamp", 0x3b5a3a); p("desert", 0xe6c76d); p("badlands", 0xc0603a);
        p("snowy_plains", 0xf2f6f8); p("snowy_taiga", 0xc9d9d2); p("snowy_slopes", 0xe9eff2); p("stony_peaks", 0x8d8a85);
        p("frozen_peaks", 0xdfe7ee); p("jagged_peaks", 0xf7fbff); p("jungle", 0x1f7a2b); p("sparse_jungle", 0x3fa044); p("ice_spikes", 0xcfe9ff);
    }
    static int color(Column c) {
        Integer v = PAL.get(c.mcBiome); int rgb = v == null ? 0xff00ff : v;
        if (c.hasWater() && !c.sea) rgb = (c.mcBiome.contains("frozen")) ? 0xaad4ea : 0x3b86c4;
        return rgb;
    }
    static void render(VerdenTerrain t, String out, double cx, double cz, double halfW, double halfH, int pxW, int pxH, boolean marks) throws Exception {
        double sx = 2 * halfW / pxW, sz = 2 * halfH / pxH;
        int[] hgt = new int[pxW * pxH]; int[] col = new int[pxW * pxH]; Column c = new Column();
        for (int j = 0; j < pxH; j++) for (int i = 0; i < pxW; i++) {
            int x = (int) (cx - halfW + (i + 0.5) * sx), z = (int) (cz - halfH + (j + 0.5) * sz);
            t.sample(x, z, c);
            hgt[j * pxW + i] = c.hasWater() ? c.water : c.surface; col[j * pxW + i] = color(c);
            if (c.sea) { int d = VerdenTerrain.SEA - c.surface; double k = Math.max(0.55, 1 - d / 90.0); int r = (int) (((col[j*pxW+i] >> 16) & 255) * k), g = (int) (((col[j*pxW+i] >> 8) & 255) * k), b = (int) ((col[j*pxW+i] & 255) * k); col[j*pxW+i] = (r << 16) | (g << 8) | b; }
        }
        BufferedImage img = new BufferedImage(pxW, pxH, BufferedImage.TYPE_INT_RGB);
        double vexag = Math.max(0.25, 5.0 / Math.max(sx, 1));       // sombreado legível em qualquer zoom
        for (int j = 0; j < pxH; j++) for (int i = 0; i < pxW; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(pxW - 1, i + 1), j0 = Math.max(0, j - 1), j1 = Math.min(pxH - 1, j + 1);
            double dzx = (hgt[j * pxW + i1] - hgt[j * pxW + i0]) / (2 * sx) * 40 * vexag, dzy = (hgt[j1 * pxW + i] - hgt[j0 * pxW + i]) / (2 * sz) * 40 * vexag;
            double shade = 0.9 + Math.max(-0.45, Math.min(0.45, (-dzx - dzy) * 0.5 * 0.7071));
            int rgb = col[j * pxW + i]; int r = (int) Math.min(255, ((rgb >> 16) & 255) * shade), g = (int) Math.min(255, ((rgb >> 8) & 255) * shade), b = (int) Math.min(255, (rgb & 255) * shade);
            img.setRGB(i, j, (r << 16) | (g << 8) | b);
        }
        Graphics2D g2 = img.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (marks) {
            int fs = Math.max(11, pxW / 150); g2.setFont(new Font("SansSerif", Font.BOLD, fs));
            for (Location l : t.locations) {
                if (l.x < cx - halfW || l.x > cx + halfW || l.z < cz - halfH || l.z > cz + halfH) continue;
                if (l.kind.equals("floresta") || l.kind.equals("campos") || l.kind.equals("pântano") || l.kind.equals("caverna") || l.kind.equals("lago") || l.kind.startsWith("lago")) continue;
                int px = (int) ((l.x - (cx - halfW)) / sx), py = (int) ((l.z - (cz - halfH)) / sz);
                int r = l.isCapital() ? fs / 2 + 3 : fs / 3 + 1;
                g2.setColor(l.pending ? Color.ORANGE : (l.isCapital() ? Color.RED : Color.WHITE)); g2.fillOval(px - r, py - r, 2 * r, 2 * r);
                g2.setColor(Color.BLACK); g2.drawOval(px - r, py - r, 2 * r, 2 * r);
                String s = l.name; g2.setColor(Color.BLACK); g2.drawString(s, px + r + 3, py + fs / 3 + 1); g2.drawString(s, px + r + 1, py + fs / 3 + 1);
                g2.setColor(Color.WHITE); g2.drawString(s, px + r + 2, py + fs / 3);
            }
        }
        for (VerdenTerrain.RiverInfo r : t.riverInfos()) {
            g2.setColor(r.pending ? new Color(255, 150, 0, 230) : new Color(40, 120, 255, 235));
            g2.setStroke(new BasicStroke((float) Math.max(2.0, r.width / sx * 1.6), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i + 1 < r.xs.length; i++) g2.drawLine((int) ((r.xs[i] - (cx - halfW)) / sx), (int) ((r.zs[i] - (cz - halfH)) / sz), (int) ((r.xs[i+1] - (cx - halfW)) / sx), (int) ((r.zs[i+1] - (cz - halfH)) / sz));
        }
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 255, 255, 230)); g2.fillRect(14, pxH - 44, 260, 30);
        double km = pxW * sx / 1000.0 / 5; double nice = Math.pow(10, Math.floor(Math.log10(km))); km = Math.max(1, Math.round(km / nice) * nice);
        int barPx = (int) (km * 1000 / sx); g2.setColor(Color.BLACK); g2.fillRect(20, pxH - 30, barPx, 6);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13)); g2.drawString((int) km + " km = " + (int) km * 1000 + " blocos", 24 + barPx, pxH - 24);
        g2.dispose();
        ImageIO.write(img, "png", new File(out));
        System.out.println("ok " + out);
    }

    public static void main(String[] a) throws Exception {
        VerdenTerrain t = VerdenTerrain.loadFromDir(Path.of(a[0])); String out = a[1]; new File(out).mkdirs();
        if (a.length > 2 && a[2].equals("diag")) {
            Column c = new Column();
            System.out.printf("%-20s %9s %9s %5s %5s %-30s %s%n", "id", "x", "z", "y", "agua", "bioma", "costa_px/flat");
            for (Location l : t.locations) { t.sample(l.x, l.z, c);
                System.out.printf("%-20s %9d %9d %5d %5s %-30s %.1f/%.2f%s%n", l.id, l.x, l.z, c.surface, c.hasWater() ? c.water : "-", c.mcBiome, c.coastPx, c.flat, (c.sea && !l.kind.contains("ilha") && !l.kind.equals("acidente-marítimo")) ? "   <-- NO MAR?" : ""); }
            return;
        }
        double x0 = -(t.ox) * t.bpp, z0 = -(t.oy) * t.bpp, W = t.width * t.bpp, H = t.height * t.bpp;
        render(t, out + "/01_continente_completo.png", x0 + W / 2, z0 + H / 2, W / 2, H / 2, 2184, 1638, true);
        String[][] zoom = {{"jord", "02_jord", "14000"}, {"flerin", "03_flerin_yettus", "26000"}, {"veroogh", "04_veroogh_koold", "30000"},
                {"nagdaar", "05_nagdaar", "20000"}, {"intersecao_rios", "06_intersecao_dos_rios", "18000"}, {"karamarraw", "07_karamarraw", "60000"}, {"maariz", "08_maariz", "16000"}};
        for (String[] z : zoom) { Location l = t.byId.get(z[0]); double h = Double.parseDouble(z[2]);
            render(t, out + "/" + z[1] + ".png", l.x, l.z, h, h * 0.75, 1600, 1200, true); }
    }
}
