package net.barttolo.bwe;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Manual-only source archives. Never registers their worldgen or loads their mod code. */
public final class BweCatalog {
    public record Piece(String template,int x,int y,int z,int rotation) {}
    public record Entry(String id,String title,String description,int sizeX,int sizeY,int sizeZ,
                        int ground,List<Piece> pieces) {}
    public record Cell(BlockPos pos,BlockState state,CompoundTag nbt) {}
    public record Spawn(CompoundTag nbt,double x,double y,double z,float yaw) {}
    public record Blueprint(List<Cell> cells,List<Spawn> entities,List<BlockPos> supports,int surfaceY) {}
    private static final Map<String,StructureTemplate> CACHE=new HashMap<>();
    private static List<Entry> catalog;
    private static String problem="";
    private static Path folder() { return FMLPaths.CONFIGDIR.get().resolve("bwe"); }
    public static void reset() { catalog=null;CACHE.clear();problem=""; }
    public static String problem() { entries();return problem; }
    public static List<Entry> entries() {
        if(catalog!=null) return catalog;
        try { bootstrap(); } catch(IOException error) { problem=error.getMessage();catalog=List.of();return catalog; }
        try {
            ArrayList<Entry> result=new ArrayList<>();
            for(String filename:List.of("catalogo-iv-v2.json","catalogo-extra-v1.json","catalogo-biomas-v1.json")) {
            Path file=folder().resolve(filename);if(!Files.isRegularFile(file))continue;
            JsonObject root;try(var reader=Files.newBufferedReader(file)){root=JsonParser.parseReader(reader).getAsJsonObject();}
            for(var item:root.getAsJsonArray("entries")) {
                JsonObject o=item.getAsJsonObject();ArrayList<Piece> pieces=new ArrayList<>();
                for(var value:o.getAsJsonArray("pieces")) {
                    var p=value.getAsJsonObject();pieces.add(new Piece(p.get("template").getAsString(),
                        p.get("x").getAsInt(),p.get("y").getAsInt(),p.get("z").getAsInt(),p.get("rotation").getAsInt()));
                }
                int x=o.get("sizeX").getAsInt(),y=o.get("sizeY").getAsInt(),z=o.get("sizeZ").getAsInt();
                if(x<1||z<1||y<1||x>256||z>256||y>256||pieces.size()>128)
                    throw new IllegalArgumentException("Tamano fuera del limite: "+o.get("id"));
                result.add(new Entry(o.get("id").getAsString(),o.get("title").getAsString(),
                    o.get("description").getAsString(),x,y,z,o.get("ground").getAsInt(),List.copyOf(pieces)));
            }
            }
            catalog=List.copyOf(result);
        } catch(Exception error) { problem="Falta o no se puede leer config/bwe/catalogo.json: "+error.getMessage();catalog=List.of(); }
        return catalog;
    }
    /** Private deliverable may carry the exact user-provided archives as passive resources. */
    private static void bootstrap() throws IOException {
        Files.createDirectories(folder());
        for(String name:List.of("ctov.jar","explorify.jar","integrated_vanilla.jar","catalogo-iv-v2.json","bwe_extra.jar","catalogo-extra-v1.json","bwe_better.jar","bwe_capitals.jar","catalogo-biomas-v1.json")) {
            Path target=folder().resolve(name);
            if(Files.exists(target))continue;
            try(InputStream input=BweCatalog.class.getResourceAsStream("/assets/barttoloworldexpansion/"+name)) {
                if(input!=null)Files.copy(input,target);
            }
        }
    }
    public static boolean biomePack(Entry e) { return e.id().startsWith("bwe_better:")||e.id().startsWith("bwe_capitals:"); }
    public static boolean pirate(Entry e) { return e.id().equals("integrated_villages:aldea_pirate_village"); }
    public static boolean airship(Entry e) { return e.id().equals("integrated_villages:aldea_airship_village"); }
    public static Entry find(String id) { return entries().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null); }
    private static StructureTemplate template(ServerLevel world,String id) throws IOException {
        StructureTemplate cached=CACHE.get(id);if(cached!=null)return cached;
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null)throw new IOException("ID invalido: "+id);
        Path archive=folder().resolve(key.getNamespace().equals("integrated_villages")?"integrated_vanilla.jar":key.getNamespace()+".jar");
        StructureTemplate result;
        if(Files.isRegularFile(archive) && Set.of("ctov","explorify","integrated_villages","bwe_extra","bwe_better","bwe_capitals").contains(key.getNamespace())) {
            try(ZipFile zip=new ZipFile(archive.toFile())) {
                String base="data/"+key.getNamespace()+"/";
                var entry=zip.getEntry(base+"structures/"+key.getPath()+".nbt");
                if(entry==null)entry=zip.getEntry(base+"structure/"+key.getPath()+".nbt");
                if(entry==null)throw new IOException("Plantilla no encontrada: "+id);
                try(InputStream input=zip.getInputStream(entry)) {
                    CompoundTag tag=NbtIo.readCompressed(input);
                    TreeSet<String> missing=new TreeSet<>();
                    ListTag palette=tag.getList("palette",Tag.TAG_COMPOUND);
                    for(Tag raw:palette){String block=((CompoundTag)raw).getString("Name");ResourceLocation bid=ResourceLocation.tryParse(block);if(bid==null||!BuiltInRegistries.BLOCK.containsKey(bid))missing.add(block);}
                    if(!missing.isEmpty())throw new IOException("Faltan bloques requeridos por esta aldea: "+String.join(", ",missing.stream().limit(6).toList())+". Instala sus mods antes de construir; terreno intacto.");
                    result=new StructureTemplate();result.load(BuiltInRegistries.BLOCK.asLookup(),tag);
                }
            }
        } else result=world.getStructureManager().get(key)
            .orElseThrow(() -> new IOException("Falta config/bwe/"+key.getNamespace()+".jar para "+id));
        CACHE.put(id,result);return result;
    }
    public static BlockPos rotate(int x,int y,int z,int r) {
        return switch(Math.floorMod(r,4)) {
            case 1 -> new BlockPos(-z,y,x);case 2 -> new BlockPos(-x,y,-z);
            case 3 -> new BlockPos(z,y,-x);default -> new BlockPos(x,y,z);
        };
    }
    private static BlockState finalState(String value) {
        String id=value.split("\\[",2)[0];ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.BLOCK.containsKey(key))return Blocks.AIR.defaultBlockState();
        BlockState state=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        if(value.contains("[")) {
            String props=value.substring(value.indexOf('[')+1).replace("]","");
            for(String pair:props.split(",")) {
                String[] kv=pair.split("=",2);if(kv.length!=2)continue;
                var property=state.getBlock().getStateDefinition().getProperty(kv[0]);
                if(property!=null)state=apply(state,property,kv[1]);
            }
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,
        net.minecraft.world.level.block.state.properties.Property<T> p,String value) {
        return p.getValue(value).map(v -> state.setValue(p,v)).orElse(state);
    }
    public static Blueprint compose(ServerLevel world,Entry entry) throws IOException {
        LinkedHashMap<Long,Cell> cells=new LinkedHashMap<>();ArrayList<Spawn> entities=new ArrayList<>();Set<BlockPos> supports=new HashSet<>();
        Map<Integer,Integer> roadHeights=new TreeMap<>();
        Rotation[] rotations={Rotation.NONE,Rotation.CLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.COUNTERCLOCKWISE_90};
        for(Piece p:entry.pieces()) {
            boolean road=!entry.id().contains("airship_village")&&(p.template().contains("/path/")||p.template().contains("/roads/")||p.template().contains("/docks/")||p.template().contains("/streets/")||p.template().contains("/road/")||p.template().endsWith("/main_road"));
            Map<Long,Integer> roadColumns=new HashMap<>();
            StructureTemplate template=template(world,p.template());
            var blueprint=TemplateNbtPiece.readForConstruction(template);
            CompoundTag saved=template.save(new CompoundTag());Map<Long,CompoundTag> metadata=new HashMap<>();
            for(Tag raw:saved.getList("blocks",Tag.TAG_COMPOUND)) {
                CompoundTag b=(CompoundTag)raw;ListTag pos=b.getList("pos",Tag.TAG_INT);
                if(b.contains("nbt",Tag.TAG_COMPOUND))metadata.put(BlockPos.asLong(pos.getInt(0),pos.getInt(1),pos.getInt(2)),b.getCompound("nbt"));
            }
            for(var c:blueprint.cells()) {
                CompoundTag data=metadata.get(BlockPos.asLong(c.x(),c.y(),c.z()));BlockState state=c.state();
                String npcPool=state.is(Blocks.JIGSAW)&&data!=null?data.getString("pool"):"";
                if(state.is(Blocks.STRUCTURE_VOID))continue;
                if(state.is(Blocks.JIGSAW)) { state=finalState(data==null?"minecraft:air":data.getString("final_state"));data=null; }
                if(state.is(Blocks.STRUCTURE_BLOCK)||state.is(Blocks.STRUCTURE_VOID))continue;
                BlockPos pos=rotate(c.x(),c.y(),c.z(),p.rotation()).offset(p.x(),p.y(),p.z());
                if(pos.getX()<0||pos.getX()>=entry.sizeX()||pos.getY()<0||pos.getY()>=entry.sizeY()||pos.getZ()<0||pos.getZ()>=entry.sizeZ())
                    { if(state.isAir())continue;throw new IOException("Plan fuera de limites en "+entry.id()+": "+pos); }
                if(road&&Math.abs(pos.getY()-(entry.ground()-1))<=3&&!state.isAir()&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()
                    &&!state.is(net.minecraft.tags.BlockTags.LEAVES)&&!state.is(net.minecraft.tags.BlockTags.LOGS)
                    &&(state.is(Blocks.DIRT_PATH)||state.isSolidRender(world,BlockPos.ZERO)))
                    roadColumns.merge(((long)pos.getX()<<32)^(pos.getZ()&0xffffffffL),pos.getY(),Math::max);
                if(npcPool.startsWith("minecraft:village/")&&npcPool.endsWith("/villagers")) {
                    CompoundTag villager=new CompoundTag();villager.putString("id","minecraft:villager");
                    entities.add(new Spawn(villager,pos.getX()+0.5,pos.getY()+1.0,pos.getZ()+0.5,0));
                }
                if(c.y()==0 && !state.isAir() && state.getFluidState().isEmpty()
                    && !state.canBeReplaced() && !state.hasBlockEntity()
                    && !state.is(net.minecraft.tags.BlockTags.LEAVES)) supports.add(pos);
                if(biomePack(entry)&&state.isAir()&&cells.containsKey(pos.asLong())&&!cells.get(pos.asLong()).state().isAir())continue;
                cells.put(pos.asLong(),new Cell(pos,state.rotate(rotations[Math.floorMod(p.rotation(),4)]),data==null?null:data.copy()));
                if(cells.size()>1_000_000)throw new IOException("Demasiados bloques en una construccion");
            }
            for(int height:roadColumns.values())roadHeights.merge(height,1,Integer::sum);
            for(Tag raw:saved.getList("entities",Tag.TAG_COMPOUND)) {
                CompoundTag e=(CompoundTag)raw;ListTag pos=e.getList("pos",Tag.TAG_DOUBLE);
                double x=pos.getDouble(0),y=pos.getDouble(1),z=pos.getDouble(2);
                double edge=biomePack(entry)?1.0:0.0;
                double rx=switch(Math.floorMod(p.rotation(),4)){case 1->edge-z;case 2->edge-x;case 3->z;default->x;};
                double rz=switch(Math.floorMod(p.rotation(),4)){case 1->x;case 2->edge-z;case 3->edge-x;default->z;};
                CompoundTag entityTag=e.getCompound("nbt").copy();
                if(biomePack(entry)&&entityTag.contains("TileX")){
                    BlockPos anchor=rotate(entityTag.getInt("TileX"),entityTag.getInt("TileY"),entityTag.getInt("TileZ"),p.rotation()).offset(p.x(),p.y(),p.z());
                    entityTag.putInt("TileX",anchor.getX());entityTag.putInt("TileY",anchor.getY());entityTag.putInt("TileZ",anchor.getZ());
                    if(entityTag.getString("id").equals("minecraft:painting"))entityTag.putByte("facing",(byte)Math.floorMod(entityTag.getByte("facing")+p.rotation(),4));
                    else if(entityTag.getString("id").endsWith("item_frame"))entityTag.putByte("Facing",(byte)rotations[Math.floorMod(p.rotation(),4)].rotate(net.minecraft.core.Direction.from3DDataValue(entityTag.getByte("Facing"))).get3DDataValue());
                }
                entities.add(new Spawn(entityTag,rx+p.x(),y+p.y(),rz+p.z(),90*p.rotation()));
            }
        }
        ArrayList<Cell> sorted=new ArrayList<>(cells.values());
        sorted.sort(Comparator.<Cell,Integer>comparing(c -> c.state().isAir()?0:1).thenComparingInt(c -> c.pos().getY()));
        int surface=entry.ground()-1,best=0;
        for(var h:roadHeights.entrySet())if(h.getValue()>best){surface=h.getKey();best=h.getValue();}
        // This fixed harbour has its docks at local Y=20 and its waterline at Y=19.
        if(pirate(entry))surface=Math.max(0,entry.ground()-5);
        return new Blueprint(List.copyOf(sorted),List.copyOf(entities),List.copyOf(supports),surface);
    }
}

