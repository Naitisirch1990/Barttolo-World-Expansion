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
    private enum Stage { LIMPIEZA,CIMENTACION,CONSTRUCCION,DETALLES,POBLACION,RETIRAR }
    private static final class Job {
        final ServerLevel world;final BweCatalog.Entry entry;final BweCatalog.Blueprint blueprint;
        final BlockPos origin;final int walkY,x0,z0,x1,z1;final UUID operator;final AABB area;
        int x,z,y=Integer.MIN_VALUE,cursor,done;Stage stage=Stage.LIMPIEZA;String waiting="";
        Job(ServerLevel w,BweCatalog.Entry e,BweCatalog.Blueprint b,BlockPos center,int ground,int margin,UUID op) {
            world=w;entry=e;blueprint=b;walkY=ground;operator=op;
            origin=new BlockPos(center.getX()-e.sizeX()/2,ground-e.ground(),center.getZ()-e.sizeZ()/2);
            x0=origin.getX()-margin;z0=origin.getZ()-margin;x1=origin.getX()+e.sizeX()-1+margin;z1=origin.getZ()+e.sizeZ()-1+margin;
            x=x0;z=z0;area=new AABB(x0,w.getMinBuildHeight(),z0,x1+1.0,w.getMaxBuildHeight(),z1+1.0);
        }
        void column() {y=Integer.MIN_VALUE;if(++z>z1){z=z0;x++;}}
    }
    public static boolean busy(){return active!=null;}
    public static void reset(){active=null;last=null;}
    public static String status(){return active==null?"Sin obra en curso.":active.entry.title()+" | "+active.stage+" | "+active.done+" operaciones"+(active.waiting.isEmpty()?"":" | "+active.waiting);}
    private static void say(Job j,String s){var p=j.world.getServer().getPlayerList().getPlayer(j.operator);if(p!=null)p.sendSystemMessage(Component.literal("[BWE] "+s));}
    public static int ground(ServerLevel world,BlockPos center,BweCatalog.Entry e,int margin) {
        int x0=center.getX()-e.sizeX()/2-margin,z0=center.getZ()-e.sizeZ()/2-margin;
        int x1=x0+e.sizeX()-1+2*margin,z1=z0+e.sizeZ()-1+2*margin;
        checkArea(world,x0,z0,x1,z1);
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
    public static String start(ServerLevel world,BweCatalog.Entry e,BlockPos center,int ground,int margin,UUID op) {
        if(busy()||BuildEngine.busy())return "Ya hay una obra en curso";
        try {
            Job j=new Job(world,e,BweCatalog.compose(world,e),center,ground,margin,op);
            checkArea(world,j.x0,j.z0,j.x1,j.z1);
            if(j.origin.getY()<world.getMinBuildHeight()||j.origin.getY()+e.sizeY()>world.getMaxBuildHeight())return "La estructura no cabe a esta altura";
            active=j;last=j;return null;
        }catch(Exception error){return error.getMessage();}
    }
    public static String remove(ServerLevel world){
        Job j=active==null?last:active;
        if(j==null)return "No hay obra reciente en memoria para retirar";
        if(j.world!=world)return "Vuelve a la dimension de la obra";
        j.stage=Stage.RETIRAR;j.cursor=0;j.done=0;active=j;return null;
    }
    private static boolean purge(Job j){
        var es=j.world.getEntitiesOfClass(Entity.class,j.area);
        if(es.stream().anyMatch(e->e instanceof Player)){j.waiting="Alejate horizontalmente del area";return false;}
        int count=0;for(var e:es){if(e instanceof Player)continue;if(count++>=256){j.waiting="Retirando entidades";return false;}e.discard();}
        j.waiting="";return true;
    }
    private static void set(Job j,BlockPos at,net.minecraft.world.level.block.state.BlockState state){
        if(!j.world.hasChunk(at.getX()>>4,at.getZ()>>4))throw new IllegalArgumentException("Chunk descargado: acercate sin entrar en la obra");
        var old=j.world.getBlockState(at);
        if(!old.equals(state)||old.hasBlockEntity()){
            j.world.removeBlockEntity(at);
            if(old.equals(state)&&old.hasBlockEntity())j.world.setBlock(at,Blocks.AIR.defaultBlockState(),FLAGS);
            j.world.setBlock(at,state,FLAGS);
        }
    }
    public static void tick(){
        Job j=active;if(j==null)return;
        try{
            checkArea(j.world,j.x0,j.z0,j.x1,j.z1);
            // Purge before creating the new population, otherwise we'd discard our own villagers.
            if(j.stage!=Stage.POBLACION&&!purge(j))return;
            int work=0;
            while(active==j&&work++<BUDGET){
                if(j.stage==Stage.LIMPIEZA){
                    if(j.x>j.x1){j.x=j.x0;j.z=j.z0;j.y=Integer.MIN_VALUE;j.stage=Stage.CIMENTACION;continue;}
                    if(j.y==Integer.MIN_VALUE)j.y=j.world.getHeight(Heightmap.Types.WORLD_SURFACE,j.x,j.z)-1;
                    if(j.y<j.walkY){j.column();continue;}
                    set(j,new BlockPos(j.x,j.y--,j.z),Blocks.AIR.defaultBlockState());
                }else if(j.stage==Stage.CIMENTACION){
                    if(j.x>j.x1){j.stage=Stage.CONSTRUCCION;j.cursor=0;continue;}
                    if(j.y==Integer.MIN_VALUE)j.y=j.walkY-1;
                    if(j.y<Math.max(j.world.getMinBuildHeight(),j.walkY-64))throw new IllegalArgumentException("No hay soporte: elige un terreno mas bajo");
                    BlockPos at=new BlockPos(j.x,j.y,j.z);var old=j.world.getBlockState(at);
                    boolean support=!old.isAir()&&old.getFluidState().isEmpty()&&!old.canBeReplaced();
                    if(j.y==j.walkY-1||!support)set(j,at,(j.y==j.walkY-1?Blocks.GRASS_BLOCK:j.y>=j.walkY-3?Blocks.DIRT:Blocks.STONE).defaultBlockState());
                    if(support)j.column();else j.y--;
                }else if(j.stage==Stage.CONSTRUCCION){
                    if(j.cursor>=j.blueprint.cells().size()){j.stage=Stage.DETALLES;j.cursor=0;continue;}
                    var cell=j.blueprint.cells().get(j.cursor++);set(j,j.origin.offset(cell.pos()),cell.state());
                }else if(j.stage==Stage.DETALLES){
                    if(j.cursor>=j.blueprint.cells().size()){j.stage=Stage.POBLACION;j.cursor=0;continue;}
                    var cell=j.blueprint.cells().get(j.cursor++);
                    if(cell.nbt()!=null){BlockPos at=j.origin.offset(cell.pos());var be=j.world.getBlockEntity(at);
                        if(be!=null){CompoundTag tag=cell.nbt().copy();tag.putInt("x",at.getX());tag.putInt("y",at.getY());tag.putInt("z",at.getZ());be.load(tag);be.setChanged();}}
                }else if(j.stage==Stage.POBLACION){
                    if(j.cursor>=j.blueprint.entities().size()){
                        say(j,"Construccion terminada: "+j.entry.title()+" | "+j.entry.sizeX()+"x"+j.entry.sizeZ()+" bloques. Puedes crear otra; /bwe deshacer solo retira esta obra.");active=null;break;}
                    var spawn=j.blueprint.entities().get(j.cursor++);CompoundTag tag=spawn.nbt().copy();tag.remove("UUID");
                    float yaw=tag.getList("Rotation",Tag.TAG_FLOAT).getFloat(0)+spawn.yaw();
                    Entity entity=EntityType.loadEntityRecursive(tag,j.world,e->{e.setUUID(UUID.randomUUID());e.moveTo(j.origin.getX()+spawn.x(),j.origin.getY()+spawn.y(),j.origin.getZ()+spawn.z(),yaw,0);return e;});
                    if(entity!=null)j.world.addFreshEntityWithPassengers(entity);
                }else if(j.stage==Stage.RETIRAR){
                    if(j.cursor>=j.blueprint.cells().size()){say(j,"Obra retirada. No se recupera el terreno anterior ni las entidades eliminadas.");active=null;last=null;break;}
                    var at=j.origin.offset(j.blueprint.cells().get(j.cursor++).pos());
                    set(j,at,(at.getY()>=j.walkY?Blocks.AIR:at.getY()==j.walkY-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
                }
                j.done++;
            }
        }catch(Exception error){say(j,"Obra detenida: "+error.getMessage()+". Puedes retirar con /bwe deshacer.");com.mojang.logging.LogUtils.getLogger().error("[BWE] Surface construction failed",error);active=null;}
    }
}
