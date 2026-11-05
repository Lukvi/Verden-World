#!/usr/bin/env python3
"""Verden-World - gera os rasters e tabelas que o gerador de terreno lê.
Entradas : reference/mapa_colorido.jpg, data/locations.json, data/regions.json, data/biomes.csv
Saídas   : src/main/resources/verden_world/maps/{sdf,biome,height,rough}.png + *.txt
Determinístico: mesma entrada => mesma saída.   Uso: python3 -I tools/build_rasters.py
"""
import json, os, csv, numpy as np, cv2
from scipy import ndimage as ndi
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/verden_world/maps"); os.makedirs(OUT, exist_ok=True)
loc = json.load(open(os.path.join(ROOT, "data/locations.json"), encoding="utf-8"))
reg = json.load(open(os.path.join(ROOT, "data/regions.json"), encoding="utf-8"))
META = loc["meta"]; BPP = META["scale_blocks_per_px"]; OX, OY = META["origin_px"]
BIO = {r["name"]: int(r["id"]) for r in csv.DictReader(open(os.path.join(ROOT, "data/biomes.csv"), encoding="utf-8"))}
im = cv2.imread(os.path.join(ROOT, "reference/mapa_colorido.jpg")); Hh, Ww = im.shape[:2]
hsv = cv2.cvtColor(im, cv2.COLOR_BGR2HSV); Hc, Sc, Vc = [hsv[..., i].astype(int) for i in range(3)]
ss = lambda x: (lambda t: t*t*(3-2*t))(np.clip(x, 0, 1))
def poly_mask(poly):
    m = np.zeros((Hh, Ww), np.uint8); cv2.fillPoly(m, [np.array(poly, np.int32)], 1); return m.astype(bool)
def blur(a, s): return cv2.GaussianBlur(a.astype(np.float32), (0, 0), s)

# ---------------------------------------------------------------- 1. costa (mascara terra/mar)
water = (Hc >= 55) & (Hc <= 115) & (Sc > 35)
lab, n = ndi.label(water); sz = ndi.sum(water, lab, range(1, n+1))
sea = np.isin(lab, [i+1 for i, s in enumerate(sz) if s > 6000])
land = ~sea
land[236:296, 515:742] = True                          # halo azul do rotulo 'Veroogh' lido como agua
land[380:520, 150:305] = False                          # navios/halo do rotulo 'Nagdaar' sobre o mar
land[778:846, 885:995] = False                           # dragao desenhado no mar
k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
land = cv2.morphologyEx(land.astype(np.uint8), cv2.MORPH_OPEN, k) > 0
# Costa sul de Karamarraw: o titulo do mapa a cobre -> INFERIDA: mantem a borda norte visivel e da a ilha espessura ~31 km
for x_ in range(285, 1100):
    col_ = land[835:935, x_]
    if not col_.any(): continue
    top_ = 835 + int(np.argmax(col_))
    th_ = int(78 + 10*np.sin(x_/70.0) + 5*np.sin(x_/23.0 + 2))
    land[835:1000, x_] = False; land[top_:min(top_+th_, 995), x_] = True
land = cv2.morphologyEx(land.astype(np.uint8), cv2.MORPH_OPEN, k) > 0
lab, n = ndi.label(land)
DROP = [(1172, 812), (1264, 943), (148, 450), (175, 432), (1295, 108), (1224, 531), (1016, 388), (62, 211), (154, 236), (1063, 983)]
# compass, 'Fim the World' (removida pelo Guia), monstros, navios, 'Vesten Hayvmand' (removida pelo Guia), titulo
for i in range(1, n+1):
    ys, xs = np.where(lab == i)
    if len(xs) < 400: land[lab == i] = False; continue
    cx, cy = xs.mean(), ys.mean()
    if any(abs(cx-a) < 30 and abs(cy-b) < 30 for a, b in DROP): land[lab == i] = False
land = ndi.binary_fill_holes(land)
land_u8 = land.astype(np.uint8)
d_in = cv2.distanceTransform(land_u8, cv2.DIST_L2, 5); d_out = cv2.distanceTransform(1-land_u8, cv2.DIST_L2, 5)
# fora da imagem = mar: distancia ate a borda nao importa (todas as bordas sao mar)
sdf = cv2.GaussianBlur(d_in - d_out, (0, 0), 1.3)   # px, + = terra adentro

# ---------------------------------------------------------------- 2. frio / deserto / montanhas
snow = (Sc < 70) & (Vc > 135) & land
snow[300:, :] = False
snow = cv2.morphologyEx(snow.astype(np.uint8), cv2.MORPH_OPEN, np.ones((5, 5), np.uint8)) > 0
cold = np.maximum(np.clip(blur(snow, 12)*1.5, 0, 1), 0.9*snow) * land
cold_hi = np.clip(blur(snow, 5)*1.2, 0, 1)
orange = (Hc >= 6) & (Hc <= 22) & (Sc > 140) & (Vc > 120)
orange = cv2.morphologyEx(orange.astype(np.uint8), cv2.MORPH_OPEN, np.ones((7, 7), np.uint8))
orange = cv2.morphologyEx(orange, cv2.MORPH_CLOSE, np.ones((71, 71), np.uint8)) > 0
orange = ndi.binary_fill_holes(orange)
zone = poly_mask([[735, 430], [1000, 425], [1115, 520], [1125, 700], [1000, 790], [740, 790]])
desert = orange & zone & land
desert = cv2.morphologyEx(desert.astype(np.uint8), cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (31, 31))) > 0
dmask = blur(desert, 6)

P = {p["id"]: p for p in reg["polygons"]}
def _noise(seed, cell=38):
    r = np.random.RandomState(seed); g = r.rand(Hh//cell+3, Ww//cell+3).astype(np.float32)
    n = cv2.resize(g, (Ww+2*cell, Hh+2*cell), interpolation=cv2.INTER_CUBIC)[:Hh, :Ww]
    g2 = r.rand(Hh//12+3, Ww//12+3).astype(np.float32)
    return 0.7*n + 0.3*cv2.resize(g2, (Ww+24, Hh+24), interpolation=cv2.INTER_CUBIC)[:Hh, :Ww]
def soft_poly(poly, seed):
    mk = blur(poly_mask(poly), 5)
    return (mk + 0.55*(_noise(seed) - 0.5)) > 0.5
mount = np.zeros((Hh, Ww), np.float32); high = np.zeros_like(mount); rough_poly = np.zeros_like(mount)
for p in reg["polygons"]:
    if p.get("mountain"): mount = np.maximum(mount, poly_mask(p["poly"]))
    if p.get("highland"): high = np.maximum(high, poly_mask(p["poly"]))
_b = np.clip(blur(mount, 7), 0, 1)
m = np.clip(1.3*_b + 0.5*(_noise(7) - 0.5)*4*_b*(1-_b), 0, 1) * (sdf > 0)
hl = np.clip(blur(high, 12)*1.2, 0, 1) * (sdf > 0)

# ---------------------------------------------------------------- 3. classes de bioma
bio = np.full((Hh, Ww), BIO["PLAINS"], np.uint8)
bio[cold > 0.2] = BIO["TAIGA"]; bio[cold > 0.45] = BIO["SNOW_TAIGA"]; bio[cold > 0.78] = BIO["SNOW_PLAINS"]
bio[desert] = BIO["DESERT"]
swamp = np.zeros_like(mount); rough = np.full((Hh, Ww), 10.0, np.float32)
rough[desert] = 14
for p in reg["polygons"]:
    b = p["biome"]; pm = soft_poly(p["poly"], 100 + sum(map(ord, p["id"]))) & land
    if p.get("highland"): continue
    if b == "MOUNTAIN": continue
    if b in ("SWAMP", "MANGROVE"):
        bio[pm & (cold < 0.6)] = BIO[b]; swamp = np.maximum(swamp, pm); continue
    if b in ("ICE_SPIKES",): bio[pm] = BIO[b]; continue
    sel = pm & (cold < 0.6)
    bio[sel] = BIO[b]
    if "rough" in p: rough[sel] = p["rough"]
# karamarraw: toda a ilha tropical
ys, xs = np.mgrid[0:Hh, 0:Ww]
kara = land & (ys > 835) & (xs > 270)
bio[kara & (bio == BIO["PLAINS"])] = BIO["SPARSE_JUNGLE"]
bio[kara & poly_mask(P["karamarraw_selva"]["poly"])] = BIO["JUNGLE"]
bio[m > 0.3] = BIO["MOUNTAIN"]
bio[~land] = 0

# ---------------------------------------------------------------- 4. altura base e rugosidade
t = sdf
land_base = 63 + 4*ss(t/3) + 8*ss((t-2)/8)
sea_floor = 63 - 4*ss(-t/3) - 40*ss((-t-2)/14)
h = np.where(t > 0, land_base, sea_floor).astype(np.float32)
up = np.where(t > 0, 1.0, 0.0)
h += up * (22*blur(cold, 3) + 4*dmask + 150*np.power(m, 1.1) + 62*hl + 14*blur(poly_mask(P["yettus_minas"]["poly"]), 5))
sw = np.clip(blur(swamp, 2.5), 0, 1) * up
h = h*(1-sw) + 63.6*sw
rough = cv2.GaussianBlur(rough, (0, 0), 2.0)
rough = rough + 110*m + 45*hl*(1-m) + 0*dmask
rough = rough*(1-0.85*sw)
rough = np.where(t > 0, rough, 5.0)
h = cv2.GaussianBlur(h, (0, 0), 1.2)
h = np.clip(h, 10, 318)

def save16(name, a): cv2.imwrite(os.path.join(OUT, name), np.clip(a, 0, 65535).astype(np.uint16))
save16("sdf.png", np.clip(sdf, -63, 63)*512 + 32768)
save16("height.png", (h + 64)*128)
cv2.imwrite(os.path.join(OUT, "rough.png"), np.clip(rough*2, 0, 255).astype(np.uint8))
cv2.imwrite(os.path.join(OUT, "biome.png"), bio)
cv2.imwrite(os.path.join(OUT, "cold.png"), (cold*255).astype(np.uint8))

# ---------------------------------------------------------------- 5. tabelas texto (coordenadas em blocos)
W = lambda p: ((p[0]-OX)*BPP, (p[1]-OY)*BPP)
with open(os.path.join(OUT, "locations.txt"), "w", encoding="utf-8") as f:
    f.write("# id|nome|tipo|regiao|x|z|flatten_raio|flatten_blend|status|pendente\n")
    for l in loc["locations"]:
        x, z = W(l["px_color"]); fl = l.get("flatten", {"radius": 0, "blend": 0})
        f.write("|".join(map(str, [l["id"], l["name"], l["kind"], l["region"], round(x), round(z), fl["radius"], fl["blend"], l["status"], 1 if l.get("pending") else 0]))+"\n")
with open(os.path.join(OUT, "rivers.txt"), "w", encoding="utf-8") as f:
    f.write("# id|nome|status|largura|x,z;x,z;...  (nascente -> foz)\n")
    for r in reg["rivers"]:
        pts = ";".join("%d,%d" % tuple(map(round, W(p))) for p in r["pts"])
        f.write("|".join([r["id"], r["name"], r["status"], str(r["width"]), pts])+"\n")
hf = cv2.GaussianBlur(h, (0, 0), 1.0)
with open(os.path.join(OUT, "lakes.txt"), "w", encoding="utf-8") as f:
    f.write("# id|x|z|rx|rz|nivel_agua\n")
    for k_ in reg["lakes"]:
        cx, cy = k_["center"]; x, z = W(k_["center"])
        ring = [hf[int(cy+k_["ry"]*np.sin(a)), int(cx+k_["rx"]*np.cos(a))] for a in np.linspace(0, 6.283, 24, endpoint=False)]
        lvl = max(64, int(np.floor(np.mean(ring))) - 1)
        f.write("|".join(map(str, [k_["id"], round(x), round(z), round(k_["rx"]*BPP), round(k_["ry"]*BPP), lvl]))+"\n")
with open(os.path.join(OUT, "meta.txt"), "w") as f:
    f.write("blocks_per_px=%d\norigin_px_x=%d\norigin_px_y=%d\nwidth=%d\nheight=%d\nsea_level=63\n" % (BPP, OX, OY, Ww, Hh))
import shutil; shutil.copy(os.path.join(ROOT, "data/biomes.csv"), os.path.join(OUT, "biomes.csv"))
print("ok; terra=%.1f%% mar | cold>0.5: %d px | desert: %d px | montanha: %d px" % (100*(~land).mean(), (cold > .5).sum(), desert.sum(), (m > .3).sum()))
