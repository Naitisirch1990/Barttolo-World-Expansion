"""Auditoría SOLO LECTURA de identificadores de plantillas (CTOV/Explorify).
No empaqueta recursos ajenos y no garantiza disponibilidad en el gestor de estructuras del servidor.
"""
import sys
import zipfile
from pathlib import Path

IDS = [
    'explorify:campsite/main/01', 'explorify:campsite/main/02',
    'explorify:campsite/main/03', 'explorify:campsite/tent/01',
    'explorify:guide_post/warm/whole', 'explorify:guide_post/cold/whole',
    'explorify:ruins/house/ruins_01', 'explorify:ruins/house/ruins_02',
    'explorify:ruins/monument/01', 'explorify:farmstead/main/01',
    'explorify:ruins/square/01', 'ctov:village/plains/house/small_1',
    'ctov:village/plains/house/small_2', 'ctov:village/plains/jobsite/bakery',
    'ctov:village/plains/decos/well',
]

def validate_files(paths):
    archives = {}
    for filename in paths:
        with zipfile.ZipFile(filename) as archive:
            for n in archive.namelist():
                if n.endswith('.nbt') and (n.startswith('data/') or n.startswith('f15/data/')):
                    archives[n] = filename
    missing=[]
    for identifier in IDS:
        ns, short = identifier.split(':', 1)
        options = (f'data/{ns}/structures/{short}.nbt', f'data/{ns}/structure/{short}.nbt',
                   f'f15/data/{ns}/structures/{short}.nbt',f'f15/data/{ns}/structure/{short}.nbt')
        if not any(o in archives for o in options):
            print('FALTA', identifier)
            missing.append(identifier)
        else:
            print('EN ARCHIVO', identifier)
    if missing:
        raise SystemExit(f'Faltan {len(missing)} plantillas')
    print(f'{len(IDS)} identificadores presentes en archivos: no prueba carga runtime en Forge 1.20.1')

if __name__=='__main__':
    if len(sys.argv)!=3: raise SystemExit('Uso: python tests/verify_resource_ids.py CTOV.jar Explorify.jar')
    validate_files(sys.argv[1:])
