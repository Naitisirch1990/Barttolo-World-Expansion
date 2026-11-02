package net.barttolo.bwe;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Runs only in the separate GameTest source set, never packaged in the production JAR. */
@GameTestHolder(BarttoloWorldExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstructionGameTests {
    @GameTest(template="empty", timeoutTicks=18000)
    public static void allOriginalsReplaceAndUndo(GameTestHelper helper) {
        ServerLevel world=helper.getLevel();
        BlockPos anchor=new BlockPos(512,80,512);
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,world.getServer());
        world.setDefaultSpawnPos(new BlockPos(0,80,0),0);
        for(int cx=(anchor.getX()-45)>>4;cx<=(anchor.getX()+45)>>4;cx++)
            for(int cz=(anchor.getZ()-45)>>4;cz<=(anchor.getZ()+45)>>4;cz++) {
                world.setChunkForced(cx,cz,true);
                world.getChunk(cx,cz);
            }
        for(int x=-40;x<=40;x++) for(int z=-40;z<=40;z++)
            world.setBlock(anchor.offset(x,-1,z),Blocks.STONE.defaultBlockState(),50);
        // The old release stopped here when actual state differed from both BEFORE and AFTER.
        BlockPos stale=anchor.above();
        world.setBlock(stale,Blocks.GLASS_PANE.defaultBlockState(),50);
        var old=new WorldData.StoredBlock(stale.asLong(),WorldData.encode(Blocks.AIR.defaultBlockState()),
            WorldData.encode(Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH,true)));
        WorldData.get(world).setUndo(new WorldData.Undo(world.dimension().location().toString(),"legacy_conflict",List.of(old),List.of(),true));
        var recovered=BuildEngine.undo(world);
        helper.assertTrue(recovered.ok(),"Legacy conflict rejected: "+recovered.message());
        var kinds=new ArrayList<>(BuildEngine.KINDS);Collections.sort(kinds);
        helper.runAfterDelay(3,new Runnable() {
            int index; boolean restoring; boolean started;
            List<WorldData.StoredBlock> before;
            @Override public void run() {
                try {
                    if(BuildEngine.busy()) { helper.runAfterDelay(1,this);return; }
                    if(!started && index==0)
                        helper.assertTrue(world.getBlockState(stale).isAir(),"Legacy conflicting state not restored");
                    if(index==kinds.size()) { helper.succeed();return; }
                    String kind=kinds.get(index);
                    if(!started) {
                        helper.assertTrue(WorldData.get(world).undo()==null,"Backup slot not cleared");
                        // A chest, artificial tower, slime and dropped item must not block construction.
                        for(int y=0;y<20;y++) world.setBlock(anchor.offset(0,y,0),Blocks.OBSIDIAN.defaultBlockState(),50);
                        world.setBlock(anchor.offset(2,0,2),Blocks.CHEST.defaultBlockState(),50);
                        Slime slime=EntityType.SLIME.create(world);
                        helper.assertTrue(slime!=null,"Could not create slime");
                        slime.setSize(4,true);slime.moveTo(anchor.getX()+.5,anchor.getY()+2,anchor.getZ()+.5,0,0);
                        world.addFreshEntity(slime);
                        ItemEntity item=new ItemEntity(world,anchor.getX()+1.5,anchor.getY()+1,anchor.getZ()+1.5,new ItemStack(Items.DIRT));
                        world.addFreshEntity(item);
                        var plan=new BuildEngine.Plan(kind,"",anchor,world.dimension().location().toString(),80,
                            BuildEngine.radius(kind),BuildEngine.radius(kind),System.currentTimeMillis(),7);
                        var simulation=BuildEngine.simulate(world,plan);
                        helper.assertTrue(simulation.ok(),kind+" simulation: "+simulation.message());
                        helper.assertTrue(!slime.isRemoved() && !item.isRemoved(),"Simulation removed entities");
                        var build=BuildEngine.build(world,plan,new UUID(0,0));
                        helper.assertTrue(build.ok(),kind+" build: "+build.message());
                        before=new ArrayList<>(WorldData.get(world).undo().blocks());
                        helper.assertTrue(before.size()>386,"Full volume backup missing: "+kind);
                        started=true;
                    } else if(!restoring) {
                        helper.assertTrue(WorldData.get(world).undo().completed(),"Build failed: "+kind);
                        helper.assertTrue(world.getEntitiesOfClass(Slime.class,
                            new net.minecraft.world.phys.AABB(anchor.offset(-40,-1,-40),anchor.offset(41,30,41))).isEmpty(),"Slime was not removed");
                        helper.assertTrue(world.getEntitiesOfClass(ItemEntity.class,
                            new net.minecraft.world.phys.AABB(anchor.offset(-40,-1,-40),anchor.offset(41,30,41))).isEmpty(),"Dropped items remain");
                        helper.assertTrue(BuildEngine.canFinalize(world).ok(),"Cannot finalize completed build");
                        if(kind.equals("refugio")) {
                            helper.assertTrue(world.getBlockState(anchor.offset(-4,0,-4)).is(Blocks.OAK_LOG),"House bottom wall missing");
                            helper.assertTrue(world.getBlockState(anchor.offset(-2,1,0)).is(Blocks.RED_BED),"Bed lost during placement");
                        }
                        // Subsequent physics/player-like changes must not block undo.
                        world.setBlock(anchor.above(2),Blocks.GOLD_BLOCK.defaultBlockState(),50);
                        var undo=BuildEngine.undo(world);
                        helper.assertTrue(undo.ok(),kind+" undo: "+undo.message());
                        restoring=true;
                    } else {
                        helper.assertTrue(WorldData.get(world).undo()==null,"Undo failed: "+kind);
                        for(var block:before)
                            helper.assertTrue(WorldData.encode(world.getBlockState(BlockPos.of(block.pos()))).equals(block.before()),
                                kind+" incomplete restore at "+BlockPos.of(block.pos()));
                        System.out.println("[BWE-GAMETEST] PASS "+kind+" | "+before.size()+" states restored");
                        // The next iteration replaces the exact same previously occupied volume.
                        index++;started=false;restoring=false;
                    }
                    helper.runAfterDelay(1,this);
                } catch(Exception error) { helper.fail(error.toString()); }
            }
        });
    }
}
