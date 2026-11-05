#!/usr/bin/env bash
# Compila o núcleo (Java puro, sem Minecraft) e roda as verificações.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/classes; mkdir -p "$OUT"
javac -d "$OUT" src/main/java/io/github/lukvi/verdenworld/core/*.java src/test/java/io/github/lukvi/verdenworld/core/CoreChecks.java
java -cp "$OUT" io.github.lukvi.verdenworld.core.CoreChecks src/main/resources/verden_world/maps
