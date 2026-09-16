#!/bin/sh
set -eu

REPOSITORY_ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$REPOSITORY_ROOT"

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <llama-server> <model.gguf> <work-directory>" >&2
  exit 2
fi

LLAMA_SERVER=$1
MODEL_FILE=$2
WORK_DIRECTORY=$3
mkdir -p "$WORK_DIRECTORY"
WORK_DIRECTORY=$(CDPATH= cd -- "$WORK_DIRECTORY" && pwd)
chmod 700 "$WORK_DIRECTORY"
umask 077
SOCKET_PATH="$WORK_DIRECTORY/llama-server.sock"
rm -f "$SOCKET_PATH"

if ! curl --help all 2>/dev/null | grep -q -- '--unix-socket'; then
  echo "curl with --unix-socket support is required for this acceptance helper" >&2
  exit 1
fi

"$LLAMA_SERVER" -m "$MODEL_FILE" --alias local-model --host "$SOCKET_PATH" \
  >"$WORK_DIRECTORY/llama-server.log" 2>&1 &
LLAMA_PID=$!
cleanup() {
  kill "$LLAMA_PID" 2>/dev/null || true
  rm -f "$SOCKET_PATH"
}
trap cleanup EXIT INT TERM

attempt=0
until curl --fail --silent --unix-socket "$SOCKET_PATH" http://localhost/health >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 120 ]; then
    echo "llama-server did not become healthy on $SOCKET_PATH; see $WORK_DIRECTORY/llama-server.log" >&2
    exit 1
  fi
  sleep 1
done

GRADLE_COMMAND=${GRADLE_COMMAND:-./gradlew}
"$GRADLE_COMMAND" --no-daemon clean check javadoc publish installDist distZip
"$GRADLE_COMMAND" --no-daemon -p verification/sdk-consumer clean compileJava
cp config/madre.properties.example "$WORK_DIRECTORY/madre.properties"
sed -i "s|kernel.database=.*|kernel.database=$WORK_DIRECTORY/kernel-work.sqlite|" "$WORK_DIRECTORY/madre.properties"
sed -i "s|module.owner-interaction.state=.*|module.owner-interaction.state=$WORK_DIRECTORY/owner-interaction-background.state|" "$WORK_DIRECTORY/madre.properties"
sed -i "s|connector.llamacpp-unix.enabled=.*|connector.llamacpp-unix.enabled=true|" "$WORK_DIRECTORY/madre.properties"
sed -i "s|connector.llamacpp-unix.socket=.*|connector.llamacpp-unix.socket=$SOCKET_PATH|" "$WORK_DIRECTORY/madre.properties"
sed -i "s|connector.llamacpp.enabled=.*|connector.llamacpp.enabled=false|" "$WORK_DIRECTORY/madre.properties"

echo "MADRE acceptance console is starting through the Unix-domain-socket llama.cpp Capability."
echo "1. Enter /standard Explain why typed algebra matters."
echo "2. Enter an ordinary difficult prompt and observe madre> before background>."
echo "3. For restart recovery, stop MADRE after submitting ordinary text, rerun the command below, and observe background delivery."
echo "4. Inspect SQLite with: sqlite3 $WORK_DIRECTORY/kernel-work.sqlite '.schema'"
echo "5. The llama-server log is: $WORK_DIRECTORY/llama-server.log"
echo "Start/restart: madre-app/build/install/madre/bin/madre $WORK_DIRECTORY/madre.properties"
madre-app/build/install/madre/bin/madre "$WORK_DIRECTORY/madre.properties"
