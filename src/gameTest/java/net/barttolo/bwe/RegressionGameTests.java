package net.barttolo.bwe;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.loading.FMLPaths;

@GameTestHolder("bwe_regression")
@PrefixGameTestTemplate(false)
public final class RegressionGameTests {
 @GameTest(batch="restore_lootr",template="empty",timeoutTicks=900)
 public static void restoresTerrainContainersAndNativeLootr(GameTestHelper h)throws Exception{
  var w=h.getLevel();var server=w.getServer();
  // Reproduce the former 65,536-block rejection using the actual Minecraft template reader.
  CompoundTag big=new CompoundTag();ListTag size=new ListTag();for(int v:new int[]{42,42,42})size.add(IntTag.valueOf(v));big.put("size",size);
  ListTag palette=new ListTag();CompoundTag air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);big.put("palette",palette);
  ListTag blocks=new ListTag();for(int x=0;x<42;x++)for(int y=0;y<42;y++)for(int z=0;z<42;z++){CompoundTag b=new CompoundTag();ListTag pos=new ListTag();for(int v:new int[]{x,y,z})pos.add(IntTag.valueOf(v));b.put("pos",pos);b.putInt("state",0);blocks.add(b);}big.put("blocks",blocks);big.put("entities",new ListTag());
  StructureTemplate template=new StructureTemplate();template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),big);
  h.assertTrue(TemplateNbtPiece.readForConstruction(template).cells().size()==74088,"Construction reader still rejects large pieces");
  boolean limited=false;try{TemplateNbtPiece.read(template);}catch(IllegalArgumentException expected){limited=true;}h.assertTrue(limited,"Inspection safety limit changed");
  // A fixture road at local Y=0 and an inaccurate old ground reference of 2.
  CompoundTag road=new CompoundTag();ListTag roadSize=new ListTag();for(int v:new int[]{5,3,5})roadSize.add(IntTag.valueOf(v));road.put("size",roadSize);
  CompoundTag gravel=new CompoundTag();gravel.putString("Name","minecraft:stone_bricks");ListTag roadPalette=new ListTag();roadPalette.add(gravel);road.put("palette",roadPalette);
  ListTag floor=new ListTag();for(int x=0;x<5;x++)for(int z=0;z<5;z++){CompoundTag b=new CompoundTag();ListTag pos=new ListTag();for(int v:new int[]{x,0,z})pos.add(IntTag.valueOf(v));b.put("pos",pos);b.putInt("state",0);floor.add(b);}road.put("blocks",floor);road.put("entities",new ListTag());
  CompoundTag boat=road.copy();ListTag boatSize=new ListTag();for(int v:new int[]{5,5,5})boatSize.add(IntTag.valueOf(v));boat.put("size",boatSize);
  ListTag boatPalette=roadPalette.copy();boatPalette.add(air.copy());boat.put("palette",boatPalette);ListTag boatBlocks=new ListTag();
  for(int x=0;x<5;x++)for(int y=0;y<5;y++)for(int z=0;z<5;z++){
   CompoundTag b=new CompoundTag();ListTag pos=new ListTag();for(int v:new int[]{x,y,z})pos.add(IntTag.valueOf(v));b.put("pos",pos);
   b.putInt("state",y==0||(y<4&&(x==0||x==4||z==0||z==4))?0:1);boatBlocks.add(b);
  }boat.put("blocks",boatBlocks);
  Path dir=FMLPaths.CONFIGDIR.get().resolve("bwe");Files.createDirectories(dir);
  try(var zip=new ZipOutputStream(Files.newOutputStream(dir.resolve("ctov.jar")))){zip.putNextEntry(new ZipEntry("data/ctov/structures/village/path/regression.nbt"));ByteArrayOutputStream data=new ByteArrayOutputStream();NbtIo.writeCompressed(road,data);zip.write(data.toByteArray());zip.closeEntry();zip.putNextEntry(new ZipEntry("data/ctov/structures/regression_boat.nbt"));data.reset();NbtIo.writeCompressed(boat,data);zip.write(data.toByteArray());zip.closeEntry();}
  var pieces=List.of(new BweCatalog.Piece("ctov:village/path/regression",0,0,0,0));BweCatalog.reset();
  var entry=new BweCatalog.Entry("test:road","road","",5,3,5,2,pieces);h.assertTrue(BweCatalog.compose(w,entry).surfaceY()==0,"Road surface reference remains one block low");
  var flying=new BweCatalog.Entry("integrated_villages:aldea_airship_village","airship","",5,3,5,2,pieces);h.assertTrue(BweCatalog.compose(w,flying).surfaceY()==1,"Airship placement changed");
  var pirate=new BweCatalog.Entry("integrated_villages:aldea_pirate_village","pirate","",5,5,5,7,List.of(new BweCatalog.Piece("ctov:regression_boat",0,0,0,0)));
  h.assertTrue(BweCatalog.compose(w,pirate).surfaceY()==2,"Pirate waterline reference incorrect");
  BlockPos at=h.absolutePos(new BlockPos(1,2,1)),chest=h.absolutePos(new BlockPos(3,2,1));
  w.setBlock(at,Blocks.STONE.defaultBlockState(),50);w.setBlock(chest,Blocks.CHEST.defaultBlockState(),50);
  ((ChestBlockEntity)w.getBlockEntity(chest)).setItem(0,new ItemStack(Items.DIAMOND,7));
  BweBackup.begin(w);BweBackup.capture(w,at);BweBackup.capture(w,chest);w.setBlock(at,Blocks.GOLD_BLOCK.defaultBlockState(),50);
  h.assertTrue(BweLootr.available(),"Real Lootr test dependency was not loaded");var tables=BweLootr.tables(w);h.assertTrue(!tables.isEmpty(),"No valid chest loot tables");
  boolean converted=BweLootr.convert(w,chest,tables,(p,state)->{try{BweBackup.capture(w,p);}catch(IOException e){throw new RuntimeException(e);}w.removeBlockEntity(p);w.setBlock(p,state,50);});
  h.assertTrue(converted,"Lootr conversion failed");var nativeChest=w.getBlockEntity(chest);var nbt=nativeChest.saveWithFullMetadata();
  h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(w.getBlockState(chest).getBlock()).toString().equals("lootr:lootr_chest"),"Not a native Lootr chest");
  h.assertTrue(tables.stream().anyMatch(id->id.toString().equals(nbt.getString("LootTable"))),"Lootr table was not persisted");
  var pig=EntityType.PIG.create(w);pig.moveTo(at.getX()+0.5,at.getY()+1,at.getZ()+0.5,0,0);BweBackup.recordEntity(pig);w.addFreshEntity(pig);
  BweBackup.close();h.assertTrue(BweBackup.restore(w)==null,"Cannot reopen persistent journal");
  h.runAfterDelay(1,new Runnable(){int phase;
   BlockPos aerialBase;
   public void run(){try{
    if(BweBackup.restoring()){h.runAfterDelay(1,()->this.run());return;}
    if(phase==0){
     h.assertTrue(w.getBlockState(at).is(Blocks.STONE),"Original terrain not restored");h.assertTrue(w.getBlockState(chest).is(Blocks.CHEST),"Original chest block not restored");
     var restored=(ChestBlockEntity)w.getBlockEntity(chest);h.assertTrue(restored.getItem(0).is(Items.DIAMOND)&&restored.getItem(0).getCount()==7,"Original chest inventory lost");h.assertTrue(pig.isRemoved(),"Created population was not removed");
     BweBackup.begin(w);BweBackup.capture(w,at);w.setBlock(at,Blocks.GOLD_BLOCK.defaultBlockState(),50);BweBackup.close();
     Path journal=server.getWorldPath(LevelResource.ROOT).resolve("bwe-backups").resolve(w.dimension().location().toString().replace(':','_').replace('/','_')+".nbtlog");Files.write(journal,new byte[]{10},StandardOpenOption.APPEND);
     h.assertTrue(BweBackup.restore(w)==null,"Cannot initiate corruption validation");phase=1;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==1){
     h.assertTrue(w.getBlockState(at).is(Blocks.GOLD_BLOCK),"Corrupt journal changed terrain before validation completed");
     // Every newly imported palette must exist with valid states on the real 1.20.1 registry.
     try(var input=RegressionGameTests.class.getResourceAsStream("/extra-palettes.json")){
      var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
      for(var raw:root.getAsJsonArray("states")){
       var st=raw.getAsJsonObject();var key=new net.minecraft.resources.ResourceLocation(st.get("Name").getAsString());
       h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(key),"Unknown imported block: "+key);
       var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key);
       if(st.has("Properties"))for(var prop:st.getAsJsonObject("Properties").entrySet()){
        var property=block.getStateDefinition().getProperty(prop.getKey());
        h.assertTrue(property!=null&&property.getValue(prop.getValue().getAsString()).isPresent(),"Invalid imported property: "+st);
       }
      }
     }
     aerialBase=h.absolutePos(new BlockPos(20,12,20));
     for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
      w.getChunk((aerialBase.getX()+x)>>4,(aerialBase.getZ()+z)>>4);
      for(int y=-5;y<=6;y++)w.setBlock(aerialBase.offset(x,y,z),Blocks.STONE.defaultBlockState(),50);
     }
     int automatic=SurfaceBuilder.ground(w,aerialBase,flying,1);
     h.assertTrue(automatic-1>=aerialBase.getY()+13,"Automatic airship base intersects terrain");
     h.assertTrue(SurfaceBuilder.start(w,flying,aerialBase,aerialBase.getY()+1,1,java.util.UUID.randomUUID())==null,"Airship build rejected");
     phase=2;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==2){
     if(SurfaceBuilder.busy()){h.runAfterDelay(1,()->this.run());return;}
     h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE_BRICKS),"Airship geometry not placed at chosen base");
     h.assertTrue(w.getBlockState(aerialBase.below(1)).isAir(),"Airship has earth under its floor");
     h.assertTrue(w.getBlockState(aerialBase.offset(-3,-4,-3)).isAir(),"Airship underside was not cleared");
     h.assertTrue(w.getBlockState(aerialBase.offset(3,6,3)).isAir(),"Airship upper or side clearance not cleared");
     h.assertTrue(w.getBlockState(aerialBase.below(5)).is(Blocks.STONE),"Airship changed terrain below clearing envelope");
     h.assertTrue(w.getBlockState(aerialBase.offset(4,0,4)).is(Blocks.STONE),"Airship changed blocks outside border");
     BweBackup.close();h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Airship backup cannot reopen");phase=3;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==3){
     h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE)&&w.getBlockState(aerialBase.below()).is(Blocks.STONE),"Airship original terrain not restored");
     h.assertTrue(SurfaceBuilder.start(w,entry,aerialBase,aerialBase.getY()+1,1,java.util.UUID.randomUUID())==null,"Ground build rejected");phase=4;h.runAfterDelay(1,()->this.run());return;
    }
    if(SurfaceBuilder.busy()){h.runAfterDelay(1,()->this.run());return;}
    if(phase==4){
     h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE_BRICKS),"Normal road placement changed");
     h.assertTrue(w.getBlockState(aerialBase.offset(-3,0,-3)).is(Blocks.GRASS_BLOCK),"Normal ground foundation disabled by airship exception");
     h.assertTrue(w.getBlockState(aerialBase.below()).is(Blocks.STONE),"Normal foundation changed original support");
     for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(Math.abs(x)==4||Math.abs(z)==4)for(int y=-4;y<=0;y++)w.setBlock(aerialBase.offset(x,y,z),Blocks.WATER.defaultBlockState(),50);
     h.assertTrue(SurfaceBuilder.start(w,pirate,aerialBase,aerialBase.getY()+1,1,java.util.UUID.randomUUID())==null,"Pirate build rejected");phase=5;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==5){
     h.assertTrue(w.getBlockState(aerialBase.below(2)).is(Blocks.STONE_BRICKS),"Pirate hull not aligned below waterline");
     h.assertTrue(w.getBlockState(aerialBase).isAir()&&w.getBlockState(aerialBase.below()).isAir(),"Sealed hull flooded");
     h.assertTrue(w.getBlockState(aerialBase.offset(-3,0,-3)).is(Blocks.WATER),"Pirate contour is not water");
     h.assertTrue(w.getBlockState(aerialBase.offset(-3,-4,-3)).is(Blocks.WATER),"Pirate underwater clearance filled with land");
     h.assertTrue(w.getBlockState(aerialBase.offset(-3,1,-3)).isAir(),"Water raised above selected waterline");
     h.assertTrue(w.getBlockState(aerialBase.offset(2,1,0)).is(Blocks.STONE_BRICKS),"Pirate upper hull lost");
     h.assertTrue(SurfaceBuilder.ground(w,aerialBase,pirate,1)==aerialBase.getY()+1,"Pirate water level detection uses seafloor or deck");
     h.assertTrue(w.getBlockState(aerialBase.below(5)).is(Blocks.STONE),"Pirate excavated below its bounded volume");
     BweBackup.close();h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Pirate undo rejected");phase=6;h.runAfterDelay(1,()->this.run());return;
    }
    h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE_BRICKS)&&w.getBlockState(aerialBase.offset(-3,0,-3)).is(Blocks.GRASS_BLOCK),"Pirate undo did not restore previous road and terrain");
    h.assertTrue(w.getBlockState(aerialBase.below(2)).is(Blocks.STONE),"Pirate undo did not recover original seafloor");
    System.out.println("[BWE-REGRESSION] PASS Lootr, persistent undo, all imported palettes, airship and normal construction unchanged; pirate water contour, dry hull, correct waterline and pirate undo");h.succeed();
   }catch(Exception e){BweBackup.close();h.fail(e.toString());}}
  });
 }
}
