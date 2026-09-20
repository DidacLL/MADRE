#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: caller_disappearance.sh <kernel-binary> <java-classpath>" >&2
  exit 2
fi

kernel_binary=$1
classpath=$2
state_dir=$(mktemp -d)
socket_path="$state_dir/kernel.sock"
kernel_pid=""

cleanup() {
  if [[ -n "$kernel_pid" ]] && kill -0 "$kernel_pid" 2>/dev/null; then
    kill "$kernel_pid" || true
    wait "$kernel_pid" || true
  fi
  rm -rf "$state_dir"
}
trap cleanup EXIT

"$kernel_binary" --data-dir "$state_dir/data" --endpoint "$socket_path" --fake-delay-ms 500 >"$state_dir/kernel.log" 2>&1 &
kernel_pid=$!

for _ in $(seq 1 100); do
  [[ -S "$socket_path" ]] && break
  sleep 0.02
done
[[ -S "$socket_path" ]] || { cat "$state_dir/kernel.log" >&2; echo "Kernel socket did not appear" >&2; exit 1; }

java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess inspect "$socket_path"
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess framing-mismatch "$socket_path"
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess protocol-version-mismatch "$socket_path"
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess protocol-overlap "$socket_path"
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess payload-limits "$socket_path"

# A second Kernel must fail before touching the live endpoint, and Kernel A must remain reachable.
if "$kernel_binary" --data-dir "$state_dir/second-data" --endpoint "$socket_path" --fake-delay-ms 10 >"$state_dir/second-kernel.log" 2>&1; then
  echo "second Kernel unexpectedly acquired live endpoint" >&2
  exit 1
fi
grep -q "Kernel endpoint lock is already held" "$state_dir/second-kernel.log" || {
  cat "$state_dir/second-kernel.log" >&2
  echo "second Kernel did not fail on the endpoint lifetime lock" >&2
  exit 1
}
[[ -S "$socket_path" ]] || { echo "live Kernel socket disappeared after second startup attempt" >&2; exit 1; }
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess inspect "$socket_path"

work_id=$(java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess submit "$socket_path" "caller-gone")
[[ "$work_id" =~ ^[0-9a-f]{32}$ ]] || { echo "invalid WorkId: $work_id" >&2; exit 1; }

# The submitter JVM has exited here. The Kernel must own the remaining physical responsibility.
if pgrep -f "io.github.didacll.madre.kernel.client.KernelClientProcess submit $socket_path" >/dev/null 2>&1; then
  echo "submitting Java process is still alive" >&2
  exit 1
fi

[[ -f "$state_dir/data/kernel.db" ]] || { echo "SQLite Work database missing" >&2; exit 1; }
[[ -f "$state_dir/data/work/$work_id/input.bin" ]] || { echo "opaque input file missing" >&2; exit 1; }

# Verify none of the Kernel's open socket file descriptors are TCP/TCP6 sockets.
mapfile -t socket_inodes < <(for fd in /proc/$kernel_pid/fd/*; do readlink "$fd" 2>/dev/null || true; done | sed -n 's/socket:\[\([0-9]*\)\]/\1/p')
for inode in "${socket_inodes[@]}"; do
  if awk -v inode="$inode" 'NR>1 && $10 == inode { found=1 } END { exit !found }' /proc/$kernel_pid/net/tcp /proc/$kernel_pid/net/tcp6; then
    echo "Kernel owns a TCP socket inode $inode" >&2
    exit 1
  fi
done

# A new JVM, with no state inherited from the submitter, reconnects and consumes the durable result.
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess collect "$socket_path" "$work_id" "fake:caller-gone"
[[ ! -f "$state_dir/data/work/$work_id/input.bin" ]] || { echo "acknowledged input payload was not removed" >&2; exit 1; }
[[ ! -f "$state_dir/data/work/$work_id/result.bin" ]] || { echo "acknowledged result payload was not removed" >&2; exit 1; }

# Cancellation is part of the C1 command surface and must work for queued/running fake Work.
java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess cancel "$socket_path" "cancel-me"

# Unclean death must release the OS lock while leaving a stale socket pathname.
kill -KILL "$kernel_pid"
wait "$kernel_pid" 2>/dev/null || true
kernel_pid=""
[[ -S "$socket_path" ]] || { echo "unclean Kernel death did not leave stale socket for recovery test" >&2; exit 1; }

"$kernel_binary" --data-dir "$state_dir/data" --endpoint "$socket_path" --fake-delay-ms 50 >"$state_dir/restarted-kernel.log" 2>&1 &
kernel_pid=$!
recovered=0
for _ in $(seq 1 100); do
  if kill -0 "$kernel_pid" 2>/dev/null &&
      java -cp "$classpath" io.github.didacll.madre.kernel.client.KernelClientProcess inspect "$socket_path" >"$state_dir/recovery-inspect.log" 2>&1; then
    recovered=1
    break
  fi
  sleep 0.02
done
if [[ "$recovered" -ne 1 ]]; then
  cat "$state_dir/restarted-kernel.log" >&2
  cat "$state_dir/recovery-inspect.log" >&2 || true
  echo "replacement Kernel did not recover stale socket after unclean termination" >&2
  exit 1
fi
cat "$state_dir/recovery-inspect.log"

kill "$kernel_pid"
wait "$kernel_pid"
kernel_pid=""

echo "caller-disappearance and C1 corrective quality-gate acceptance passed: $work_id"
