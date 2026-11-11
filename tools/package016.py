import zipfile,io,pathlib
base=zipfile.ZipFile('BarttoloWorldExpansion-0.13.0-BETTER-VILLAGE-Y-GRAND-CAPITALS-test.jar')
artifact=zipfile.ZipFile('rotation-build.zip')
engine=zipfile.ZipFile(io.BytesIO(artifact.read(next(n for n in artifact.namelist() if n.endswith('.jar')))))
updates={n:engine.read(n) for n in engine.namelist() if n.startswith('net/barttolo/bwe/') and n.endswith('.class')}
name='BarttoloWorldExpansion-0.14.0-ROTACION-Y-CONTORNOS-test.jar'
notes='''BWE 0.14.0 - Rotacion, contorno real y suelo continuo

INSTALACION
Sustituir el JAR de BWE tanto en CLIENTE como en SERVIDOR y reiniciar. Mantener config/bwe y los respaldos. El protocolo del borde cambia a 3; ambas partes necesitan esta version.

USO
/bwe escanear -> /bwe listar -> Elegir -> /bwe rotar (opcional, repetir cuanto quieras) -> /bwe altura Y (opcional) -> alejarse -> /bwe crear.
Cada /bwe rotar gira 90 grados en sentido horario, conserva el punto elegido y la altura y actualiza dimensiones y contorno. Cuatro giros recuperan la orientacion inicial. Solo gira una seleccion, nunca una obra ya construida. Al elegir otra construccion se empieza en su orientacion original. La miniatura del catalogo sigue siendo la imagen original; el contorno en el mundo corresponde al giro actual.

CONTORNO
La pared verde sigue exactamente el perimetro horizontal usado para la limpieza y cimentacion, incluido el margen de un bloque y los patios. Se conserva su altura visual de 16 bloques. No es el perfil de cada casa ni representa cada tejado: es la envolvente real de terreno afectado, que tambien incluye espacios entre casas. Las obras aereas y acuaticas conservan su volumen rectangular de aire/agua. /bwe borde oculta o muestra.

SUELO
El aire sobrante del esquema ya no vuelve a excavar el relleno situado por debajo de su primer bloque real, ni los huecos exteriores sin bloques. Se mantienen los bloques decorativos originales, los interiores sobre sus pisos y las excepciones de agua/aire. No rellena automaticamente edificaciones ya creadas: el ajuste se aplica a las nuevas obras. /bwe deshacer sigue restaurando el ultimo respaldo; no se modifica su historial.

LISTADO
Better Village se muestra como BV; Grand Capitals como GC. Bioma, variantes, dimensiones y las 70 opciones se conservan. Las abreviaturas se aplican tambien al catalogo existente sin borrar la configuracion.

VERIFICACION
Compilacion Forge y pruebas nativas: comando de giro, cuatro giros, estados orientados, entidades y cuadros anclados, contorno irregular y paquete cliente, caminos junto al relleno, decoracion elevada, interior subterraneo y restauracion. Se ejecutan tambien las regresiones anteriores de altura, Lootr, Pirata y Dirigibles. No se ha probado visualmente dentro del modpack del usuario.

NOTAS ANTERIORES
'''
with zipfile.ZipFile(name,'w',zipfile.ZIP_DEFLATED) as z:
 for n in base.namelist():
  d=updates.pop(n,base.read(n))
  if n=='META-INF/mods.toml':d=d.replace(b'0.13.0',b'0.14.0')
  if n=='BWE-LEEME.txt':d=notes.encode()+base.read(n)
  z.writestr(n,d)
 for n,d in updates.items():z.writestr(n,d)
z=zipfile.ZipFile(name);assert z.testzip() is None
for n in base.namelist():
 if n.startswith('assets/'):assert base.read(n)==z.read(n),n
assert 'net/barttolo/bwe/RegressionGameTests.class' not in z.namelist()
assert b'0.14.0' in z.read('META-INF/mods.toml')
for cls in ['BarttoloWorldExpansion','BweCatalog','SurfaceBuilder','BweNetwork','BweBorderClient','VillageFootprint']:
 name1='net/barttolo/bwe/'+cls+'.class';assert z.read(name1)==engine.read(name1)
print('PASS',name,pathlib.Path(name).stat().st_size,'bytes; all catalogue assets preserved')
