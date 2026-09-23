# vania-metrics — core

L'API publique et le noyau (Paper + Velocity) de VaniaMetrics.

```sh
./build.sh              # tout, dans dist/
./build.sh --verifier   # compile seulement
```

## Ce qui sort

```
dist/
  vania-metrics-api-<v>.jar        l'interface publique
  vania-metrics-api-<v>.pom        pour dépendre en Maven/Gradle (mvn install:install-file)
  VaniaMetrics-<v>-paper.jar       le noyau, plugin Bukkit
  VaniaMetrics-<v>-velocity.jar    le noyau, plugin Velocity
```

## Les collecteurs

Chaque collecteur vit dans son propre dépôt (`../colecteur-<nom>/`), et
compile contre `dist/vania-metrics-api-<v>.jar` — construit ici d'abord.

Voir le [README d'origine](../README.md) pour la conception d'ensemble :
convention de nommage `mc_<domaine>_<sujet>`, l'écriture d'un module,
et les règles qui ne se discutent pas.
