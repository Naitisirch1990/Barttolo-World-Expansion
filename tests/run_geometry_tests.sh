#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 17 -d "$OUT" "$ROOT/src/main/java/net/barttolo/bwe/SiteMath.java" "$ROOT/tests/java/net/barttolo/bwe/SiteMathTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TemplateFootprint.java" "$ROOT/tests/java/net/barttolo/bwe/TemplateFootprintTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TemplatePlacementVolume.java" "$ROOT/tests/java/net/barttolo/bwe/TemplatePlacementVolumeTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TemplateScanCursor.java" "$ROOT/tests/java/net/barttolo/bwe/TemplateScanCursorTest.java" "$ROOT/src/main/java/net/barttolo/bwe/SurfaceProfile.java" "$ROOT/tests/java/net/barttolo/bwe/SurfaceProfileTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TerrainColumnProbe.java" "$ROOT/tests/java/net/barttolo/bwe/TerrainColumnProbeTest.java" "$ROOT/tests/java/net/barttolo/bwe/TerrainColumnProbeLayeredTest.java" "$ROOT/src/main/java/net/barttolo/bwe/ClearanceVolume.java" "$ROOT/tests/java/net/barttolo/bwe/ClearanceVolumeTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TerraformColumnCursor.java" "$ROOT/tests/java/net/barttolo/bwe/TerraformColumnCursorTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TerraformAreaCursor.java" "$ROOT/tests/java/net/barttolo/bwe/TerraformAreaCursorTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TerraformStepGuard.java" "$ROOT/tests/java/net/barttolo/bwe/TerraformStepGuardTest.java" "$ROOT/src/main/java/net/barttolo/bwe/TerraformBackupFile.java" "$ROOT/tests/java/net/barttolo/bwe/TerraformBackupFileTest.java"
java -cp "$OUT" net.barttolo.bwe.SiteMathTest
java -cp "$OUT" net.barttolo.bwe.TemplateFootprintTest
java -cp "$OUT" net.barttolo.bwe.TemplatePlacementVolumeTest

java -cp "$OUT" net.barttolo.bwe.TemplateScanCursorTest
java -cp "$OUT" net.barttolo.bwe.SurfaceProfileTest

java -cp "$OUT" net.barttolo.bwe.TerrainColumnProbeTest
java -cp "$OUT" net.barttolo.bwe.TerrainColumnProbeLayeredTest

java -cp "$OUT" net.barttolo.bwe.ClearanceVolumeTest
java -cp "$OUT" net.barttolo.bwe.TerraformColumnCursorTest
java -cp "$OUT" net.barttolo.bwe.TerraformAreaCursorTest
java -cp "$OUT" net.barttolo.bwe.TerraformStepGuardTest
java -cp "$OUT" net.barttolo.bwe.TerraformBackupFileTest
