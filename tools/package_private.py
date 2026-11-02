"""Complete a tested BWE engine with the user's unchanged source JARs. Never upload output to GitHub."""
import argparse,zipfile,pathlib,hashlib,json

def package(engine,ctov,explorify,catalog,output):
    originals={'ctov.jar':pathlib.Path(ctov),'explorify.jar':pathlib.Path(explorify)}
    manifest=json.loads(pathlib.Path(catalog).read_text())
    for s in manifest['sources']:
        assert hashlib.sha256(originals[s['file']].read_bytes()).hexdigest()==s['sha256']
    with zipfile.ZipFile(engine) as source,zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as dest:
        assert source.testzip() is None
        assert 'version="0.8.0"' in source.read('META-INF/mods.toml').decode()
        for item in source.infolist():dest.writestr(item,source.read(item.filename))
        prefix='assets/barttoloworldexpansion/'
        for name,p in originals.items():dest.write(p,prefix+name)
        dest.write(catalog,prefix+'catalogo.json')
        # Loot tables alone cannot place structures or regenerate chunks.
        tables=0
        for p in originals.values():
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.startswith(('data/ctov/loot_tables/','data/explorify/loot_tables/')) and n.endswith('.json'):
                        json.loads(z.read(n));dest.writestr(n,z.read(n));tables+=1
        dest.writestr('BWE-RECURSOS-ORIGINALES.txt',
            'Private build from files supplied by the user.\n'
            'CTOV 3.4.14 by ChoiceTheorem; original license: BY-NC-ND-4.0.\n'
            'Explorify 1.6.5 by bebebea_loste; original license: ARR.\n'
            'Source JAR bytes and their metadata are unchanged. No new license is assigned to third-party resources.\n'
            'The source JARs are passive archives, not loaded mods. No worldgen definitions are registered.\n'
            'Manual layouts assembled from connectors; random worldgen processors are not reproduced.\n')
    with zipfile.ZipFile(engine) as source,zipfile.ZipFile(output) as dest:
        assert dest.testzip() is None
        for n in source.namelist():assert dest.read(n)==source.read(n)
        for name,p in originals.items():assert dest.read('assets/barttoloworldexpansion/'+name)==p.read_bytes()
        assert not any('/worldgen/' in n for n in dest.namelist())
    print(json.dumps({'file':str(output),'bytes':pathlib.Path(output).stat().st_size,'sha256':hashlib.sha256(pathlib.Path(output).read_bytes()).hexdigest(),'entries':len(manifest['entries']),'loot_tables':tables}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('engine');p.add_argument('ctov');p.add_argument('explorify');p.add_argument('catalog');p.add_argument('output');a=p.parse_args();package(a.engine,a.ctov,a.explorify,a.catalog,a.output)
