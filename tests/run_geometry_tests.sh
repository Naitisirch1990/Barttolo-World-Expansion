#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 17 -d "$OUT" "$ROOT/src/main/java/net/barttolo/bwe/SiteMath.java" "$ROOT/tests/java/net/barttolo/bwe/SiteMathTest.java"
java -cp "$OUT" net.barttolo.bwe.SiteMathTest
