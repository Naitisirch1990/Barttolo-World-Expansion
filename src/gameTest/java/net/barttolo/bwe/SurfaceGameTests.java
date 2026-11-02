package net.barttolo.bwe;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.*;

@GameTestHolder(BarttoloWorldExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurfaceGameTests {
    @GameTest(batch="imports",template="empty",timeoutTicks=12000)
    public static void archiveRotationGroundAndRemoval(GameTestHelper helper)throws Exception {
        ServerLevel w=helper.getLevel();BlockPos center=new BlockPos(1024,80,1024),source=new BlockPos(1100,80,1100);
        for(int x=61;x<=70;x++)for(int z=61;z<=70;z++){w.setChunkForced(x,z,true);w.getChunk(x,z);}
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)w.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),50);
        for(int x=0;x<7;x++)for(int z=0;z<7;z++)w.setBlock(source.offset(x,0,z),Blocks.OAK_PLANKS.defaultBlockState(),50);
        w.setBlock(source.offset(1,1,1),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT),50);
        // Default bed points north; matching head is one block to the north.
        w.setBlock(source.offset(1,1,0),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD),50);
        w.setBlock(source.offset(3,1,3),Blocks.CHEST.defaultBlockState(),50);
        var be=w.getBlockEntity(source.offset(3,1,3));CompoundTag chest=be.saveWithFullMetadata();chest.putString("CustomName","\"BWE fixture\"");be.load(chest);
        StructureTemplate t=new StructureTemplate();t.fillFromWorld(w,source,new Vec3i(7,6,7),false,Blocks.STRUCTURE_VOID);
        CompoundTag raw=t.save(new CompoundTag());
        Path dir=FMLPaths.CONFIGDIR.get().resolve("bwe");Files.createDirectories(dir);
        try(var zip=new ZipOutputStream(Files.newOutputStream(dir.resolve("ctov.jar")))){
            zip.putNextEntry(new ZipEntry("data/ctov/structures/fixture.nbt"));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(raw,bytes);zip.write(bytes.toByteArray());zip.closeEntry();
        }
        Files.writeString(dir.resolve("catalogo.json"),"""
          {"format":1,"entries":[{"id":"ctov:fixture","title":"Imported fixture","description":"Two rotated native pieces","sizeX":20,"sizeY":6,"sizeZ":8,"ground":1,"pieces":[{"template":"ctov:fixture","x":0,"y":0,"z":0,"rotation":0},{"template":"ctov:fixture","x":15,"y":0,"z":0,"rotation":1}]}]}
          """);
        BweCatalog.reset();var entry=BweCatalog.find("ctov:fixture");helper.assertTrue(entry!=null,"Catalog manifest not loaded");
        int ground=SurfaceBuilder.ground(w,center,entry,5);helper.assertTrue(ground==80,"Automatic ground selected "+ground);
        for(int y=0;y<12;y++)w.setBlock(center.offset(0,y,0),Blocks.OBSIDIAN.defaultBlockState(),50);
        var pig=EntityType.PIG.create(w);pig.moveTo(1024.5,80,1024.5,0,0);w.addFreshEntity(pig);
        var item=new ItemEntity(w,1024.5,81,1024.5,new ItemStack(Items.DIRT));w.addFreshEntity(item);
        String error=SurfaceBuilder.start(w,entry,center,ground,5,new UUID(0,0));helper.assertTrue(error==null,"Start rejected: "+error);
        helper.runAfterDelay(1,new Runnable(){boolean removing;
            public void run(){
                try{
                    if(SurfaceBuilder.busy()){helper.runAfterDelay(1,()->this.run());return;}
                    BlockPos origin=new BlockPos(1014,79,1020);
                    if(!removing){
                        helper.assertTrue(pig.isRemoved()&&item.isRemoved(),"Old entities not purged");
                        helper.assertTrue(w.getBlockState(center.above(10)).isAir(),"Tower not cleared");
                        helper.assertTrue(w.getBlockState(origin.offset(1,1,1)).is(Blocks.RED_BED),"Native bed missing");
                        helper.assertTrue(w.getBlockState(origin.offset(14,1,1)).is(Blocks.RED_BED),"Rotated native bed missing");
                        var actual=w.getBlockEntity(origin.offset(3,1,3));
                        helper.assertTrue(actual!=null && actual.saveWithFullMetadata().getString("CustomName").contains("BWE fixture"),"Block entity NBT not installed");
                        helper.assertTrue(SurfaceBuilder.remove(w)==null,"Cannot remove latest structure");removing=true;
                    }else{
                        helper.assertTrue(w.getBlockState(origin.offset(1,1,1)).isAir(),"Native structure not removed");
                        helper.assertTrue(w.getBlockState(origin.offset(14,1,1)).isAir(),"Rotated structure not removed");
                        System.out.println("[BWE-IMPORT-TEST] PASS archive, rotation, ground, block-entity NBT, purge and removal");helper.succeed();return;
                    }
                    helper.runAfterDelay(1,()->this.run());
                }catch(Exception failure){helper.fail(failure.toString());}
            }
        });
    }
}
