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
    record PlacementRule(int surfaceY,String mode,boolean subterranean) {}
    private static final Map<String,PlacementRule> AUDITED_PLACEMENTS=loadPlacements();
    private static Map<String,PlacementRule> loadPlacements(){
        try(InputStream in=BweCatalog.class.getResourceAsStream("/assets/barttoloworldexpansion/idas-placement-v1.json")){
            if(in==null)return Map.of();
            var root=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,PlacementRule> result=new HashMap<>();
            for(var raw:root.getAsJsonArray("entries")){
                var item=raw.getAsJsonObject();String id=item.get("id").getAsString(),mode=item.get("placement").getAsString();int surface=item.get("surfaceY").getAsInt();
                if(!id.startsWith("bwe_idas:")||surface<0||surface>255||!Set.of("ground","water","air").contains(mode)||result.containsKey(id))throw new IOException("Referencia IDAS invalida: "+id);
                result.put(id,new PlacementRule(surface,mode,item.has("subterranean")&&item.get("subterranean").getAsBoolean()));
            }
            return Map.copyOf(result);
        }catch(Exception error){throw new IllegalStateException("No se pueden leer las referencias de altura IDAS",error);}
    }
    static Map<String,PlacementRule> auditedPlacements(){return AUDITED_PLACEMENTS;}
    private static final Map<String,StructureTemplate> CACHE=new HashMap<>();
    private static List<Entry> catalog;
    private static String problem="";
    private static final Map<String,String> PLACEMENT=new HashMap<>();
    private static Path folder() { return FMLPaths.CONFIGDIR.get().resolve("bwe"); }
    public static void reset() { catalog=null;CACHE.clear();PLACEMENT.clear();problem=""; }
    public static String problem() { entries();return problem; }
    public static List<Entry> entries() {
        if(catalog!=null) return catalog;
        try { bootstrap(); } catch(IOException error) { problem=error.getMessage();catalog=List.of();return catalog; }
        try {
            ArrayList<Entry> result=new ArrayList<>();
            for(String filename:List.of("catalogo-iv-v2.json","catalogo-extra-v1.json","catalogo-biomas-v1.json","catalogo-ampliacion-v1.json")) {
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
                if(o.has("placement"))PLACEMENT.put(o.get("id").getAsString(),o.get("placement").getAsString());
                result.add(new Entry(o.get("id").getAsString(),shortTitle(o.get("title").getAsString()),
                    placementDescription(o.get("id").getAsString(),o.get("description").getAsString()),x,y,z,o.get("ground").getAsInt(),List.copyOf(pieces)));
            }
            }
            catalog=List.copyOf(result);
        } catch(Exception error) { problem="Falta o no se puede leer config/bwe/catalogo.json: "+error.getMessage();catalog=List.of(); }
        return catalog;
    }
    /** Private deliverable may carry the exact user-provided archives as passive resources. */
    private static void bootstrap() throws IOException {
        Files.createDirectories(folder());
        for(String name:List.of("ctov.jar","explorify.jar","integrated_vanilla.jar","catalogo-iv-v2.json","bwe_extra.jar","catalogo-extra-v1.json","bwe_better.jar","bwe_capitals.jar","catalogo-biomas-v1.json","catalogo-ampliacion-v1.json","bwe_tt.jar","bwe_itp.jar","bwe_idas.jar")) {
            Path target=folder().resolve(name);
            if(Files.exists(target))continue;
            try(InputStream input=BweCatalog.class.getResourceAsStream("/assets/barttoloworldexpansion/"+name)) {
                if(input!=null)Files.copy(input,target);
            }
        }
    }
    /** The exact columns used by terrain preparation and by its visual preview. */
    public static Set<Long> terrain(BweCatalog.Entry e,BweCatalog.Blueprint b,int margin){
        Set<Long> result=new HashSet<>();
        if(BweCatalog.airship(e)||BweCatalog.pirate(e)){
            for(int x=-margin;x<e.sizeX()+margin;x++)for(int z=-margin;z<e.sizeZ()+margin;z++)result.add(VillageFootprint.key(x,z));
        }else{
            for(var c:b.cells())if(!c.state().isAir())for(int dx=-margin;dx<=margin;dx++)for(int dz=-margin;dz<=margin;dz++)result.add(VillageFootprint.key(c.pos().getX()+dx,c.pos().getZ()+dz));
            result=VillageFootprint.withCourtyards(result);
        }
        return Set.copyOf(result);
    }
    private static String placementDescription(String id,String description){
        var rule=AUDITED_PLACEMENTS.get(id);if(rule==null)return description;
        if(rule.mode().equals("air"))return description+" · Construccion flotante";
        if(rule.subterranean())return description+" · Entrada en superficie; niveles inferiores subterraneos";
        return description;
    }
    public static String shortTitle(String title){return title.replace("Better Village", "BV").replace("Grand Capitals", "GC");}
    public static Entry turned(Entry e){return new Entry(e.id(),e.title(),e.description(),e.sizeZ(),e.sizeY(),e.sizeX(),e.ground(),e.pieces());}
    /** Rotate the already selected plan, never reassemble it or resample its height. */
    public static Blueprint turn(Entry e,Blueprint b){
        int depth=e.sizeZ();List<Cell> cells=new ArrayList<>();List<Spawn> entities=new ArrayList<>();List<BlockPos> supports=new ArrayList<>();
        for(var c:b.cells())cells.add(new Cell(new BlockPos(depth-1-c.pos().getZ(),c.pos().getY(),c.pos().getX()),c.state().rotate(Rotation.CLOCKWISE_90),c.nbt()==null?null:c.nbt().copy()));
        for(var p:b.supports())supports.add(new BlockPos(depth-1-p.getZ(),p.getY(),p.getX()));
        for(var spawn:b.entities()){
            CompoundTag tag=spawn.nbt().copy();
            if(biomePack(e)&&tag.contains("TileX")){
                int x=tag.getInt("TileX"),z=tag.getInt("TileZ");tag.putInt("TileX",depth-1-z);tag.putInt("TileZ",x);
                if(tag.getString("id").equals("minecraft:painting"))tag.putByte("facing",(byte)Math.floorMod(tag.getByte("facing")+1,4));
                else if(tag.getString("id").endsWith("item_frame"))tag.putByte("Facing",(byte)Rotation.CLOCKWISE_90.rotate(net.minecraft.core.Direction.from3DDataValue(tag.getByte("Facing"))).get3DDataValue());
            }
            entities.add(new Spawn(tag,depth-spawn.z(),spawn.y(),spawn.x(),(spawn.yaw()+90)%360));
        }
        return new Blueprint(List.copyOf(cells),List.copyOf(entities),List.copyOf(supports),b.surfaceY());
    }
    public static boolean biomePack(Entry e) { return e.id().startsWith("bwe_better:")||e.id().startsWith("bwe_capitals:")||imported(e); }
    public static boolean pirate(Entry e) {
        var rule=AUDITED_PLACEMENTS.get(e.id());if(rule!=null)return rule.mode().equals("water");
        return e.id().equals("integrated_villages:aldea_pirate_village")||e.id().equals("bwe_idas:fishermans_lodge")||"water".equals(PLACEMENT.get(e.id()));
    }
    public static boolean airship(Entry e) {
        var rule=AUDITED_PLACEMENTS.get(e.id());if(rule!=null)return rule.mode().equals("air");
        return e.id().equals("integrated_villages:aldea_airship_village")||"air".equals(PLACEMENT.get(e.id()));
    }
    private static final Set<Block> ACTIVE_IMPORT_BLOCKS=Set.of(Blocks.SPAWNER,Blocks.COMMAND_BLOCK,Blocks.CHAIN_COMMAND_BLOCK,Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.NETHER_PORTAL,Blocks.END_PORTAL,Blocks.END_GATEWAY,Blocks.REDSTONE_WIRE,Blocks.REPEATER,
            Blocks.COMPARATOR,Blocks.PISTON_HEAD,Blocks.MOVING_PISTON);
    static BlockState passiveState(BlockState state){
        if(ACTIVE_IMPORT_BLOCKS.contains(state.getBlock()))return Blocks.AIR.defaultBlockState();
        if(state.is(Blocks.END_PORTAL_FRAME))return state.setValue(EndPortalFrameBlock.HAS_EYE,false);
        if(state.is(Blocks.TNT)||state.is(Blocks.REDSTONE_BLOCK))return Blocks.RED_TERRACOTTA.defaultBlockState();
        if(state.is(Blocks.OBSERVER)||state.is(Blocks.PISTON)||state.is(Blocks.STICKY_PISTON))return Blocks.STONE_BRICKS.defaultBlockState();
        return state;
    }
    // Stable corrections also cover servers retaining the original 0.15.0 catalogue JSON.
    static int surfaceReference(Entry e,int detected){
        var rule=AUDITED_PLACEMENTS.get(e.id());if(rule!=null)return rule.surfaceY();
        return switch(e.id()){
            case "bwe_idas:bazaar" -> 5; // paved market floor; upper connectors sit on the roof
            case "bwe_idas:iceandfire_dread_citadel" -> 0; // expose the complete lower fortress
            case "bwe_idas:fishermans_lodge" -> 17; // poles below, deck at 18, doors at 19
            default -> detected;
        };
    }
    public static boolean imported(Entry e){return e.id().startsWith("bwe_tt:")||e.id().startsWith("bwe_itp:")||e.id().startsWith("bwe_idas:");}
    public static String group(Entry e){
        return switch(e.id().split(":",2)[0]){
            case "bwe_tt" -> "TT";case "bwe_itp" -> "ITP";case "bwe_idas" -> "IDAS";
            case "bwe_capitals" -> "GC";case "bwe_better" -> "BV";case "integrated_villages" -> "IV";
            case "explorify" -> "EX";case "ctov" -> "CTOV";default -> "EXTRAS";
        };
    }
    public static boolean hostile(Entry e){
        String name=(e.id()+" "+e.description()).toLowerCase(Locale.ROOT);
        return name.contains("hostil")||name.contains("pillager")||name.contains("outpost")||name.contains("mansion_bottom")||name.contains("witch")||e.id().equals("bwe_capitals:hut");
    }
    public static List<Entry> category(String group){return entries().stream().filter(e->group.equals("HOSTILES")?hostile(e):group(e).equals(group)).toList();}
    public static Entry find(String id) { return entries().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null); }
    private static StructureTemplate template(ServerLevel world,String id) throws IOException {
        StructureTemplate cached=CACHE.get(id);if(cached!=null)return cached;
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null)throw new IOException("ID invalido: "+id);
        Path archive=folder().resolve(key.getNamespace().equals("integrated_villages")?"integrated_vanilla.jar":key.getNamespace()+".jar");
        StructureTemplate result;
        if(Files.isRegularFile(archive) && Set.of("ctov","explorify","integrated_villages","bwe_extra","bwe_better","bwe_capitals","bwe_tt","bwe_itp","bwe_idas").contains(key.getNamespace())) {
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
                if(imported(entry)){BlockState cleaned=passiveState(state);if(cleaned!=state){state=cleaned;data=null;}}
                if(state.is(Blocks.JIGSAW)) { state=finalState(data==null?"minecraft:air":data.getString("final_state"));data=null; }
                if(state.is(Blocks.STRUCTURE_BLOCK)||state.is(Blocks.STRUCTURE_VOID))continue;
                BlockPos pos=rotate(c.x(),c.y(),c.z(),p.rotation()).offset(p.x(),p.y(),p.z());
                if(pos.getX()<0||pos.getX()>=entry.sizeX()||pos.getY()<0||pos.getY()>=entry.sizeY()||pos.getZ()<0||pos.getZ()>=entry.sizeZ())
                    { if(state.isAir())continue;throw new IOException("Plan fuera de limites en "+entry.id()+": "+pos); }
                if(road&&Math.abs(pos.getY()-(entry.ground()-1))<=3&&!state.isAir()&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()
                    &&!state.is(net.minecraft.tags.BlockTags.LEAVES)&&!state.is(net.minecraft.tags.BlockTags.LOGS)
                    &&(state.is(Blocks.DIRT_PATH)||state.isSolidRender(world,BlockPos.ZERO)))
                    roadColumns.merge(((long)pos.getX()<<32)^(pos.getZ()&0xffffffffL),pos.getY(),Math::max);
                if(!imported(entry)&&npcPool.startsWith("minecraft:village/")&&npcPool.endsWith("/villagers")) {
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
            for(Tag raw:imported(entry)?new ListTag():saved.getList("entities",Tag.TAG_COMPOUND)) {
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
        if(entry.id().equals("bwe_capitals:mansion_bottom_front_left"))lightMansion(world,cells,entities);
        ArrayList<Cell> sorted=new ArrayList<>(cells.values());
        sorted.sort(Comparator.<Cell,Integer>comparing(c -> c.state().isAir()?0:1).thenComparingInt(c -> c.pos().getY()));
        int surface=entry.ground()-1,best=0;
        for(var h:roadHeights.entrySet())if(h.getValue()>best){surface=h.getKey();best=h.getValue();}
        // This fixed harbour has its docks at local Y=20 and its waterline at Y=19.
        if(entry.id().equals("integrated_villages:aldea_pirate_village"))surface=Math.max(0,entry.ground()-5);
        if(imported(entry)&&pirate(entry))surface=entry.ground()-1;
        // These cropped schematics start with above-ground details, not a complete soil layer.
        // Put a continuous ground layer below local Y=0 instead of through their first layer.
        if(Set.of("bwe_extra:mansion","bwe_extra:faro_realista","bwe_extra:coliseo_natural").contains(entry.id()))surface=-1;
        return new Blueprint(List.copyOf(sorted),List.copyOf(entities),List.copyOf(supports),surfaceReference(entry,surface));
    }

    /** Only the large hostile mansion: bound persistent entities and keep combat local. */
    static void lightMansion(ServerLevel world,Map<Long,Cell> cells,List<Spawn> entities){
        Map<String,Integer> limits=Map.of("minecraft:painting",24,"minecraft:item_frame",8,"minecraft:armor_stand",8);
        List<Spawn> source=new ArrayList<>(entities),kept=new ArrayList<>();
        // Deterministic spatial ordering and even sampling avoid keeping one crowded room.
        source.sort(Comparator.comparingDouble(Spawn::y).thenComparingDouble(Spawn::x).thenComparingDouble(Spawn::z));
        for(var limit:limits.entrySet()){
            List<Spawn> candidates=new ArrayList<>();Set<String> occupied=new HashSet<>();
            for(Spawn s:source)if(s.nbt().getString("id").equals(limit.getKey())){
                String key=(int)Math.floor(s.x()/4)+":"+(int)Math.floor(s.y()/4)+":"+(int)Math.floor(s.z()/4);
                if(occupied.add(key))candidates.add(s);
            }
            int count=Math.min(limit.getValue(),candidates.size());
            for(int i=0;i<count;i++){
                Spawn s=candidates.get(i*candidates.size()/count);CompoundTag tag=s.nbt().copy();
                tag.remove("Passengers");kept.add(new Spawn(tag,s.x(),s.y(),s.z(),s.yaw()));
            }
        }
        // Four widely separated spawners at existing pillager positions, only on real floors.
        List<BlockPos> generators=new ArrayList<>();
        for(Spawn s:source){
            if(generators.size()>=4)break;
            if(!s.nbt().getString("id").equals("minecraft:pillager"))continue;
            BlockPos pos=BlockPos.containing(s.x(),s.y(),s.z());
            Cell here=cells.get(pos.asLong()),below=cells.get(pos.below().asLong()),above=cells.get(pos.above().asLong());
            if(here==null||!here.state().isAir()||below==null||!below.state().isSolidRender(world,BlockPos.ZERO)||above==null||!above.state().isAir())continue;
            if(generators.stream().anyMatch(p->p.distSqr(pos)<24*24))continue;
            CompoundTag tag=new CompoundTag();tag.putString("id","minecraft:mob_spawner");
            tag.putShort("Delay",(short)600);tag.putShort("MinSpawnDelay",(short)600);tag.putShort("MaxSpawnDelay",(short)1200);
            tag.putShort("SpawnCount",(short)1);tag.putShort("MaxNearbyEntities",(short)2);
            tag.putShort("RequiredPlayerRange",(short)12);tag.putShort("SpawnRange",(short)3);
            CompoundTag data=new CompoundTag(),mob=new CompoundTag();mob.putString("id","minecraft:pillager");data.put("entity",mob);tag.put("SpawnData",data);
            cells.put(pos.asLong(),new Cell(pos,Blocks.SPAWNER.defaultBlockState(),tag));generators.add(pos);
        }
        entities.clear();entities.addAll(kept);
    }
}

