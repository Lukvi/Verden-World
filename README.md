# Verden-World

O continente de **Verden** em escala **1:1** para Minecraft (Fabric 1.21.1). Projeto independente: só o mundo, sem sistemas de RPG.

**Versão 0.1 — validação geográfica.** Continente completo (relevo, oceanos, rios, lagos, biomas, árvores por bioma, regiões naturais) e as 58 localidades posicionadas, cada uma marcada por um obelisco provisório. Cidades ainda não construídas.

## Escala e coordenadas
- 1 bloco = 1 m; 400 blocos por pixel do mapa colorido (provisório). Continente ≈ 345 × 300 km; mundo todo ≈ 546 × 410 km.
- Origem (0,0) = centro do lago da **Interseção dos Rios**. X leste, Z sul. Nível do mar Y=63.
- Obelisco **dourado** (topo) = capital; **glowstone** = localidade; **terracota laranja** = posição provisória/pendente.

## Como testar (precisa ser feito por você: o mod não foi compilado nem rodado onde foi escrito)
1. Obtenha o `.jar`: aba *Actions* do GitHub → build → artefato `verden-world-jar` (ou `gradle build` localmente com Java 21).
2. Instale Minecraft **1.21.1**, Fabric Loader ≥ 0.16 e **Fabric API**. Coloque `verden-world-*.jar` e o Fabric API em `mods/`.
3. *Criar novo mundo → Mais opções → Tipo de mundo: clique no botão até aparecer **Verden**. Ative cheats.
4. Você nasce em **Jord**. Comandos: `/verden list`, `/verden tp <id>` (ex.: `jord`, `nagdaar`, `flerin`, `veroogh`, `montainezz`), `/verden where`.
5. Ao gerar áreas novas, espere: tudo é gerado sob demanda. F3 mostra o local mais próximo.
- Opções JVM: `-Dverden.markers=false` (sem obeliscos), `-Dverden.pendingRivers=false` (sem os trechos de rio pendentes).

## Estrutura
- `data/` — fontes canônicas editáveis (`locations.json`, `regions.json`, `biomes.csv`); `reference/` — os mapas originais.
- `tools/build_rasters.py` — gera deterministicamente os rasters em `src/main/resources/verden_world/maps/`.
- `src/main/java/.../core` — núcleo sem dependência de Minecraft (terreno determinístico); `.../world` — cola Fabric (gerador, bioma, comandos).
- `tools/run_checks.sh` — testes do núcleo; `tools/java/Preview.java` — imagens em `docs/images/`.
- Namespace: `verden_world` (não colide com outros mods Verden).

Veja `docs/DIVERGENCIAS_E_LIMITES.md`.
