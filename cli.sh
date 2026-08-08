#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

case "$(uname -s)" in
  Darwin)
    GAME_HOME="$SCRIPT_DIR/../.."
    JAVA="$GAME_HOME/Contents/Home/bin/java"
    GAME_LIB="$GAME_HOME/Contents/Resources/Java"
    MODS_DIRECTORY="$GAME_HOME/mods"
    ;;
  Linux)
    GAME_HOME="$SCRIPT_DIR/../.."
    JAVA="$GAME_HOME/jre_linux/bin/java"
    GAME_LIB="$GAME_HOME"
    MODS_DIRECTORY="$GAME_HOME/mods"
    ;;
  *)
    echo "error: unsupported operating system: $(uname -s)" >&2
    exit 1
    ;;
esac

ECJ_JAR="$(find "$SCRIPT_DIR/jars" -maxdepth 1 -type f -name 'ecj*.jar' -print | sort | tail -n 1)"

if [ ! -x "$JAVA" ]; then
  echo "error: game java runtime not found at $JAVA" >&2
  exit 1
fi

if [ ! -d "$GAME_LIB" ]; then
  echo "error: game library directory not found at $GAME_LIB" >&2
  exit 1
fi

if [ -z "$ECJ_JAR" ]; then
  echo "error: no ECJ compiler jar found in jars/" >&2
  exit 1
fi

COMPILER_CP="$ECJ_JAR"
while IFS= read -r jar; do
  COMPILER_CP="$COMPILER_CP:$jar"
done < <(find "$GAME_LIB" -maxdepth 1 -type f -name '*.jar' -print | sort)

STAMP="$SCRIPT_DIR/out/microforge-cli/.built"
NEEDS_BUILD=1
if [ -f "$STAMP" ] && [ -z "$(find microforge-cli/src/main/java microforge-core/src/main/java cli.sh -newer "$STAMP" -print -quit)" ]; then
  NEEDS_BUILD=0
fi

if [ "$NEEDS_BUILD" -eq 1 ]; then
  echo "building cli..." >&2
  rm -rf "$SCRIPT_DIR/out/microforge-cli"
  mkdir -p "$SCRIPT_DIR/out/microforge-cli/classes"

  find microforge-cli/src/main/java microforge-core/src/main/java -name '*.java' -print | sort > "$SCRIPT_DIR/out/microforge-cli.sources"
  if [ ! -s "$SCRIPT_DIR/out/microforge-cli.sources" ]; then
    echo "error: no Java sources found under microforge-cli/src/main/java or microforge-core/src/main/java" >&2
    exit 1
  fi

  "$JAVA" -jar "$ECJ_JAR" \
    -17 \
    -encoding UTF-8 \
    -proc:none \
    -cp "$COMPILER_CP" \
    -time \
    -d "$SCRIPT_DIR/out/microforge-cli/classes" \
    @"$SCRIPT_DIR/out/microforge-cli.sources"

  touch "$STAMP"
fi

exec "$JAVA" \
   -XX:+UnlockDiagnosticVMOptions \
   -XX:-BytecodeVerificationLocal \
   -XX:-BytecodeVerificationRemote \
   -Drazen.microforge.cli.path.game="$GAME_LIB" \
   -Drazen.microforge.cli.path.ecj="$ECJ_JAR" \
   -Dcom.fs.starfarer.settings.paths.mods="$MODS_DIRECTORY" \
   -cp "$SCRIPT_DIR/out/microforge-cli/classes:$ECJ_JAR:$GAME_LIB/*" \
   razen.microforge.cli.Main "$@"
