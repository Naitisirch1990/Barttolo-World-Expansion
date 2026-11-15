package net.barttolo.bwe;

import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Optional Lootr integration via its registered blocks and vanilla virtual setLootTable API. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=BarttoloWorldExpansion.MOD_ID)
public final class BweLootr {
    public static final ResourceLocation SUPPLY=new ResourceLocation(BarttoloWorldExpansion.MOD_ID,"chests/village_supply");
    private static net.minecraft.server.MinecraftServer cachedServer;
    private static List<ResourceLocation> cachedSources;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void reload(net.minecraftforge.event.OnDatapackSyncEvent event){if(event.getPlayer()==null)clearCache();}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){clearCache();}
    private static void clearCache(){cachedServer=null;cachedSources=null;}
    public static boolean convertible(BlockState state){return state.is(Blocks.CHEST)||state.is(Blocks.TRAPPED_CHEST)||state.is(Blocks.BARREL);}
    public static List<ResourceLocation> tables(ServerLevel w){
        if(w.getServer().getLootData().getLootTable(SUPPLY)==LootTable.EMPTY)throw new IllegalStateException("Falta la tabla de provisiones BWE; instala el JAR completo");
        return List.of(SUPPLY);
    }
    public static boolean available(){return BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("lootr:lootr_chest"));}
    public static List<ResourceLocation> sources(ServerLevel w){
        if(cachedServer==w.getServer()&&cachedSources!=null)return cachedSources;
        TreeSet<ResourceLocation> modded=new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        for(var file:w.getServer().getResourceManager().listResources("loot_tables",r->r.getPath().endsWith(".json")&&(r.getPath().contains("/chests/")||r.getPath().contains("/chest/"))).keySet()){
            if(file.getNamespace().equals("minecraft")||file.getNamespace().equals(BarttoloWorldExpansion.MOD_ID))continue;
            String path=file.getPath();ResourceLocation id=new ResourceLocation(file.getNamespace(),path.substring("loot_tables/".length(),path.length()-5));
            LootTable t=w.getServer().getLootData().getLootTable(id);
            if(t!=LootTable.EMPTY&&t.getParamSet()==LootContextParamSets.CHEST)modded.add(id);
        }
        if(!modded.isEmpty()){cachedServer=w.getServer();return cachedSources=List.copyOf(modded);}
        ArrayList<ResourceLocation> fallback=new ArrayList<>();
        for(String name:List.of("simple_dungeon","abandoned_mineshaft","desert_pyramid","jungle_temple","buried_treasure","shipwreck_treasure","stronghold_library","woodland_mansion")){
            ResourceLocation id=new ResourceLocation("minecraft","chests/"+name);
            if(w.getServer().getLootData().getLootTable(id)!=LootTable.EMPTY)fallback.add(id);
        }
        cachedServer=w.getServer();return cachedSources=List.copyOf(fallback);
    }
    public static boolean convert(ServerLevel w,BlockPos pos,List<ResourceLocation> tables,BiConsumer<BlockPos,BlockState> writer){
        BlockState old=w.getBlockState(pos);String target=old.is(Blocks.TRAPPED_CHEST)?"lootr:lootr_trapped_chest":old.is(Blocks.CHEST)?"lootr:lootr_chest":old.is(Blocks.BARREL)?"lootr:lootr_barrel":null;
        if(target==null||tables.isEmpty()||!available())return false;
        ResourceLocation id=new ResourceLocation(target);if(!BuiltInRegistries.BLOCK.containsKey(id))return false;
        BlockState next=BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for(Property<?> p:old.getProperties())if(next.hasProperty(p))next=copy(next,old,p);
        if(next.hasProperty(BarrelBlock.OPEN))next=next.setValue(BarrelBlock.OPEN,false);
        var previous=w.getBlockEntity(pos);var metadata=previous==null?null:previous.saveWithFullMetadata();
        writer.accept(pos,next);
        if(!(w.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container))throw new IllegalStateException("Lootr no expone un contenedor compatible en "+pos);
        if(metadata!=null&&metadata.contains("CustomName")){
            var name=net.minecraft.network.chat.Component.Serializer.fromJson(metadata.getString("CustomName"));if(name!=null)container.setCustomName(name);
        }
        container.setLootTable(tables.get(w.random.nextInt(tables.size())),w.random.nextLong());container.setChanged();
        w.sendBlockUpdated(pos,old,next,3);return true;
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState next,BlockState old,Property<T> p){return next.setValue(p,old.getValue(p));}
}
