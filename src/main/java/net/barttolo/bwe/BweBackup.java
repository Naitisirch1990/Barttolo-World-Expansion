package net.barttolo.bwe;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;

/** Persistent write-ahead journal. Restore validates the complete journal before writing any blocks. */
public final class BweBackup {
 private static DataOutputStream output;
 private static DataInputStream input;
 private static final Set<Long> seen=new HashSet<>();
 private static ServerLevel restoringWorld;
 private static UUID operator;
 private static boolean validating;
 private static long count,total;
 private static CompoundTag pending;
 private static int entityWait;
 private static final Set<UUID> created=new HashSet<>();
 private static Path path(ServerLevel w){return w.getServer().getWorldPath(LevelResource.ROOT).resolve("bwe-backups").resolve(w.dimension().location().toString().replace(':','_').replace('/','_')+".nbtlog");}
 public static void begin(ServerLevel w)throws IOException{
  close();Path p=path(w);Files.createDirectories(p.getParent());
  if(Files.exists(p)&&Files.size(p)>0){Path history=p.getParent().resolve("history");Files.createDirectories(history);Files.copy(p,history.resolve(p.getFileName()+"."+System.currentTimeMillis()+"."+UUID.randomUUID()));}
  output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p)));seen.clear();
 }
 public static void capture(ServerLevel w,BlockPos p)throws IOException{
  if(output==null)throw new IOException("No hay respaldo abierto; obra detenida antes de modificar bloques");
  if(seen.contains(p.asLong()))return;
  CompoundTag t=new CompoundTag();t.putLong("pos",p.asLong());t.put("state",NbtUtils.writeBlockState(w.getBlockState(p)));
  var be=w.getBlockEntity(p);if(be!=null)t.put("be",be.saveWithFullMetadata());
  NbtIo.write(t,output);output.flush();seen.add(p.asLong());
 }
 public static void recordEntity(Entity entity)throws IOException{
  if(output==null)throw new IOException("No hay respaldo abierto para la poblacion");
  for(Entity e:entity.getSelfAndPassengers().toList()){
   CompoundTag t=new CompoundTag();t.putUUID("entity",e.getUUID());t.putLong("entityPos",e.blockPosition().asLong());NbtIo.write(t,output);
  }
  output.flush();
 }
 public static String restore(ServerLevel w){return restore(w,null);}
 public static String restore(ServerLevel w,UUID player){
  if(restoring())return "Ya hay una restauracion en curso";
  try{
   close();Path p=path(w);if(!Files.exists(p)||Files.size(p)==0)return "No hay respaldo de terreno en esta dimension";
   input=open(p);restoringWorld=w;operator=player;validating=true;count=0;total=0;return null;
  }catch(IOException e){close();return "No se puede abrir el respaldo: "+e.getMessage();}
 }
 private static DataInputStream open(Path p)throws IOException{return new DataInputStream(new BufferedInputStream(Files.newInputStream(p)));}
 private static CompoundTag next()throws IOException{input.mark(1);int b=input.read();if(b<0)return null;input.reset();return NbtIo.read(input);}
 private static void validate(CompoundTag t)throws IOException{
  if(t.hasUUID("entity")){if(!t.contains("entityPos",Tag.TAG_LONG))throw new IOException("Registro de entidad incompleto");return;}
  if(!t.contains("pos",Tag.TAG_LONG)||!t.contains("state",Tag.TAG_COMPOUND))throw new IOException("Registro de bloques incompleto");
  var tag=t.getCompound("state");ResourceLocation id=ResourceLocation.tryParse(tag.getString("Name"));
  if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Falta el bloque original "+tag.getString("Name"));
  var definition=BuiltInRegistries.BLOCK.get(id).getStateDefinition();var props=tag.getCompound("Properties");
  for(String name:props.getAllKeys()){var property=definition.getProperty(name);if(property==null||property.getValue(props.getString(name)).isEmpty())throw new IOException("Estado original incompatible: "+id+" / "+name);}
 }
 public static boolean restoring(){return restoringWorld!=null;}
 public static String status(){return (validating?"Comprobando respaldo: ":"Restaurando terreno: ")+count+(validating?" registros":" / "+total+" registros");}
 private static void say(String s){if(restoringWorld==null)return;var p=operator==null?null:restoringWorld.getServer().getPlayerList().getPlayer(operator);if(p!=null)p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[BWE] "+s));}
 public static void tick(){
  if(restoringWorld==null)return;
  try{
   for(int i=0;i<200;i++){
    if(entityWait>0){created.removeIf(uuid->{Entity e=restoringWorld.getEntity(uuid);if(e!=null){e.discard();return true;}return false;});if(created.isEmpty()||--entityWait==0){say("Restauracion terminada: recuperados los bloques y datos de contenedores respaldados. Las entidades originales eliminadas no se recuperan.");close();}return;}
    CompoundTag t=pending==null?next():pending;pending=null;
    if(t==null){
     if(validating){total=count;count=0;input.close();input=open(path(restoringWorld));validating=false;continue;}
     entityWait=30;continue;
    }
    if(validating){validate(t);count++;continue;}
    if(t.hasUUID("entity")){BlockPos p=BlockPos.of(t.getLong("entityPos"));restoringWorld.getChunk(p.getX()>>4,p.getZ()>>4);created.add(t.getUUID("entity"));count++;continue;}
    BlockPos p=BlockPos.of(t.getLong("pos"));restoringWorld.getChunk(p.getX()>>4,p.getZ()>>4);
    if(restoringWorld.players().stream().anyMatch(player->player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p)))){pending=t;return;}
    restoringWorld.removeBlockEntity(p);restoringWorld.setBlock(p,NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),t.getCompound("state")),2|16|32);
    if(t.contains("be")){var be=restoringWorld.getBlockEntity(p);if(be!=null){be.load(t.getCompound("be"));be.setChanged();}}
    count++;
   }
  }catch(Exception e){say("Restauracion detenida: "+e.getMessage()+". El archivo de respaldo se conserva.");com.mojang.logging.LogUtils.getLogger().error("[BWE] Restore failed, journal retained",e);close();}
 }
 public static void close(){
  try{if(output!=null)output.close();if(input!=null)input.close();}catch(IOException e){com.mojang.logging.LogUtils.getLogger().error("[BWE] Unable to close backup",e);}
  finally{output=null;input=null;restoringWorld=null;operator=null;pending=null;created.clear();entityWait=0;seen.clear();}
 }
}
