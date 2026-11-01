"""Static fail-closed parity check; not an in-game test."""
from pathlib import Path
import re
p=Path(__file__).resolve().parents[1]/'src/main/java/net/barttolo/bwe/BuildEngine.java'
s=p.read_text()
assert 'private static boolean occupied(ServerLevel world, Plan plan)' in s
assert 'world.getEntitiesOfClass(Entity.class, footprint)' in s, 'Proteger items, botes, marcos y entidades no vivas'
build=s[s.index('public static Result build('):s.index('public static Result undo(')]
simulate=s[s.index('public static Result simulate('):]
assert 'if(occupied(world,p))' in build and 'if(occupied(world,p))' in simulate
assert 'if(!p.template().isEmpty())' in build and 'if(!p.template().isEmpty())' in simulate
for fragment in ('WorldChecks.scanFull(world,p.center(),p.halfX(),p.halfZ(),4)',
                 'old.hasBlockEntity() || !WorldChecks.natural(old)',
                 'MAX_CHANGES'):
    assert fragment in build and fragment in simulate, fragment
print('OK: simulacion y construccion comparten barrera de entidades, escaneo total y estado natural; no verifica Forge runtime')
