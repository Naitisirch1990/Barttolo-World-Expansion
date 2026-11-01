"""Static CI wiring audit. Does not compile Forge or contact GitHub."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
yaml = (root / '.github/workflows/build-forge.yml').read_text()
assert 'python3 tests/verify_simulation_parity.py' in yaml
assert 'python3 tests/verify_ci.py' in yaml
assert 'python3 tests/test_live_occupancy_guard.py' in yaml
assert 'python3 tests/test_strict_undo_codec.py' in yaml
assert 'python3 tests/test_completion_marker.py' in yaml
assert 'python3 tests/test_server_lifecycle.py' in yaml
assert 'python3 tests/test_undo_entity_guard.py' in yaml
assert 'pull_request:' in yaml
assert 'branches: [main]' in yaml
assert 'python3 tests/test_template_command_id.py' in yaml
assert 'python3 tests/verify_no_third_party_assets.py' in yaml
assert 'if-no-files-found: error' in yaml
assert 'path: build/libs/*.jar' in yaml
assert 'Validar version, integridad y bytecode del JAR' in yaml
assert 'version="0.6.9"' in yaml
assert 'major version: 61' in yaml
assert 'gradle --no-daemon --stacktrace build' in yaml
assert 'if [[ ! -f build.gradle' in yaml, 'CI debe fallar si faltan los fuentes'
assert '::error::' in yaml
assert 'java-version: \'17\'' in yaml
print('OK: CI verifica fuentes y pruebas y recoge el JAR; no equivale a ejecutar Actions')
