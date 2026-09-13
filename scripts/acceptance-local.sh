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

"$LLAMA_SERVER" -m "$MODEL_FILE" --host 127.0.0.1 --port 8080 >"$WORK_DIRECTORY/llama-server.log" 2>&1 &
LLAMA_PID=$!
cleanup() { kill "$LLAMA_PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

attempt=0
until curl --fail --silent http://127.0.0.1:8080/health >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 120 ]; then
    echo "llama-server did not become healthy; see $WORK_DIRECTORY/llama-server.log" >&2
    exit 1
  fi
  sleep 1
done

GRADLE_COMMAND=${GRADLE_COMMAND:-gradle}
"$GRADLE_COMMAND" --no-daemon clean check javadoc publish installDist distZip
"$GRADLE_COMMAND" --no-daemon -p verification/sdk-consumer clean compileJava
cp config/madre.properties.example "$WORK_DIRECTORY/madre.properties"
sed -i "s|kernel.database=.*|kernel.database=$WORK_DIRECTORY/kernel-work.sqlite|" "$WORK_DIRECTORY/madre.properties"
sed -i "s|core.state=.*|core.state=$WORK_DIRECTORY/core-background.state|" "$WORK_DIRECTORY/madre.properties"

echo "MADRE acceptance console is starting."
echo "1. Enter /standard Explain why typed algebra matters."
echo "2. Enter an ordinary difficult prompt and observe core> before background>."
echo "3. For restart recovery, stop MADRE after submitting ordinary text, rerun the command below, and observe background delivery."
echo "4. Inspect SQLite with: sqlite3 $WORK_DIRECTORY/kernel-work.sqlite '.schema'"
echo "Start/restart: madre-app/build/install/madre/bin/madre $WORK_DIRECTORY/madre.properties"
madre-app/build/install/madre/bin/madre "$WORK_DIRECTORY/madre.properties"
