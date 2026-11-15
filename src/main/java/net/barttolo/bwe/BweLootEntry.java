package net.barttolo.bwe;

import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Only BWE's own supply table calls this entry; other world loot tables remain unchanged. */
public final class BweLootEntry extends LootPoolSingletonContainer {
    public static final DeferredRegister<LootPoolEntryType> TYPES=DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE,BarttoloWorldExpansion.MOD_ID);
    public static final RegistryObject<LootPoolEntryType> TYPE=TYPES.register("available_chest",()->new LootPoolEntryType(new Serializer()));
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    private BweLootEntry(int weight,int quality,LootItemCondition[] conditions,LootItemFunction[] functions){super(weight,quality,conditions,functions);}
    @Override public LootPoolEntryType getType(){return TYPE.get();}
    @Override protected void createItemStack(Consumer<ItemStack> output,LootContext context){
        if(ACTIVE.get())return;
        var sources=BweLootr.sources(context.getLevel());if(sources.isEmpty())return;
        var id=sources.get(context.getRandom().nextInt(sources.size()));
        ACTIVE.set(true);
        try{
            var items=new java.util.ArrayList<ItemStack>();
            context.getLevel().getServer().getLootData().getLootTable(id).getRandomItems(context,items::add);
            items.stream().filter(item->!item.isEmpty()).forEach(output);
        }catch(RuntimeException error){
            com.mojang.logging.LogUtils.getLogger().warn("[BWE] Optional loot table {} failed; using BWE supplies",id,error);
        }finally{ACTIVE.remove();}
    }
    public static final class Serializer extends LootPoolSingletonContainer.Serializer<BweLootEntry>{
        @Override protected BweLootEntry deserialize(JsonObject json,JsonDeserializationContext context,int weight,int quality,LootItemCondition[] conditions,LootItemFunction[] functions){return new BweLootEntry(weight,quality,conditions,functions);}
    }
}
