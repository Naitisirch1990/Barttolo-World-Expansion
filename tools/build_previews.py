"""Build small private top-down preview images from the supplied native NBT assets."""
import argparse, gzip, json, pathlib, sys, zipfile
from PIL import Image, ImageDraw
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from build_catalog import N

def tint(name):
    n=name.split(":",1)[-1]
    if any(x in n for x in ("water","ice","snow","packed_ice")): return (95,165,210)
    if any(x in n for x in ("leaf","leaves","moss","grass","vine","sapling","azalea")): return (92,145,76)
    if any(x in n for x in ("log","wood","plank","door","trapdoor","fence","sign","boat","barrel")): return (157,112,67)
    if any(x in n for x in ("terracotta","sandstone","sand","red_sand","mud_brick","brick")): return (190,147,91)
    if any(x in n for x in ("stone","cobble","deepslate","andesite","granite","diorite","basalt","ore")): return (117,123,126)
    if any(x in n for x in ("wool","carpet","banner","bed")): return (202,83,72)
    if any(x in n for x in ("glass","pane")): return (153,199,202)
    if any(x in n for x in ("torch","lantern","glowstone","sea_lantern","shroomlight")): return (229,195,93)
    if any(x in n for x in ("dirt","farmland","path","podzol","rooted_dirt","coarse_dirt")): return (135,101,69)
    if any(x in n for x in ("flower","crop","wheat","carrot","potato","beetroot","cactus","sugar_cane")): return (102,158,79)
    if any(x in n for x in ("iron","gold","copper","chain","anvil","cauldron")): return (165,157,143)
    return (160,155,143)

class Templates:
    def __init__(self, files):
        self.zips=[zipfile.ZipFile(p) for p in files]
        self.index={}
        for z in self.zips:
            for name in z.namelist():
                parts=name.split("/")
                if len(parts)>4 and parts[0]=="data" and parts[2] in ("structures","structure") and name.endswith(".nbt"):
                    ident=parts[1]+":"+name.split("/",3)[3][:-4]
                    if ident not in self.index or parts[2]=="structures": self.index[ident]=(z,name)
        self.cache={}
    def get(self, ident):
        if ident in self.cache:return self.cache[ident]
        z,n=self.index[ident]; data=N(gzip.decompress(z.read(n))).read(); self.cache[ident]=data;return data

def rotate(x,z,r):
    return ((x,z),(-z,x),(-x,-z),(z,-x))[r%4]

def render(entry,templates,dest):
    top={}
    for piece in entry["pieces"]:
        data=templates.get(piece["template"]); palette=data.get("palette",data.get("palettes",[[]])[0])
        for block in data["blocks"]:
            state=palette[block["state"]]; name=state["Name"]
            if name=="minecraft:jigsaw":
                name=block.get("nbt",{}).get("final_state","minecraft:air").split("[",1)[0]
            if name in ("minecraft:air","minecraft:cave_air","minecraft:void_air","minecraft:structure_void","minecraft:structure_block","minecraft:jigsaw"): continue
            x,y,z=block["pos"];rx,rz=rotate(x,z,piece["rotation"])
            px,pz=rx+piece["x"],rz+piece["z"]; py=y+piece["y"]
            key=(px,pz)
            if key not in top or py>=top[key][0]: top[key]=(py,name)
    width,height=256,174; image=Image.new("RGBA",(width,height),(19,27,32,255)); draw=ImageDraw.Draw(image)
    spanX=max(1,entry["sizeX"]);spanZ=max(1,entry["sizeZ"])
    scale=min(224/spanX,136/spanZ)
    drawW=spanX*scale;drawH=spanZ*scale; ox=(width-drawW)/2;oz=(height-drawH)/2
    for x in range(spanX):
        for z in range(spanZ):
            if (x+z)%2==0:
                a=ox+x*scale;b=oz+z*scale
                draw.rectangle((a,b,a+max(1,scale),b+max(1,scale)),fill=(29,39,43,255))
    for (x,z),(y,name) in sorted(top.items(),key=lambda kv:(kv[0][0]+kv[0][1],kv[0][0])):
        if not (0<=x<spanX and 0<=z<spanZ):continue
        color=tint(name); heightShade=max(-22,min(22,y//3)); color=tuple(max(0,min(255,c+heightShade)) for c in color)
        a=ox+x*scale;b=oz+z*scale
        draw.rectangle((a,b,a+max(1,scale),b+max(1,scale)),fill=(*color,255))
        if scale>=4: draw.rectangle((a,b,a+scale,b+1),fill=tuple(min(255,c+18) for c in color)+(255,))
    draw.rectangle((ox,oz,ox+drawW,oz+drawH),outline=(116,137,145,255),width=1)
    image.save(dest,optimize=True)

def main():
    ap=argparse.ArgumentParser();ap.add_argument("ctov");ap.add_argument("explorify");ap.add_argument("catalog");ap.add_argument("output");a=ap.parse_args()
    templates=Templates([a.ctov,a.explorify]); root=pathlib.Path(a.output);root.mkdir(parents=True,exist_ok=True)
    data=json.loads(pathlib.Path(a.catalog).read_text())
    for entry in data["entries"]:
        name=entry["id"].lower().replace(":","_").replace("/","_")
        render(entry,templates,root/(name+".png"))
    print(f"Generadas {len(data['entries'])} vistas previas en {root}")

if __name__=="__main__":main()
