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
  mansionBudget(h);
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
  for(String special:List.of("bwe_extra:mansion","bwe_extra:faro_realista","bwe_extra:coliseo_natural")){
   var cropped=new BweCatalog.Entry(special,"cropped","",5,3,5,5,pieces);var cropPlan=BweCatalog.compose(w,cropped);
   h.assertTrue(cropPlan.surfaceY()==-1&&SurfaceBuilder.originY(cropped,cropPlan,71)==71,"Cropped base still buried: "+special);
  }
  var flying=new BweCatalog.Entry("integrated_villages:aldea_airship_village","airship","",5,3,5,2,pieces);h.assertTrue(BweCatalog.compose(w,flying).surfaceY()==1,"Airship placement changed");
  var pirate=new BweCatalog.Entry("integrated_villages:aldea_pirate_village","pirate","",5,5,5,7,List.of(new BweCatalog.Piece("ctov:regression_boat",0,0,0,0)));
  h.assertTrue(BweCatalog.compose(w,pirate).surfaceY()==2,"Pirate waterline reference incorrect");
  var market=new BweCatalog.Entry("bwe_idas:bazaar","Bazar","",5,10,5,30,List.of(new BweCatalog.Piece("ctov:village/path/regression",0,5,0,0)));
  var fortress=new BweCatalog.Entry("bwe_idas:iceandfire_dread_citadel","Ciudadela","",5,8,5,39,pieces);
  var fisher=new BweCatalog.Entry("bwe_idas:fishermans_lodge","Refugio","",5,24,5,1,pieces);
  h.assertTrue(BweCatalog.compose(w,market).surfaceY()==5,"Stale bazaar metadata still uses roof as ground");
  h.assertTrue(BweCatalog.compose(w,fortress).surfaceY()==0,"Stale citadel metadata buries lower fortress");
  h.assertTrue(BweCatalog.compose(w,fisher).surfaceY()==17&&BweCatalog.pirate(fisher),"Stale fisher metadata still creates earth foundation");
  BlockPos at=h.absolutePos(new BlockPos(1,2,1)),chest=h.absolutePos(new BlockPos(3,2,1));
  w.setBlock(at,Blocks.STONE.defaultBlockState(),50);w.setBlock(chest,Blocks.CHEST.defaultBlockState(),50);
  ((ChestBlockEntity)w.getBlockEntity(chest)).setItem(0,new ItemStack(Items.DIAMOND,7));
  BweBackup.begin(w);BweBackup.capture(w,at);BweBackup.capture(w,chest);w.setBlock(at,Blocks.GOLD_BLOCK.defaultBlockState(),50);
  h.assertTrue(BweLootr.available(),"Real Lootr test dependency was not loaded");var tables=BweLootr.tables(w);h.assertTrue(!tables.isEmpty(),"No valid chest loot tables");
  boolean converted=BweLootr.convert(w,chest,tables,(p,state)->{try{BweBackup.capture(w,p);}catch(IOException e){throw new RuntimeException(e);}w.removeBlockEntity(p);w.setBlock(p,state,50);});
  h.assertTrue(converted,"Lootr conversion failed");var nativeChest=w.getBlockEntity(chest);var nbt=nativeChest.saveWithFullMetadata();
  h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(w.getBlockState(chest).getBlock()).toString().equals("lootr:lootr_chest"),"Not a native Lootr chest");
  h.assertTrue(tables.stream().anyMatch(id->id.toString().equals(nbt.getString("LootTable"))),"Lootr table was not persisted");
  lootRegression(h,chest);
  BlockPos barrel=h.absolutePos(new BlockPos(2,2,2));
  w.setBlock(barrel,Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,net.minecraft.core.Direction.UP),50);
  var oldBarrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)w.getBlockEntity(barrel);oldBarrel.setItem(0,new ItemStack(Items.EMERALD,9));oldBarrel.setCustomName(net.minecraft.network.chat.Component.literal("Suministros"));
  h.assertTrue(BweLootr.convertible(w.getBlockState(barrel)),"Construction does not select barrels");
  h.assertTrue(BweLootr.convert(w,barrel,tables,(p,state)->{try{BweBackup.capture(w,p);}catch(IOException e){throw new RuntimeException(e);}w.removeBlockEntity(p);w.setBlock(p,state,50);}),"Barrel conversion failed");
  h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(w.getBlockState(barrel).getBlock()).toString().equals("lootr:lootr_barrel"),"Not a native Lootr barrel");
  h.assertTrue(w.getBlockState(barrel).getValue(BarrelBlock.FACING)==net.minecraft.core.Direction.UP,"Barrel rotation lost");
  h.assertTrue(((net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)w.getBlockEntity(barrel)).getDisplayName().getString().equals("Suministros"),"Barrel custom name lost");
  lootRegression(h,barrel);
  var pig=EntityType.PIG.create(w);pig.moveTo(at.getX()+0.5,at.getY()+1,at.getZ()+0.5,0,0);BweBackup.recordEntity(pig);w.addFreshEntity(pig);
  BweBackup.close();h.assertTrue(BweBackup.restore(w)==null,"Cannot reopen persistent journal");
  h.runAfterDelay(1,new Runnable(){int phase;
   BlockPos aerialBase;int desired;
   public void run(){try{
    if(BweBackup.restoring()){h.runAfterDelay(1,()->this.run());return;}
    if(phase==0){
     h.assertTrue(w.getBlockState(at).is(Blocks.STONE),"Original terrain not restored");h.assertTrue(w.getBlockState(chest).is(Blocks.CHEST),"Original chest block not restored");
     h.assertTrue(w.getBlockState(barrel).is(Blocks.BARREL)&&((net.minecraft.world.Container)w.getBlockEntity(barrel)).getItem(0).getCount()==9,"Original barrel/inventory not restored");
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
     int detected=SurfaceBuilder.ground(w,aerialBase,pirate,1);h.assertTrue(detected==aerialBase.getY()+1,"Pirate water detection: expected "+(aerialBase.getY()+1)+", got "+detected);
     h.assertTrue(w.getBlockState(aerialBase.below(5)).is(Blocks.STONE),"Pirate excavated below its bounded volume");
     BweBackup.close();h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Pirate undo rejected");phase=6;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==6){
     h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE_BRICKS)&&w.getBlockState(aerialBase.offset(-3,0,-3)).is(Blocks.GRASS_BLOCK),"Pirate undo did not restore previous road and terrain");
     h.assertTrue(w.getBlockState(aerialBase.below(2)).is(Blocks.STONE),"Pirate undo did not recover original seafloor");
     // A road ten blocks above the scheme origin must land at the exact requested Y.
     Files.writeString(dir.resolve("catalogo-iv-v2.json"),"{\"entries\":[{\"id\":\"test:height\",\"title\":\"Height fixture\",\"description\":\"\",\"sizeX\":5,\"sizeY\":13,\"sizeZ\":5,\"ground\":12,\"pieces\":[{\"template\":\"ctov:village/path/regression\",\"x\":0,\"y\":10,\"z\":0,\"rotation\":0}]}]}");
     BweCatalog.reset();var tall=BweCatalog.find("test:height");h.assertTrue(tall!=null,"Height fixture missing");var plan=BweCatalog.compose(w,tall);
     h.assertTrue(plan.surfaceY()==10,"Height fixture road reference invalid");desired=aerialBase.getY()+4;
     h.assertTrue(SurfaceBuilder.originY(tall,plan,desired+1)+plan.surfaceY()==desired,"Manual height incorrectly treated as origin");
     var fake=net.minecraftforge.common.util.FakePlayerFactory.get(w,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"BWEHeightTest"));
     fake.setPos(aerialBase.getX()+0.5,aerialBase.getY()+1,aerialBase.getZ()+0.5);
     command(h,fake,"bwe listar");command(h,fake,"bwe listar EXTRAS");command(h,fake,"bwe listar 1");
     command(h,fake,"bwe escanear");command(h,fake,"bwe seleccionar test:height");command(h,fake,"bwe altura "+desired);
     command(h,fake,"bwe borde");command(h,fake,"bwe altura "+desired);command(h,fake,"bwe seleccionar test:height");
     for(int rotation=0;rotation<4;rotation++)command(h,fake,"bwe rotar");
     command(h,fake,"bwe crear");
     var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
     try{var border=new BweNetwork.Border(w.dimension().location().toString(),true,-5000,-100,5225,124,desired+1);border.encode(buf);h.assertTrue(border.equals(BweNetwork.Border.decode(buf)),"Large border packet lost coordinates or height");}finally{buf.release();}
     phase=7;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==7){
     h.assertTrue(w.getBlockState(new BlockPos(aerialBase.getX(),desired,aerialBase.getZ())).is(Blocks.STONE_BRICKS),"Height changed after reselection; road is not at requested Y");
     h.assertTrue(!w.getBlockState(new BlockPos(aerialBase.getX(),desired-10,aerialBase.getZ())).is(Blocks.STONE_BRICKS),"Road was buried at the scheme origin");
     validateBiomes(h);validateExpansion(h);
     CompoundTag wall=boat.copy();ListTag walls=new ListTag();
     for(int x=0;x<5;x++)for(int y=0;y<5;y++){CompoundTag b=new CompoundTag();ListTag pos=new ListTag();for(int v:new int[]{x,y,2})pos.add(IntTag.valueOf(v));b.put("pos",pos);b.putInt("state",0);walls.add(b);}wall.put("blocks",walls);
     CompoundTag painting=new CompoundTag();painting.putString("id","minecraft:painting");painting.putString("variant","minecraft:kebab");painting.putByte("facing",(byte)2);painting.putInt("TileX",2);painting.putInt("TileY",2);painting.putInt("TileZ",1);
     ListTag paintingPos=new ListTag();for(double v:new double[]{2.5,2.5,1.96875})paintingPos.add(DoubleTag.valueOf(v));painting.put("Pos",paintingPos);ListTag paintingRotation=new ListTag();paintingRotation.add(FloatTag.valueOf(180));paintingRotation.add(FloatTag.valueOf(0));painting.put("Rotation",paintingRotation);
     CompoundTag entityInfo=new CompoundTag();entityInfo.put("nbt",painting);entityInfo.put("pos",paintingPos);ListTag anchor=new ListTag();for(int v:new int[]{2,2,1})anchor.add(IntTag.valueOf(v));entityInfo.put("blockPos",anchor);ListTag wallEntities=new ListTag();wallEntities.add(entityInfo);wall.put("entities",wallEntities);
     CompoundTag empty=wall.copy();ListTag emptyBlocks=new ListTag();for(int x=0;x<5;x++)for(int y=0;y<5;y++)for(int z=0;z<5;z++){CompoundTag b=new CompoundTag();ListTag pos=new ListTag();for(int v:new int[]{x,y,z})pos.add(IntTag.valueOf(v));b.put("pos",pos);b.putInt("state",1);emptyBlocks.add(b);}empty.put("blocks",emptyBlocks);empty.put("entities",new ListTag());
     try(var zip=new ZipOutputStream(Files.newOutputStream(dir.resolve("bwe_better.jar")))){
      for(var pair:java.util.Map.of("wall",wall,"air",empty).entrySet()){zip.putNextEntry(new ZipEntry("data/bwe_better/structures/test/"+pair.getKey()+".nbt"));ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(pair.getValue(),out);zip.write(out.toByteArray());zip.closeEntry();}
     }
     Files.writeString(dir.resolve("catalogo-biomas-v1.json"),"{\"entries\":[{\"id\":\"bwe_better:test_wall\",\"title\":\"Better Village - Pradera\",\"description\":\"Pradera\",\"biome\":\"Pradera\",\"sizeX\":5,\"sizeY\":5,\"sizeZ\":5,\"ground\":1,\"pieces\":[{\"template\":\"bwe_better:test/wall\",\"x\":3,\"y\":0,\"z\":0,\"rotation\":1},{\"template\":\"bwe_better:test/air\",\"x\":0,\"y\":0,\"z\":0,\"rotation\":0}]}]}");
     BweCatalog.reset();h.assertTrue(BweCatalog.find("test:height")!=null,"New biome catalogue replaced existing entries");var entry=BweCatalog.find("bwe_better:test_wall");h.assertTrue(entry!=null,"New biome catalogue not appended");var plan=BweCatalog.compose(w,entry);
     h.assertTrue(plan.cells().stream().filter(c->!c.state().isAir()).count()==25,"New template padding erased neighbouring structure");var spawn=plan.entities().get(0);h.assertTrue(Math.abs(spawn.x()-2.03125)<0.0001,"Rotated entity centre shifted by one block");h.assertTrue(spawn.nbt().getInt("TileX")==2&&spawn.nbt().getInt("TileZ")==2&&spawn.nbt().getByte("facing")==3,"Hanging anchor or direction did not rotate");
     var cleanEntry=new BweCatalog.Entry("bwe_tt:population_fixture","TT - fixture","Sin mobs",5,5,5,1,entry.pieces());
     h.assertTrue(BweCatalog.compose(w,cleanEntry).entities().isEmpty(),"Imported stored painting/entity was not removed");
     rotationAndOutline(h,entry,plan);
     entry=BweCatalog.turned(entry);plan=BweCatalog.turn(BweCatalog.find("bwe_better:test_wall"),plan);
     h.assertTrue(SurfaceBuilder.start(w,entry,plan,aerialBase,aerialBase.getY()+1,1,java.util.UUID.randomUUID())==null,"Biome archive construction rejected");phase=8;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==8){
     var paintings=w.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class,new net.minecraft.world.phys.AABB(aerialBase).inflate(6));
     h.assertTrue(paintings.size()==1,"Imported hanging decoration missing or duplicated");h.assertTrue(paintings.get(0).survives(),"User-rotated painting detached from its wall");
     List<BweCatalog.Cell> cells=new ArrayList<>();
     // Air-padded road, decorative raised post, and a genuine covered underground room.
     for(int x=0;x<7;x++)for(int z=0;z<7;z++)for(int y=0;y<5;y++){
      var state=Blocks.AIR.defaultBlockState();
      if(x==0&&y==2)state=Blocks.STONE_BRICKS.defaultBlockState();
      if(x==1&&z==3&&y==4)state=Blocks.OAK_FENCE.defaultBlockState();
      if(x>=3&&x<=5&&z>=2&&z<=4&&(y==0||y==4||x==3||x==5||z==2||z==4))state=Blocks.STONE_BRICKS.defaultBlockState();
      cells.add(new BweCatalog.Cell(new BlockPos(x,y,z),state,null));
     }
     var entry=new BweCatalog.Entry("test:padding","Padding regression","",7,5,7,3,List.of());
     var plan=new BweCatalog.Blueprint(cells,List.of(),List.of(),2);
     h.assertTrue(SurfaceBuilder.start(w,entry,plan,aerialBase,aerialBase.getY()+1,1,UUID.randomUUID())==null,"Padding build rejected");phase=9;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==9){
     BlockPos origin=aerialBase.offset(-3,-2,-3);
     h.assertTrue(w.getBlockState(origin.offset(0,2,2)).is(Blocks.STONE_BRICKS),"Road changed by terrain repair");
     h.assertTrue(w.getBlockState(origin.offset(1,2,2)).is(Blocks.GRASS_BLOCK),"Air padding excavated grass beside road");
     h.assertTrue(w.getBlockState(origin.offset(1,2,3)).is(Blocks.GRASS_BLOCK),"Air padding excavated terrain below raised decoration");
     h.assertTrue(w.getBlockState(origin.offset(4,2,3)).isAir(),"Underground interior was filled");
     h.assertTrue(w.getBlockState(origin.offset(4,0,3)).is(Blocks.STONE_BRICKS),"Basement floor changed");
     h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Padding undo rejected");phase=10;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==10){
     List<BweCatalog.Cell> cropCells=new ArrayList<>();
     for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=0;y<3;y++){
      var state=Blocks.AIR.defaultBlockState();
      if(y==0&&(x==0||x==4))state=Blocks.STONE_BRICKS.defaultBlockState();
      if(y==0&&x==2&&z==2)state=Blocks.GRASS.defaultBlockState();
      cropCells.add(new BweCatalog.Cell(new BlockPos(x,y,z),state,null));
     }
     var cropped=new BweCatalog.Entry("bwe_extra:coliseo_natural","cropped","",5,3,5,1,List.of());
     var cropPlan=new BweCatalog.Blueprint(cropCells,List.of(),List.of(),-1);
     h.assertTrue(SurfaceBuilder.start(w,cropped,cropPlan,aerialBase,aerialBase.getY()+1,1,UUID.randomUUID())==null,"Cropped build rejected");
     phase=11;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==11){
     for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.assertTrue(w.getBlockState(aerialBase.offset(x,0,z)).is(Blocks.GRASS_BLOCK),"Cropped courtyard floor has a hole or relief");
     h.assertTrue(w.getBlockState(aerialBase.offset(-2,1,0)).is(Blocks.STONE_BRICKS),"Cropped architecture buried");
     h.assertTrue(w.getBlockState(aerialBase.above()).is(Blocks.GRASS),"Cropped plant replaced soil instead of sitting on it");
     h.assertTrue(w.getBlockState(aerialBase.offset(1,1,0)).isAir(),"Terrain remains above arena floor");
     h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Cropped undo rejected");phase=12;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==12){
     h.assertTrue(SurfaceBuilder.start(w,market,aerialBase,aerialBase.getY()+1,1,UUID.randomUUID())==null,"Market placement rejected");
     phase=13;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==13){
     h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE_BRICKS),"Market floor is not at selected terrain Y");
     h.assertTrue(w.getBlockState(aerialBase.above()).isAir(),"Market buried under generated terrain");
     h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Market undo rejected");phase=14;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==14){
     var plan=BweCatalog.compose(w,fortress);var cells=new ArrayList<>(plan.cells());
     cells.add(new BweCatalog.Cell(new BlockPos(2,5,2),Blocks.OAK_PLANKS.defaultBlockState(),null));
     plan=new BweCatalog.Blueprint(cells,List.of(),plan.supports(),plan.surfaceY());
     h.assertTrue(SurfaceBuilder.start(w,fortress,plan,aerialBase,aerialBase.getY()+1,1,UUID.randomUUID())==null,"Citadel placement rejected");
     phase=15;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==15){
     h.assertTrue(w.getBlockState(aerialBase).is(Blocks.STONE_BRICKS),"Citadel lower base still buried");
     h.assertTrue(w.getBlockState(aerialBase.above(5)).is(Blocks.OAK_PLANKS)&&w.getBlockState(aerialBase.offset(1,5,0)).isAir(),"Lower wooden entrance covered by terrain");
     h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Citadel undo rejected");phase=16;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==16){
     List<BweCatalog.Cell> cells=new ArrayList<>();
     for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=0;y<24;y++){
      var state=Blocks.AIR.defaultBlockState();
      if(y==18)state=Blocks.STONE_BRICKS.defaultBlockState();
      else if(y<18&&(x==0||x==4)&&(z==0||z==4))state=Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
      cells.add(new BweCatalog.Cell(new BlockPos(x,y,z),state,null));
     }
     w.setBlock(aerialBase.offset(1,-8,1),Blocks.GOLD_BLOCK.defaultBlockState(),50);
     var plan=new BweCatalog.Blueprint(cells,List.of(),List.of(),BweCatalog.surfaceReference(fisher,0));
     h.assertTrue(SurfaceBuilder.start(w,fisher,plan,aerialBase,aerialBase.getY()+1,1,UUID.randomUUID())==null,"Fisher water placement rejected");
     phase=17;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==17){
     for(int depth=0;depth<=17;depth++)h.assertTrue(w.getBlockState(aerialBase.below(depth)).is(Blocks.WATER),"Air or earth remains between fisher pillars at depth "+depth);
     h.assertTrue(w.getBlockState(aerialBase.offset(-2,-17,-2)).is(Blocks.STRIPPED_SPRUCE_LOG),"Fisher pillar was replaced with water");
     h.assertTrue(w.getBlockState(aerialBase.above()).is(Blocks.STONE_BRICKS)&&w.getBlockState(aerialBase.above(2)).isAir(),"Fisher deck not one block above waterline");
     h.assertTrue(w.getBlockState(aerialBase.offset(1,-8,1)).is(Blocks.WATER),"Fisher underwater terrain not cleared");
     h.assertTrue(SurfaceBuilder.ground(w,aerialBase,fisher,1)==aerialBase.getY()+1,"Fisher automatic waterline mismatch");
     h.assertTrue(SurfaceBuilder.restoreTerrain(w)==null,"Fisher undo rejected");phase=18;h.runAfterDelay(1,()->this.run());return;
    }
    if(phase==18)h.assertTrue(w.getBlockState(aerialBase.offset(1,-8,1)).is(Blocks.GOLD_BLOCK),"Fisher undo failed to recover original submerged terrain");
    System.out.println("[BWE-REGRESSION] PASS bazaar/citadel ground references with stale metadata, fisher water between pillars and deck clearance, water undo; cropped bases/courtyard, bounded mansion decorations and spawners; native Lootr chest/barrel per-player rewards, no refill on reopen, 256 seeds with empty/modded loot sources, barrel restore; rotation command, four-turn identity, entities, exact footprint packet, terrain padding and basement preservation; previous height/Lootr/undo/water/airship checks");h.succeed();
   }catch(Exception e){BweBackup.close();h.fail(e.toString());}}
  });
 }
 private static void validateExpansion(GameTestHelper h)throws Exception{
  h.assertTrue(BweCatalog.passiveState(Blocks.NETHER_PORTAL.defaultBlockState()).isAir()&&BweCatalog.passiveState(Blocks.SPAWNER.defaultBlockState()).isAir(),"Imported portals or spawners active");
  h.assertTrue(BweCatalog.passiveState(Blocks.TNT.defaultBlockState()).is(Blocks.RED_TERRACOTTA)&&BweCatalog.passiveState(Blocks.OBSERVER.defaultBlockState()).is(Blocks.STONE_BRICKS),"Imported traps/clock still active");
  try(var input=RegressionGameTests.class.getResourceAsStream("/expansion-validation.json")){
   var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
   var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
   for(var raw:root.getAsJsonArray("states")){
    var st=raw.getAsJsonObject();var id=new net.minecraft.resources.ResourceLocation(st.get("Name").getAsString());
    h.assertTrue(registry.containsKey(id),"Unknown imported block "+id);var block=registry.get(id);
    h.assertTrue(!id.getPath().contains("spawner")&&!id.getPath().contains("command_block")&&!id.getPath().contains("sign")&&!id.getPath().contains("banner"),"Active/heavy decoration remains "+id);
    if(st.has("Properties"))for(var prop:st.getAsJsonObject("Properties").entrySet()){
     var p=block.getStateDefinition().getProperty(prop.getKey());h.assertTrue(p!=null&&p.getValue(prop.getValue().getAsString()).isPresent(),"Invalid imported property "+st);
    }
   }
   h.assertTrue(root.getAsJsonArray("entries").size()>100,"Expansion catalogue unexpectedly incomplete");
   for(var raw:root.getAsJsonArray("entries")){
    var e=raw.getAsJsonObject();h.assertTrue(e.get("cells").getAsInt()<=1000000&&e.get("pieces").getAsInt()<=128,"Expansion exceeds budget");
    for(var v:e.getAsJsonArray("size"))h.assertTrue(v.getAsInt()>0&&v.getAsInt()<=256,"Expansion outside bounds");
   }
  }
  var neutral=new BweCatalog.Entry("bwe_tt:village_test","TT","Sin mobs",5,3,5,1,List.of(new BweCatalog.Piece("ctov:village/path/regression",0,0,0,0)));
  var hostile=new BweCatalog.Entry("bwe_itp:bastille","ITP","Hostil · Sin mobs",5,3,5,1,neutral.pieces());
  h.assertTrue(BweCatalog.group(neutral).equals("TT")&&!BweCatalog.hostile(neutral),"Neutral category classification wrong");
  h.assertTrue(BweCatalog.group(hostile).equals("ITP")&&BweCatalog.hostile(hostile),"Hostile category classification wrong");
  h.assertTrue(BweCatalog.compose(h.getLevel(),neutral).entities().isEmpty(),"New import has population");
 }
 private static void mansionBudget(GameTestHelper h){
  Map<Long,BweCatalog.Cell> cells=new LinkedHashMap<>();List<BweCatalog.Spawn> population=new ArrayList<>();
  for(int i=0;i<598;i++){CompoundTag t=new CompoundTag();t.putString("id","minecraft:painting");t.put("Passengers",new ListTag());population.add(new BweCatalog.Spawn(t,i%80,4+(i/80)*4,i%84,0));}
  for(String kind:List.of("armor_stand","item_frame"))for(int i=0;i<50;i++){CompoundTag t=new CompoundTag();t.putString("id","minecraft:"+kind);population.add(new BweCatalog.Spawn(t,i*4,3,i*4,0));}
  for(int i=0;i<300;i++){CompoundTag t=new CompoundTag();t.putString("id","minecraft:pillager");int x=(i%4)*30;BlockPos pos=new BlockPos(x,2,2);
   population.add(new BweCatalog.Spawn(t,x+0.5,2,2.5,0));
   for(int y=1;y<=3;y++){BlockPos at=new BlockPos(x,y,2);cells.put(at.asLong(),new BweCatalog.Cell(at,(y==1?Blocks.STONE:Blocks.AIR).defaultBlockState(),null));}
  }
  BweCatalog.lightMansion(h.getLevel(),cells,population);
  h.assertTrue(population.size()==40,"Mansion decoration cap or sampling incorrect");
  h.assertTrue(population.stream().noneMatch(e->e.nbt().contains("Passengers")||e.nbt().getString("id").equals("minecraft:pillager")),"Mansion imported mobs remain");
  var spawners=cells.values().stream().filter(c->c.state().is(Blocks.SPAWNER)).toList();
  h.assertTrue(spawners.size()==4,"Mansion spawners not bounded or missing");
  for(var c:spawners){
   var be=new net.minecraft.world.level.block.entity.SpawnerBlockEntity(c.pos(),c.state());be.load(c.nbt());var saved=be.saveWithFullMetadata();
   h.assertTrue(saved.getShort("SpawnCount")==1&&saved.getShort("MaxNearbyEntities")==2&&saved.getShort("MinSpawnDelay")==600,"Spawner load lost low activity limits");
   h.assertTrue(saved.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:pillager"),"Spawner mob data invalid");
  }
 }
 private static void lootRegression(GameTestHelper h,BlockPos pos)throws Exception{
  var w=h.getLevel();var be=w.getBlockEntity(pos);var nbt=be.saveWithFullMetadata();be.load(nbt);
  h.assertTrue(nbt.getString("LootTable").equals(BweLootr.SUPPLY.toString()),"Supply table not persisted");
  var a=net.minecraftforge.common.util.FakePlayerFactory.get(w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LootPlayerA"));
  var b=net.minecraftforge.common.util.FakePlayerFactory.get(w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LootPlayerB"));
  var first=lootrInventory(w,be,a);h.assertTrue(!first.isEmpty(),"First native Lootr inventory empty");first.clearContent();
  var second=lootrInventory(w,be,b);h.assertTrue(!second.isEmpty(),"Second player did not get independent loot");
  h.assertTrue(lootrInventory(w,be,a).isEmpty(),"Reopening refilled consumed personal inventory");
  var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(w).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(pos)).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
  var table=w.getServer().getLootData().getLootTable(BweLootr.SUPPLY);boolean bonus=false;
  var empty=new net.minecraft.resources.ResourceLocation("bwe_regression","chests/empty_source");
  h.assertTrue(BweLootr.sources(w).contains(empty),"Empty modded regression source not loaded");
  h.assertTrue(w.getServer().getLootData().getLootTable(empty).getRandomItems(params,1).isEmpty(),"Empty source is not empty");
  for(int seed=1;seed<=256;seed++){var items=table.getRandomItems(params,seed);h.assertTrue(items.stream().anyMatch(i->!i.isEmpty()),"Supply table generated empty loot");if(items.stream().anyMatch(i->i.is(Items.NETHER_STAR)))bonus=true;}
  h.assertTrue(bonus,"Modded source rewards were discarded");
 }
 private static net.minecraft.world.Container lootrInventory(net.minecraft.server.level.ServerLevel w,net.minecraft.world.level.block.entity.BlockEntity be,net.minecraft.server.level.ServerPlayer player)throws Exception{
  Class<?> filler=Class.forName("noobanidus.mods.lootr.api.LootFiller");
  var unpack=be.getClass().getMethod("unpackLootTable",net.minecraft.world.entity.player.Player.class,net.minecraft.world.Container.class,net.minecraft.resources.ResourceLocation.class,long.class);
  Object callback=java.lang.reflect.Proxy.newProxyInstance(filler.getClassLoader(),new Class[]{filler},(proxy,method,args)->{
   net.minecraft.world.Container inventory=null;net.minecraft.resources.ResourceLocation table=null;net.minecraft.world.entity.player.Player who=null;long seed=Long.MIN_VALUE;
   for(Object arg:args){if(arg instanceof net.minecraft.world.Container c)inventory=c;else if(arg instanceof net.minecraft.resources.ResourceLocation id)table=id;else if(arg instanceof net.minecraft.world.entity.player.Player p)who=p;else if(arg instanceof Long n)seed=n;}
   unpack.invoke(be,who,inventory,table,seed);return null;
  });
  UUID id=(UUID)be.getClass().getMethod("getTileId").invoke(be);
  for(var method:Class.forName("noobanidus.mods.lootr.data.DataStorage").getMethods())if(method.getName().equals("getInventory")&&method.getParameterCount()==6&&method.getParameterTypes()[1]==UUID.class&&method.getParameterTypes()[5]==filler)
   return (net.minecraft.world.Container)method.invoke(null,w,id,be.getBlockPos(),player,be,callback);
  throw new IllegalStateException("Native Lootr inventory API unavailable");
 }
 private static void rotationAndOutline(GameTestHelper h,BweCatalog.Entry original,BweCatalog.Blueprint plan){
  h.assertTrue(original.title().startsWith("BV"),"Existing catalogue did not abbreviate title");
  h.assertTrue(BweCatalog.shortTitle("Grand Capitals - Taiga").equals("GC - Taiga"),"GC abbreviation failed");
  var entry=original;var current=plan;
  for(int i=0;i<4;i++){var next=BweCatalog.turn(entry,current);entry=BweCatalog.turned(entry);current=next;}
  h.assertTrue(entry.sizeX()==original.sizeX()&&entry.sizeZ()==original.sizeZ()&&current.surfaceY()==plan.surfaceY(),"Four turns changed dimensions or height");
  h.assertTrue(current.cells().equals(plan.cells())&&current.supports().equals(plan.supports()),"Four turns changed block states/coordinates");
  for(int i=0;i<plan.entities().size();i++){var a=plan.entities().get(i);var b=current.entities().get(i);h.assertTrue(Math.abs(a.x()-b.x())<1e-6&&Math.abs(a.z()-b.z())<1e-6&&a.nbt().equals(b.nbt()),"Four turns changed entity or hanging anchor");}
  var rectangular=new BweCatalog.Entry("test:rotation","","",3,4,7,2,List.of());var single=new BweCatalog.Blueprint(List.of(new BweCatalog.Cell(new BlockPos(1,2,5),Blocks.OAK_STAIRS.defaultBlockState(),null)),List.of(),List.of(),1);
  var turned=BweCatalog.turn(rectangular,single);h.assertTrue(turned.cells().get(0).pos().equals(new BlockPos(1,2,1))&&BweCatalog.turned(rectangular).sizeX()==7,"Non-square rotation incorrect");
  Set<Long> triangle=new HashSet<>();for(int x=0;x<8;x++)for(int z=0;z<=x;z++)triangle.add(VillageFootprint.key(x,z));
  var edges=VillageFootprint.outline(triangle);Set<String> expected=new HashSet<>(),actual=new HashSet<>();
  for(long k:triangle){int x=(int)(k>>32),z=(int)k;if(!triangle.contains(VillageFootprint.key(x,z-1)))expected.add("h:"+x+":"+z);if(!triangle.contains(VillageFootprint.key(x,z+1)))expected.add("h:"+x+":"+(z+1));if(!triangle.contains(VillageFootprint.key(x-1,z)))expected.add("v:"+x+":"+z);if(!triangle.contains(VillageFootprint.key(x+1,z)))expected.add("v:"+(x+1)+":"+z);}
  for(var e:edges){if(e.z0()==e.z1())for(int x=e.x0();x<e.x1();x++)actual.add("h:"+x+":"+e.z0());else for(int z=e.z0();z<e.z1();z++)actual.add("v:"+e.x0()+":"+z);}
  h.assertTrue(expected.equals(actual)&&edges.size()>4,"Outline lost actual terrain boundary");
  var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{var border=new BweNetwork.Border("minecraft:overworld",true,0,0,8,8,70,edges);border.encode(buf);h.assertTrue(border.equals(BweNetwork.Border.decode(buf)),"Irregular contour packet mismatch");}finally{buf.release();}
 }
 private static void validateBiomes(GameTestHelper h)throws Exception{
  try(var input=RegressionGameTests.class.getResourceAsStream("/biome-validation.json")){
   var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
   for(var raw:root.getAsJsonArray("states")){
    var st=raw.getAsJsonObject();var key=new net.minecraft.resources.ResourceLocation(st.get("Name").getAsString());h.assertTrue(registry.containsKey(key),"Unknown new biome block: "+key);var block=registry.get(key);
    if(st.has("Properties"))for(var prop:st.getAsJsonObject("Properties").entrySet()){var p=block.getStateDefinition().getProperty(prop.getKey());h.assertTrue(p!=null&&p.getValue(prop.getValue().getAsString()).isPresent(),"Invalid biome state property: "+st);}
   }
   for(var id:root.getAsJsonArray("entities"))h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(new net.minecraft.resources.ResourceLocation(id.getAsString())),"Unknown new entity: "+id);
   for(var id:root.getAsJsonArray("painting_variants"))h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.PAINTING_VARIANT.containsKey(new net.minecraft.resources.ResourceLocation(id.getAsString())),"Unknown painting variant: "+id);
   h.assertTrue(root.getAsJsonArray("entries").size()==35,"New layout count differs");
   for(var raw:root.getAsJsonArray("entries")){var e=raw.getAsJsonObject();h.assertTrue(e.get("cells").getAsInt()<=1_000_000&&e.get("pieces").getAsInt()<=128,"Imported layout exceeds construction budget");for(var size:e.getAsJsonArray("size"))h.assertTrue(size.getAsInt()>0&&size.getAsInt()<=256,"Imported layout exceeds dimensions");}
  }
 }
 private static void command(GameTestHelper h,net.minecraft.server.level.ServerPlayer player,String command){
  int result=player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),command);
  h.assertTrue(result==1,"Command failed: "+command);
 }
}
