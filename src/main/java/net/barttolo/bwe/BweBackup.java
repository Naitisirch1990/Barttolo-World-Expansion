package net.barttolo.bwe;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.Registries;

/** Write-ahead terrain journal. Original state is recorded before each first modification. */
public final class BweBackup {
 private static DataOutputStream output;
 private static DataInputStream input;
 private static final Set<Long> seen=new HashSet<>();
 private static ServerLevel restoringWorld;
 private static Path path(ServerLevel w){
  return w.getServer().getWorldPath(LevelResource.ROOT).resolve("bwe-backups").resolve(w.dimension().location().toString().replace(':','_').replace('/','_')+".nbtlog");
 }
 public static void begin(ServerLevel w)throws IOException{
  close();Path p=path(w);Files.createDirectories(p.getParent());
  output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p)));seen.clear();
 }
 public static void capture(ServerLevel w,BlockPos p)throws IOException{
  if(output==null)throw new IOException("No hay respaldo abierto; obra detenida antes de modificar bloques");
  if(seen.contains(p.asLong()))return;
  CompoundTag t=new CompoundTag();t.putLong("pos",p.asLong());t.put("state",NbtUtils.writeBlockState(w.getBlockState(p)));
  var be=w.getBlockEntity(p);if(be!=null)t.put("be",be.saveWithFullMetadata());
  NbtIo.write(t,output);output.flush();seen.add(p.asLong());
 }
 public static String restore(ServerLevel w){
  try{close();Path p=path(w);if(!Files.exists(p))return "No hay respaldo de terreno en esta dimension";
   input=new DataInputStream(new BufferedInputStream(Files.newInputStream(p)));restoringWorld=w;return null;
  }catch(IOException e){return "No se puede abrir el respaldo: "+e.getMessage();}
 }
 public static boolean restoring(){return restoringWorld!=null;}
 public static void tick(){
  if(restoringWorld==null)return;
  try{
   for(int i=0;i<200;i++){
    input.mark(1);int first=input.read();if(first<0){close();return;}input.reset();
    CompoundTag t=NbtIo.read(input);BlockPos p=BlockPos.of(t.getLong("pos"));
    // Load only the existing chunk needed by this restoration.
    restoringWorld.getChunk(p.getX()>>4,p.getZ()>>4);
    restoringWorld.removeBlockEntity(p);
    restoringWorld.setBlock(p,NbtUtils.readBlockState(restoringWorld.holderLookup(Registries.BLOCK),t.getCompound("state")),2|16|32);
    if(t.contains("be")){var be=restoringWorld.getBlockEntity(p);if(be!=null){be.load(t.getCompound("be"));be.setChanged();}}
   }
  }catch(Exception e){close();throw new IllegalStateException("Restauracion detenida; respaldo conservado",e);}
 }
 public static void close(){
  try{if(output!=null)output.close();if(input!=null)input.close();}catch(IOException e){throw new IllegalStateException(e);}
  finally{output=null;input=null;restoringWorld=null;seen.clear();}
 }
}
