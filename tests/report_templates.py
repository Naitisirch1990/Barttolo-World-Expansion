"""Solo lee tamaño y cantidad aproximada de bloques de plantillas NBT externas.
No extrae ni redistribuye bloques, geometría, paletas, entidades ni recursos de terceros.
Uso: python tests/report_templates.py CTOV.jar Explorify.jar
"""
import gzip
import struct
import sys
import zipfile
from pathlib import Path
IDS=[
 'explorify:campsite/main/01','explorify:campsite/main/02',
 'explorify:campsite/main/03','explorify:campsite/tent/01',
 'explorify:guide_post/warm/whole','explorify:guide_post/cold/whole',
 'explorify:ruins/house/ruins_01','explorify:ruins/house/ruins_02',
 'explorify:ruins/monument/01','explorify:farmstead/main/01',
 'explorify:ruins/square/01','ctov:village/plains/house/small_1',
 'ctov:village/plains/house/small_2','ctov:village/plains/jobsite/bakery',
 'ctov:village/plains/decos/well'
]
class NBT:
    def __init__(self, data): self.d=memoryview(data); self.i=0
    def take(self,n):
        if n<0 or self.i+n>len(self.d): raise ValueError('NBT fuera de limites')
        p=self.d[self.i:self.i+n]; self.i+=n; return p
    def num(self,c): return struct.unpack('>'+c,self.take(struct.calcsize('>'+c)))[0]
    def string(self): return bytes(self.take(self.num('H'))).decode('utf8','replace')
    def payload(self,t):
        if t in (1,2,3,4,5,6): return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7:
            n=self.num('i');self.take(n);return f'bytes({n})'
        if t==8: return self.string()
        if t==9:
            typ=self.num('B');n=self.num('i')
            if n<0 or n>500000: raise ValueError('NBT list invalid length')
            # Solo retener lista de tres enteros de 'size'; descartar el resto.
            if typ==3 and n==3:return [self.payload(typ) for _ in range(n)]
            for _ in range(n):self.payload(typ)
            return f'list({n})'
        if t==10:
            result={}
            while True:
                kind=self.num('B')
                if kind==0:return result
                key=self.string()
                val=self.payload(kind)
                if key in ('size','blocks','entities'):result[key]=val
        if t in (11,12):
            n=self.num('i')
            if n<0 or n>500000: raise ValueError('NBT array invalid length')
            if t==11 and n==3:return [self.num('i') for _ in range(n)]
            self.take(n*(4 if t==11 else 8));return f'array({n})'
        raise ValueError('Tipo NBT no valido '+str(t))
    def root(self):
        assert self.num('B')==10
        self.string()
        return self.payload(10)

def report(files):
    entries={}
    for file in files:
        with zipfile.ZipFile(file) as z:
            for ident in IDS:
                ns, path=ident.split(':',1)
                for name in (f'data/{ns}/structure/{path}.nbt', f'data/{ns}/structures/{path}.nbt'):
                    if name in z.namelist():
                        raw=z.read(name)
                        if raw[:2]==b'\x1f\x8b':raw=gzip.decompress(raw)
                        try:
                            data=NBT(raw).root()
                            entries[ident]=(data.get('size'),data.get('blocks'),data.get('entities'))
                        except Exception as err:entries[ident]=(f'ERROR {err}',None,None)
    return entries

if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit('Uso: report_templates.py CTOV.jar Explorify.jar')
    print('id;size;blocks;entities')
    r=report(sys.argv[1:])
    for x in IDS:
        sz,bl,en=r.get(x,('no encontrado','',''))
        print(f'{x};{sz};{bl};{en}')
