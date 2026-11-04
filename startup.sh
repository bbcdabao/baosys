#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$ROOT_DIR/out"

if [[ ! -d "$OUT_DIR" ]]; then
    echo 'out directory is missing. Run mvn install first.' >&2
    exit 1
fi
cd -- "$OUT_DIR"
shopt -s nullglob
jars=(./*.jar)
if (( ${#jars[@]} == 0 )); then
    echo 'No JAR files in out. Run mvn install first.' >&2
    exit 1
fi
command -v java >/dev/null || { echo 'java is required on PATH.' >&2; exit 1; }

if [[ ! -f signing-jwks.json ]]; then
    command -v jshell >/dev/null || { echo 'jshell is required on PATH (JDK 17+).' >&2; exit 1; }
    jshell '-R-Dsigning.key.output=signing-jwks.json' "$ROOT_DIR/scripts/generate-signing-key.jsh"
    [[ -s signing-jwks.json ]] || { echo 'Signing key generation failed.' >&2; exit 1; }
fi

# All applications run from out, so this file resource resolves to out/signing-jwks.json.
export JWT_JWK_SET_LOCATION='file:./signing-jwks.json'
export DEFAULT_ADMIN_ENABLED='true'
export DEFAULT_ADMIN_USERNAME='admin'
export DEFAULT_ADMIN_PASSWORD='LocalAdmin123!'

pids=()
cleanup() {
    trap '' INT TERM
    if (( ${#pids[@]} > 0 )); then
        echo 'Stopping applications...'
        kill -TERM "${pids[@]}" 2>/dev/null || true
        # Give Spring shutdown hooks up to ten seconds, then force remaining JVMs to exit.
        for ((attempt = 0; attempt < 10; attempt++)); do
            alive=false
            for pid in "${pids[@]}"; do
                if kill -0 "$pid" 2>/dev/null; then alive=true; break; fi
            done
            if [[ "$alive" == false ]]; then break; fi
            sleep 1
        done
        kill -KILL "${pids[@]}" 2>/dev/null || true
        wait "${pids[@]}" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for jar in "${jars[@]}"; do
    echo "Starting ${jar#./}"
    java -jar "$jar" &
    pids+=("$!")
done
echo 'Applications started. Press Ctrl+C to stop all applications.'
# Bash 4.3+: stop the group when any application exits, including startup failures.
wait -n
