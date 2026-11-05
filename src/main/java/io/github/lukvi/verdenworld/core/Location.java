package io.github.lukvi.verdenworld.core;

/** Localidade/ponto do mapa (posição em blocos, origem na Interseção dos Rios). */
public final class Location {
    public final String id, name, kind, region, status;
    public final int x, z, flatRadius, flatBlend;
    public final boolean pending;
    /** Altura-alvo do platô (calculada no carregamento); 0 = sem platô. */
    int targetY;

    Location(String id, String name, String kind, String region, int x, int z, int fr, int fb, String status, boolean pending) {
        this.id = id; this.name = name; this.kind = kind; this.region = region; this.x = x; this.z = z;
        this.flatRadius = fr; this.flatBlend = fb; this.status = status; this.pending = pending;
    }
    public int targetY() { return targetY; }
    public boolean isCapital() { return "capital".equals(kind); }
}
