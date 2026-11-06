package net.barttolo.bwe;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Manual surface construction. No audit commands or mandatory rollback snapshots. */
public final class SurfaceBuilder {
    private static final int BUDGET=200,FLAGS=2|16|32;
    private static Job active,last;
    private enum Stage { VALIDAR,VALIDAR_APOYOS,LIMPIEZA,CIMENTACION,APOYOS,CONSTRUCCION,DETALLES,AGUA,LOOTR,POBLACION }
    private static final class Job {
        final ServerLevel world;final BweCatalog.Entry entry;final BweCatalog.Blueprint blueprint;
        final boolean aerial,aquatic;final BitSet water,dry;final int waterWidth,waterDepth,waterHeight;final int clearBottom,clearTop;final BlockPos origin;final int walkY,x0,z0,x1,z1;final UUID operator;final AABB area;final Set<Long> footprint=new HashSet<>();final Set<Long> placementFootprint;
        int x,z,y=Integer.MIN_VALUE,cursor,done;Stage stage=Stage.VALIDAR;String waiting="";int converted;final List<net.minecraft.resources.ResourceLocation> lootTables;final List<BlockPos> chests;
        Job(ServerLevel w,BweCatalog.Entry e,BweCatalog.Blueprint b,BlockPos center,int ground,int margin,UUID op) {
            world=w;entry=e;blueprint=b;walkY=ground;operator=op;aerial=BweCatalog.airship(e);aquatic=BweCatalog.pirate(e);
            origin=new BlockPos(center.getX()-e.sizeX()/2,originY(e,b,ground),center.getZ()-e.sizeZ()/2);
            clearBottom=aerial?origin.getY()-4:aquatic?origin.getY()-2:walkY;clearTop=(aerial||aquatic)?origin.getY()+e.sizeY()+3:Math.max(origin.getY()+e.sizeY()+1,walkY+64);
            lootTables=BweLootr.available()?BweLootr.tables(w):List.of();
            chests=b.cells().stream().filter(c->c.state().is(Blocks.CHEST)||c.state().is(Blocks.TRAPPED_CHEST)).map(c->origin.offset(c.pos())).toList();
            x0=origin.getX()-margin;z0=origin.getZ()-margin;x1=origin.getX()+e.sizeX()-1+margin;z1=origin.getZ()+e.sizeZ()-1+margin;
            for(var c:b.cells())if(!c.state().isAir()){
                int px=origin.getX()+c.pos().getX(),pz=origin.getZ()+c.pos().getZ();
                for(int dx=-margin;dx<=margin;dx++)for(int dz=-margin;dz<=margin;dz++)footprint.add(columnKey(px+dx,pz+dz));
            }
            if(aerial||aquatic)for(int px=x0;px<=x1;px++)for(int pz=z0;pz<=z1;pz++)footprint.add(columnKey(px,pz));
            waterWidth=x1-x0+1;waterDepth=z1-z0+1;waterHeight=aquatic?walkY-clearBottom:0;
            if(aquatic){
                BitSet blocked=new BitSet(waterWidth*waterDepth*waterHeight);
                for(var c:b.cells())if(!c.state().isAir()){
                    BlockPos at=origin.offset(c.pos());int wy=at.getY()-clearBottom;
                    if(wy>=0&&wy<waterHeight)blocked.set((wy*waterDepth+at.getZ()-z0)*waterWidth+at.getX()-x0);
                }
                water=WaterEnvelope.exterior(waterWidth,waterHeight,waterDepth,blocked);
                dry=new BitSet();dry.set(0,waterWidth*waterDepth*waterHeight);dry.andNot(blocked);dry.andNot(water);
            }else {water=new BitSet();dry=new BitSet();}
            placementFootprint=Set.copyOf(footprint);
            Set<Long> interior=VillageFootprint.withCourtyards(footprint);footprint.addAll(interior);
            x=x0;z=z0;area=new AABB(x0,(aerial||aquatic)?clearBottom:w.getMinBuildHeight(),z0,x1+1.0,(aerial||aquatic)?clearTop+1:w.getMaxBuildHeight(),z1+1.0);
        }
        void column() {y=Integer.MIN_VALUE;if(++z>z1){z=z0;x++;}}
    }
    public static boolean busy(){return active!=null||BweBackup.restoring();}
    public static void reset(){active=null;last=null;BweBackup.close();}
    public static String status(){return BweBackup.restoring()?BweBackup.status():active==null?"Sin obra en curso.":active.entry.title()+" | "+active.stage+" | "+active.done+" operaciones"+(active.waiting.isEmpty()?"":" | "+active.waiting);}
    private static void say(Job j,String s){var p=j.world.getServer().getPlayerList().getPlayer(j.operator);if(p!=null)p.sendSystemMessage(Component.literal("[BWE] "+s));}
    public static int ground(ServerLevel world,BlockPos center,BweCatalog.Entry e,int margin) {
        int x0=center.getX()-e.sizeX()/2-margin,z0=center.getZ()-e.sizeZ()/2-margin;
        int x1=x0+e.sizeX()-1+2*margin,z1=z0+e.sizeZ()-1+2*margin;
        checkArea(world,x0,z0,x1,z1);
        if(BweCatalog.pirate(e)){
            Map<Integer,Integer> levels=new TreeMap<>();
            // Read actual water blocks near the selected site, including water under docks/trees.
            // A heightmap can point at a roof or remain above recently removed terrain.
            int high=Math.min(world.getMaxBuildHeight()-2,center.getY()+16),low=Math.max(world.getMinBuildHeight(),center.getY()-96);
            for(int x=x0;x<=x1;x+=Math.max(1,(x1-x0)/16))for(int z=z0;z<=z1;z+=Math.max(1,(z1-z0)/16)){
                for(int y=high;y>=low;y--){
                    BlockPos at=new BlockPos(x,y,z);
                    if(world.getBlockState(at).is(Blocks.WATER)&&world.getFluidState(at).isSource()
                        &&!world.getFluidState(at.above()).is(net.minecraft.tags.FluidTags.WATER)
                        &&(world.getBlockState(at.above()).isAir()||world.getBlockState(at.above()).canBeReplaced())){
                        levels.merge(y,1,Integer::sum);break;
                    }
                }
            }
            if(!levels.isEmpty())return levels.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey()+1;
        }
        if(BweCatalog.airship(e)){
            int top=world.getMinBuildHeight();
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)top=Math.max(top,world.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));
            // At least six air blocks between the existing surface and the lowest ship block.
            return Math.max(center.getY(),top+6)+1;
        }
        ArrayList<Integer> heights=new ArrayList<>();
        for(int x=x0;x<=x1;x+=Math.max(1,(x1-x0)/8))for(int z=z0;z<=z1;z+=Math.max(1,(z1-z0)/8)){
            int y=WorldChecks.ground(world,x,z);
            if(y==Integer.MIN_VALUE)y=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
            heights.add(y+1);
        }
        Collections.sort(heights);return heights.get(heights.size()/2);
    }
    private static void checkArea(ServerLevel world,int x0,int z0,int x1,int z1) {
        for(int cx=x0>>4;cx<=x1>>4;cx++)for(int cz=z0>>4;cz<=z1>>4;cz++)
            if(!world.hasChunk(cx,cz))throw new IllegalArgumentException("Acercate al lugar: hay chunks sin cargar");
        for(int x:new int[]{x0,x1})for(int z:new int[]{z0,z1})
            if(!world.getWorldBorder().isWithinBounds(new BlockPos(x,world.getMinBuildHeight(),z)))
                throw new IllegalArgumentException("La obra sale del borde del mundo");
        String dim=world.dimension().location().toString();var d=WorldData.get(world);
        if(d.protectedFootprint(dim,x0,z0,x1,z1,0))throw new IllegalArgumentException("Zona protegida expresamente con BWE");
    }
    static int originY(BweCatalog.Entry e,BweCatalog.Blueprint b,int ground) {
        return ground-1-(BweCatalog.airship(e)?0:b.surfaceY());
    }
    public static String heightProblem(ServerLevel world,BweCatalog.Entry e,BweCatalog.Blueprint b,int ground) {
        int origin=originY(e,b,ground);boolean aerial=BweCatalog.airship(e),aquatic=BweCatalog.pirate(e);
        int bottom=aerial?origin-4:aquatic?origin-2:origin,top=(aerial||aquatic)?origin+e.sizeY()+4:origin+e.sizeY();
        return bottom<world.getMinBuildHeight()||top>world.getMaxBuildHeight()?"La estructura y su espacio de limpieza no caben a esta altura":null;
    }
    public static String start(ServerLevel world,BweCatalog.Entry e,BlockPos center,int ground,int margin,UUID op) {
        if(busy()||BuildEngine.busy())return "Ya hay una obra en curso";
        try{return start(world,e,BweCatalog.compose(world,e),center,ground,margin,op);}catch(Exception error){return error.getMessage();}
    }
    public static String start(ServerLevel world,BweCatalog.Entry e,BweCatalog.Blueprint blueprint,BlockPos center,int ground,int margin,UUID op) {
        if(busy()||BuildEngine.busy())return "Ya hay una obra en curso";
        try {
            String height=heightProblem(world,e,blueprint,ground);if(height!=null)return height;
            Job j=new Job(world,e,blueprint,center,ground,margin,op);
            checkArea(world,j.x0,j.z0,j.x1,j.z1);
            if(((j.aerial||j.aquatic)?j.clearBottom:j.origin.getY())<world.getMinBuildHeight()||((j.aerial||j.aquatic)?j.clearTop+1:j.origin.getY()+e.sizeY())>world.getMaxBuildHeight())return "La estructura no cabe a esta altura";
            active=j;return null;
        }catch(Exception error){return error.getMessage();}
    }
    public static String restoreTerrain(ServerLevel world){return restoreTerrain(world,null);}
    public static String restoreTerrain(ServerLevel world,UUID player){
        if(BuildEngine.busy())return "Espera a que termine la otra obra";
        if(active!=null&&active.world!=world)return "Vuelve a la dimension de la obra";
        if(active!=null&&(active.stage==Stage.VALIDAR||active.stage==Stage.VALIDAR_APOYOS)){say(active,"Obra cancelada antes de modificar el terreno");active=null;return null;}
        String error=BweBackup.restore(world,player);
        if(error==null){active=null;last=null;}
        return error;
    }
    public static String remove(ServerLevel world){return restoreTerrain(world);}
    private static long columnKey(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static boolean affected(Job j,int x,int z){return j.footprint.contains(columnKey(x,z));}
    private static boolean purge(Job j){
        var es=j.world.getEntitiesOfClass(Entity.class,j.area).stream().filter(e->affected(j,e.blockPosition().getX(),e.blockPosition().getZ())).toList();
        if(es.stream().anyMatch(e->e instanceof Player)){j.waiting="Alejate horizontalmente del area";return false;}
        int count=0;for(var e:es){if(e instanceof Player)continue;if(count++>=256){j.waiting="Retirando entidades";return false;}e.discard();}
        j.waiting="";return true;
    }
    private static void set(Job j,BlockPos at,net.minecraft.world.level.block.state.BlockState state){
        if(!j.world.hasChunk(at.getX()>>4,at.getZ()>>4))throw new IllegalArgumentException("Chunk descargado: acercate sin entrar en la obra");
        var old=j.world.getBlockState(at);
        if(!old.equals(state)||old.hasBlockEntity()){
            try{BweBackup.capture(j.world,at);}catch(java.io.IOException error){throw new IllegalStateException("No se pudo guardar el respaldo",error);}
            j.world.removeBlockEntity(at);
            if(old.equals(state)&&old.hasBlockEntity())j.world.setBlock(at,Blocks.AIR.defaultBlockState(),FLAGS);
            j.world.setBlock(at,state,FLAGS);
        }
    }
    private static boolean firm(net.minecraft.world.level.block.state.BlockState state){
        return !state.isAir()&&state.getFluidState().isEmpty()&&!state.canBeReplaced()
            &&!state.is(net.minecraft.tags.BlockTags.LEAVES)&&!state.is(net.minecraft.tags.BlockTags.LOGS);
    }
    private static int baseBottom(Job j){return Math.max(j.world.getMinBuildHeight(),Math.min(j.walkY,j.origin.getY())-8);}
    private static void requireSupport(Job j,int x,int top,int z){
        if(x<j.x0||x>j.x1||z<j.z0||z>j.z1||top<j.world.getMinBuildHeight()||top>=j.world.getMaxBuildHeight())
            throw new IllegalArgumentException("La base calculada queda fuera del area o de la altura del mundo; no se ha modificado el terreno");
    }
    public static void tick(){
        if(BweBackup.restoring()){BweBackup.tick();return;}
        Job j=active;if(j==null)return;
        try{
            checkArea(j.world,j.x0,j.z0,j.x1,j.z1);
            // Purge before creating the new population, otherwise we'd discard our own villagers.
            if(j.stage!=Stage.VALIDAR&&j.stage!=Stage.VALIDAR_APOYOS&&j.stage!=Stage.POBLACION&&!purge(j))return;
            int work=0;
            while(active==j&&work++<BUDGET){
                if(j.stage==Stage.VALIDAR){
                    if(j.x>j.x1){j.stage=Stage.VALIDAR_APOYOS;j.cursor=0;continue;}
                    requireSupport(j,j.x,j.walkY-1,j.z);
                    j.column();
                }else if(j.stage==Stage.VALIDAR_APOYOS){
                    if(j.aerial||j.aquatic||j.cursor>=j.blueprint.supports().size()){BweBackup.begin(j.world);j.stage=Stage.LIMPIEZA;last=j;j.x=j.x0;j.z=j.z0;j.y=Integer.MIN_VALUE;continue;}
                    BlockPos floor=j.origin.offset(j.blueprint.supports().get(j.cursor++));
                    if(floor.getY()>=j.walkY&&floor.getY()-j.walkY<=63)continue;
                    requireSupport(j,floor.getX(),floor.getY()-1,floor.getZ());
                }else if(j.stage==Stage.LIMPIEZA){
                    if(j.x>j.x1){
                        j.x=j.x0;j.z=j.z0;j.y=Integer.MIN_VALUE;j.stage=(j.aerial||j.aquatic)?Stage.CONSTRUCCION:Stage.CIMENTACION;j.cursor=0;continue;
                    }
                    if(!affected(j,j.x,j.z)){j.column();continue;}
                    if(j.y==Integer.MIN_VALUE)j.y=Math.min(j.world.getHeight(Heightmap.Types.WORLD_SURFACE,j.x,j.z)-1,j.clearTop);
                    int skipped=0;
                    while(j.y>=j.clearBottom&&skipped<32&&j.world.getBlockState(new BlockPos(j.x,j.y,j.z)).isAir()){j.y--;skipped++;}
                    if(j.y<j.clearBottom){j.column();continue;}
                    if(skipped==32)continue;
                    set(j,new BlockPos(j.x,j.y--,j.z),Blocks.AIR.defaultBlockState());
                }else if(j.stage==Stage.CIMENTACION){
                    if(j.x>j.x1){j.stage=Stage.APOYOS;j.cursor=0;j.y=Integer.MIN_VALUE;continue;}
                    if(!affected(j,j.x,j.z)){j.column();continue;}
                    if(j.y==Integer.MIN_VALUE)j.y=j.walkY-1;
                    if(j.y<baseBottom(j)){j.column();continue;}
                    BlockPos at=new BlockPos(j.x,j.y,j.z);var old=j.world.getBlockState(at);
                    boolean support=firm(old);
                    if(j.y==j.walkY-1||!support)set(j,at,(j.y==j.walkY-1?Blocks.GRASS_BLOCK:j.y>=j.walkY-3?Blocks.DIRT:Blocks.STONE).defaultBlockState());
                    if(support)j.column();else j.y--;
                }else if(j.stage==Stage.APOYOS){
                    if(j.cursor>=j.blueprint.supports().size()){j.stage=Stage.CONSTRUCCION;j.cursor=0;continue;}
                    BlockPos floor=j.origin.offset(j.blueprint.supports().get(j.cursor));
                    if(j.y==Integer.MIN_VALUE)j.y=floor.getY()-1;
                    if(j.y<baseBottom(j)){j.cursor++;j.y=Integer.MIN_VALUE;continue;}
                    BlockPos at=new BlockPos(floor.getX(),j.y,floor.getZ());var old=j.world.getBlockState(at);
                    if(firm(old)) {j.cursor++;j.y=Integer.MIN_VALUE;}
                    else {set(j,at,Blocks.DIRT.defaultBlockState());j.y--;}
                }else if(j.stage==Stage.CONSTRUCCION){
                    if(j.cursor>=j.blueprint.cells().size()){j.stage=Stage.DETALLES;j.cursor=0;continue;}
                    var cell=j.blueprint.cells().get(j.cursor++);BlockPos at=j.origin.offset(cell.pos());if(!cell.state().isAir()||j.placementFootprint.contains(columnKey(at.getX(),at.getZ())))set(j,at,cell.state());
                }else if(j.stage==Stage.DETALLES){
                    if(j.cursor>=j.blueprint.cells().size()){j.stage=j.aquatic?Stage.AGUA:Stage.LOOTR;j.cursor=0;continue;}
                    var cell=j.blueprint.cells().get(j.cursor++);
                    if(cell.nbt()!=null){BlockPos at=j.origin.offset(cell.pos());var be=j.world.getBlockEntity(at);
                        if(be!=null){CompoundTag tag=cell.nbt().copy();tag.putInt("x",at.getX());tag.putInt("y",at.getY());tag.putInt("z",at.getZ());be.load(tag);be.setChanged();}}
                }else if(j.stage==Stage.AGUA){
                    int wet=j.water.nextSetBit(j.cursor),empty=j.dry.nextSetBit(j.cursor);
                    int i=wet<0?empty:empty<0?wet:Math.min(wet,empty);
                    if(i<0){j.stage=Stage.LOOTR;j.cursor=0;continue;}
                    j.cursor=i+1;int wx=i%j.waterWidth,wz=(i/j.waterWidth)%j.waterDepth,wy=i/(j.waterWidth*j.waterDepth);
                    set(j,new BlockPos(j.x0+wx,j.clearBottom+wy,j.z0+wz),(j.dry.get(i)?Blocks.AIR:Blocks.WATER).defaultBlockState());
                }else if(j.stage==Stage.LOOTR){
                    if(j.cursor>=j.chests.size()){j.stage=Stage.POBLACION;j.cursor=0;continue;}
                    if(BweLootr.convert(j.world,j.chests.get(j.cursor++),j.lootTables,(at,state)->set(j,at,state)))j.converted++;
                }else if(j.stage==Stage.POBLACION){
                    if(j.cursor>=j.blueprint.entities().size()){
                        BweBackup.close();say(j,"Construccion terminada: "+j.entry.title()+" | "+j.entry.sizeX()+"x"+j.entry.sizeZ()+" bloques. "+j.converted+" cofres Lootr. /bwe deshacer restaura el terreno original.");active=null;break;}
                    var spawn=j.blueprint.entities().get(j.cursor++);CompoundTag tag=spawn.nbt().copy();tag.remove("UUID");
                    float yaw=tag.getList("Rotation",Tag.TAG_FLOAT).getFloat(0)+spawn.yaw();
                    Entity entity=EntityType.loadEntityRecursive(tag,j.world,e->{e.setUUID(UUID.randomUUID());e.moveTo(j.origin.getX()+spawn.x(),j.origin.getY()+spawn.y(),j.origin.getZ()+spawn.z(),yaw,0);return e;});
                    if(entity!=null){BweBackup.recordEntity(entity);j.world.addFreshEntityWithPassengers(entity);}

                }
                j.done++;
            }
        }catch(Exception error){if(error.getMessage()!=null&&(error.getMessage().contains("chunks sin cargar")||error.getMessage().contains("Chunk descargado"))){j.waiting="Esperando chunks: acercate sin entrar al area; la obra se reanudara";return;}say(j,"Obra detenida: "+error.getMessage()+(j.stage==Stage.VALIDAR||j.stage==Stage.VALIDAR_APOYOS?"":". Puedes retirar con /bwe deshacer."));com.mojang.logging.LogUtils.getLogger().error("[BWE] Surface construction failed",error);active=null;}
    }
}

