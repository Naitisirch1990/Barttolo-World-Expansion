from pathlib import Path

main = Path('src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java').read_text()
engine = Path('src/main/java/net/barttolo/bwe/BuildEngine.java').read_text()
assert 'ServerStoppedEvent' in main
assert 'public void onServerStopped(ServerStoppedEvent event)' in main
assert 'BuildEngine.resetForShutdown();' in main
assert 'PLANS.clear();' in main
assert 'public static void resetForShutdown()' in engine
assert 'running = null;' in engine[engine.index('public static void resetForShutdown()'):engine.index('public static boolean busy()')]
assert 'undoRunning = null;' in engine[engine.index('public static void resetForShutdown()'):engine.index('public static boolean busy()')]
assert 'WorldData.get(world).clearUndo()' not in engine[engine.index('public static void resetForShutdown()'):engine.index('public static boolean busy()')]
print('OK: parada del servidor limpia referencias en memoria sin borrar el respaldo (auditoria estatica)')
