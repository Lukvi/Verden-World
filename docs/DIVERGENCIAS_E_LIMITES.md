# Divergências, pendências e limites (v0.1)

Hierarquia usada: Guia de Cartografia > livros > mapa colorido (posições) > pergaminho (conferência).

## Pendências de canon (não resolvidas silenciosamente)
- **Rio Real**: livro diz "Real a Oeste"; mapa mostra faixa a oeste até a costa de Nagdaar *e* o Guia/leitura do livro ("seguir o Real leva ao Kapenge") sugere descida até virar Dash. Ambos os ramos (`real_oeste`, `real_dash`) estão marcados PENDENTE e podem ser desligados (`-Dverden.pendingRivers=false`). Renderizados em laranja nas imagens.
- **Marin × Maariz × Synor**: tratados como hipótese de um só rio ao sul (id `synor`).
- **Rio Mak** (norte): traçado superior inferido (HIPOTESE).
- **Lolity**: provisoriamente na ilha NE do Mar Hojre — pendente de confirmação. A ilha NW (antes com esse nome) foi mantida como "Ilha da Caverna Frajorn (nome pendente)".
- **Muzaloth**: sem posição clara no mapa; posição aproximada.
- **Porto de Jord**: inferido do texto (status TEXTO).
- **Costa sul de Karamarraw**: inferida (o mapa a corta).
- Aslogy, Runes, Belins, Ost Sirener, Oerr etc.: preservadas sem lore, sem história inventada.

## "Expansões ambientais" (invenção minha, não canon)
Raios de platô das localidades; contornos de florestas/pântanos/campos (aproximações visuais dos mapas); vale de Veroogh; escolha de bioma Minecraft das ilhas; compressão vertical do relevo (picos ≤ ~315); dunas e ruído de relevo.

## Limites conhecidos
- **Mod não compilado nem testado aqui** (hosts Maven/Fabric bloqueados). Pontos prováveis de ajuste de API: `carve`, `populateNoise`, `RegistryOps.getEntryLookupCodec`, `ServerPlayerEntity.teleport`, `Identifier.of`, `LeavesBlock.PERSISTENT`, versões em `gradle.properties`. O CI mostra os erros.
- O núcleo Java (terreno) foi testado: determinismo, ordem de geração, localidades em terra, capitais planas e secas, ~0,73 ms/chunk.
- Árvores: carvalho, bétula, carvalho-escuro, abeto e selva, determinísticas por bioma (`core/Trees.java`, testada sem emendas entre chunks). Cobertura de chão por bioma (`core/GroundCover.java`: grama, samambaia, flores, arbusto seco). Sem troncos grandes 2x2 nem grama alta dupla.
- Sem cavernas, sem mobs gerados, sem estruturas vanilla.
- Escala 400 blocos/px é provisória.
