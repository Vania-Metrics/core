#!/usr/bin/env bash
# =============================================================================
# core/ — API + noyau (paper + velocity)
# Sort dans dist/ :
#   vania-metrics-api-<v>.jar        + son .pom
#   VaniaMetrics-<v>-paper.jar
#   VaniaMetrics-<v>-velocity.jar
#
# javac direct, pas Gradle : le noyau n'a AUCUNE dépendance à l'exécution.
# --release 21, imposé par le proxy (le lobby tourne en 25).
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

DEPS=.deps
BUILD=.build
DIST=dist
CIBLE_JAVA=21
GROUPE=fr.samflix

VERSION="$(sed -n 's/.*VALEUR = "\([^"]*\)".*/\1/p' api/src/fr/samflix/vaniametrics/api/Version.java)"
[[ -n "$VERSION" ]] || { echo "version illisible dans Version.java" >&2; exit 1; }

etape() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
info()  { printf '    %s\n' "$1"; }
erreur() { printf '\n\033[1;31m!! %s\033[0m\n' "$1" >&2; exit 1; }

VERIFIER=0
[[ "${1:-}" == "--verifier" ]] && VERIFIER=1

command -v javac >/dev/null || erreur "javac introuvable"

etape "Dépendances — VaniaMetrics core $VERSION"
mkdir -p "$DEPS"
while read -r somme url; do
    [[ -z "${somme:-}" || "$somme" == \#* ]] && continue
    f="$DEPS/${url##*/}"
    if [[ -s "$f" ]] && [[ "$(sha256sum "$f" | cut -d' ' -f1)" == "$somme" ]]; then continue; fi
    info "téléchargement ${url##*/}"
    curl -sSfL -o "$f" "$url" || erreur "téléchargement impossible : $url"
    reelle="$(sha256sum "$f" | cut -d' ' -f1)"
    [[ "$reelle" == "$somme" ]] || erreur "empreinte fausse pour ${url##*/}
    attendue $somme
    obtenue  $reelle"
done < deps.txt

CP="$(printf '%s:' "$DEPS"/*.jar)"
info "$(ls "$DEPS"/*.jar | wc -l) jar(s)"

etape "Compilation"
rm -rf "$BUILD" && mkdir -p "$BUILD"

javac --release "$CIBLE_JAVA" -proc:none -Xlint:all -Werror -encoding UTF-8 \
    -d "$BUILD/api" $(find api/src -name '*.java')
info "api — compilée seule, sans aucune API de serveur"

javac --release "$CIBLE_JAVA" -proc:none -Xlint:all,-path -Werror -encoding UTF-8 \
    -cp "$CP$BUILD/api" -d "$BUILD/core-paper" \
    $(find src/fr/samflix/vaniametrics/core src/fr/samflix/vaniametrics/paper -name '*.java')
info "noyau paper"

javac --release "$CIBLE_JAVA" -proc:full -Xlint:all,-path,-processing,-options -Werror -encoding UTF-8 \
    -cp "$CP$BUILD/api" -d "$BUILD/core-velocity" \
    $(find src/fr/samflix/vaniametrics/core src/fr/samflix/vaniametrics/velocity -name '*.java')
info "noyau velocity"

if [[ "$VERIFIER" == 1 ]]; then
    etape "Vérification seule"
    exit 0
fi

etape "Empaquetage"
rm -rf "$DIST" && mkdir -p "$DIST"

jar --create --file "$DIST/vania-metrics-api-$VERSION.jar" -C "$BUILD/api" .
cat > "$DIST/vania-metrics-api-$VERSION.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$GROUPE</groupId>
  <artifactId>vania-metrics-api</artifactId>
  <version>$VERSION</version>
  <name>VaniaMetrics API</name>
  <description>Interface publique de l'exportateur Prometheus VaniaMetrics.</description>
  <!-- AUCUNE DEPENDANCE, et c'est le point : l'API se compile sur le JDK seul. -->
</project>
POM
info "vania-metrics-api-$VERSION.jar (+ .pom)"

cp resources/metrics.properties "$BUILD/core-paper/"
cp resources/metrics.properties "$BUILD/core-velocity/"
cp -r "$BUILD/api"/* "$BUILD/core-paper/"
cp -r "$BUILD/api"/* "$BUILD/core-velocity/"
sed "s/\${version}/$VERSION/g" resources/paper/plugin.yml > "$BUILD/core-paper/plugin.yml"

jar --create --file "$DIST/VaniaMetrics-$VERSION-paper.jar" -C "$BUILD/core-paper" .
info "VaniaMetrics-$VERSION-paper.jar"
jar --create --file "$DIST/VaniaMetrics-$VERSION-velocity.jar" -C "$BUILD/core-velocity" .
info "VaniaMetrics-$VERSION-velocity.jar"

rm -rf "$BUILD"
