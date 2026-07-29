#!/bin/bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

GAME_HOME="$DIR/../../Contents/Home"
JAVA="$GAME_HOME/bin/java"
GAME_LIB="$DIR/../../Contents/Resources/Java"
ECJ_JAR="$(find "$DIR/jars" -maxdepth 1 -type f -name 'ecj*.jar' -print | sort | tail -n 1)"

if [ ! -x "$JAVA" ]; then
  echo "error: game java runtime not found at $JAVA" >&2
  exit 1
fi

if [ -z "$ECJ_JAR" ]; then
  echo "error: no ECJ compiler jar found in jars/" >&2
  exit 1
fi

STAMP="out/microforge-cli/.built"
NEEDS_BUILD=1
if [ -f "$STAMP" ] && [ -z "$(find microforge-cli/src/main/java microforge-compiler/src/main/java cli.sh -newer "$STAMP" -print -quit)" ]; then
  NEEDS_BUILD=0
fi

if [ "$NEEDS_BUILD" -eq 1 ]; then
  echo "building cli..." >&2
  rm -rf out/microforge-cli
  mkdir -p out/microforge-cli/classes

  find microforge-cli/src/main/java microforge-compiler/src/main/java -name '*.java' -print | sort > out/microforge-cli.sources
  if [ ! -s out/microforge-cli.sources ]; then
    echo "error: no Java sources found under microforge-cli/src/main/java or microforge-compiler/src/main/java" >&2
    exit 1
  fi

  COMPILER_CP="$ECJ_JAR"
  while IFS= read -r jar; do
    COMPILER_CP="$COMPILER_CP:$jar"
  done < <(find "$GAME_LIB" -maxdepth 1 -type f -name '*.jar' -print | sort)

  "$JAVA" -jar "$ECJ_JAR" \
    -17 \
    -encoding UTF-8 \
    -proc:none \
    -cp "$COMPILER_CP" \
    -time \
    -d out/microforge-cli/classes \
    @out/microforge-cli.sources

  touch "$STAMP"
fi

"$JAVA" \
   -Drazen.microforge.cli.path.game="$GAME_LIB" \
   -Drazen.microforge.cli.path.ecj="$ECJ_JAR" \
   -cp "out/microforge-cli/classes:$ECJ_JAR" \
   razen.microforge.cli.Main "$@"
