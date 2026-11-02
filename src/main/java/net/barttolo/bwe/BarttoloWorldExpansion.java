package net.barttolo.bwe;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Manual placement in existing chunks. No audit or worldgen commands. */
@Mod(BarttoloWorldExpansion.MOD_ID)
public final class BarttoloWorldExpansion {
    public static final String MOD_ID="barttoloworldexpansion";
    private record Selection(BweCatalog.Entry entry,BlockPos center,String dimension,int ground,int margin) {}
    private static final Map<UUID,Selection> SELECTED=new HashMap<>();
    private static final Map<UUID,BlockPos> SITES=new HashMap<>();
    private static final Map<UUID,Integer> MARGINS=new HashMap<>();
    public BarttoloWorldExpansion(){BweNetwork.register();MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void onTick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.END){BuildEngine.serverTick();SurfaceBuilder.tick();}
    }
    @SubscribeEvent public void onStopped(ServerStoppedEvent event){
        BuildEngine.resetForShutdown();SurfaceBuilder.reset();BweCatalog.reset();SELECTED.clear();SITES.clear();MARGINS.clear();
    }
    @SubscribeEvent public void onCommands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("bwe").requires(s->s.hasPermission(2)).executes(c->help(c.getSource()))
            .then(Commands.literal("ayuda").executes(c->help(c.getSource())))
            .then(Commands.literal("listar").executes(c->listing(c.getSource(),1))
                .then(Commands.argument("pagina",IntegerArgumentType.integer(1)).executes(c->listing(c.getSource(),IntegerArgumentType.getInteger(c,"pagina")))))
            .then(Commands.literal("vista").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(c->preview(c.getSource(),StringArgumentType.getString(c,"id")))))
            .then(Commands.literal("seleccionar").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(c->select(c.getSource(),StringArgumentType.getString(c,"id")))))
            .then(Commands.literal("escanear").executes(c->scan(c.getSource())))
            .then(Commands.literal("crear").executes(c->create(c.getSource())))
            .then(Commands.literal("estado").executes(c->{say(c.getSource(),SurfaceBuilder.busy()?SurfaceBuilder.status():BuildEngine.status());return 1;}))
            .then(Commands.literal("deshacer").executes(c->undo(c.getSource())))
            .then(Commands.literal("margen").executes(c->{say(c.getSource(),"Margen: "+MARGINS.getOrDefault(id(c.getSource()),5)+" bloques");return 1;})
                .then(Commands.argument("bloques",IntegerArgumentType.integer(5,10)).executes(c->{MARGINS.put(id(c.getSource()),IntegerArgumentType.getInteger(c,"bloques"));say(c.getSource(),"Margen actualizado; selecciona la construccion otra vez");return 1;})))
            .then(Commands.literal("recargar").executes(c->{if(SurfaceBuilder.busy())return fail(c.getSource(),"Espera a que termine la obra");BweCatalog.reset();return listing(c.getSource(),1);}))
            .then(Commands.literal("proteger").then(Commands.argument("radio",IntegerArgumentType.integer(1,512))
                .executes(c->{int n=WorldData.get(c.getSource().getLevel()).protect(c.getSource().getLevel().dimension().location().toString(),c.getSource().getPlayerOrException().blockPosition(),IntegerArgumentType.getInteger(c,"radio"));say(c.getSource(),"Proteccion #"+n);return 1;})))
            .then(Commands.literal("desproteger").then(Commands.argument("numero",IntegerArgumentType.integer(1))
                .executes(c->{boolean ok=WorldData.get(c.getSource().getLevel()).unprotect(IntegerArgumentType.getInteger(c,"numero"));if(!ok)return fail(c.getSource(),"Proteccion no encontrada");say(c.getSource(),"Proteccion retirada");return 1;})))
        );
    }
    private static UUID id(CommandSourceStack s)throws CommandSyntaxException{return s.getPlayerOrException().getUUID();}
    private static void say(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal("[BWE] "+text),false);}
    private static int fail(CommandSourceStack s,String text){s.sendFailure(Component.literal("[BWE] "+text));return 0;}
    private static int help(CommandSourceStack s){
        say(s,"En el lugar elegido: /bwe listar -> clic en una construccion -> alejarse -> /bwe crear");
        say(s,"/bwe estado | /bwe deshacer (RETIRA, sin restaurar terreno) | /bwe margen 5");
        say(s,"La altura del suelo se calcula automaticamente. Se limpian bloques y entidades excepto jugadores.");
        return 1;
    }
    private static int listing(CommandSourceStack s,int page){
        var entries=BweCatalog.entries();int pages=Math.max(1,(entries.size()+5)/6);
        if(entries.isEmpty())return fail(s,BweCatalog.problem()+". Usa el JAR completo con las plantillas incluidas.");
        if(page>pages)return fail(s,"Ultima pagina: "+pages);
        say(s,"Construcciones reales CTOV / Explorify: "+entries.size()+" | pagina "+page+"/"+pages);
        for(var e:entries.subList((page-1)*6,Math.min(entries.size(),page*6))){
            Component option=Component.literal("[Ver] "+e.title()+" "+e.sizeX()+" x "+e.sizeZ()+" ")
                .withStyle(style->style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe vista "+e.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Abrir esquema de la construccion"))))
                .append(Component.literal("[Elegir]").withStyle(style->style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe seleccionar "+e.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(e.description()+". Elegir en tu ubicacion."))));
            s.sendSuccess(()->option,false);
        }
        if(page>1)s.sendSuccess(()->Component.literal("[Anterior]").withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+(page-1)))),false);
        if(page<pages)s.sendSuccess(()->Component.literal("[Siguiente]").withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+(page+1)))),false);
        return 1;
    }
    private static int preview(CommandSourceStack s,String name)throws CommandSyntaxException{
        var e=BweCatalog.find(name.trim());if(e==null)return fail(s,"Construccion no encontrada. /bwe listar");
        BweNetwork.openPreview(s.getPlayerOrException(),e);return 1;
    }
    private static int scan(CommandSourceStack s)throws CommandSyntaxException{
        SITES.put(id(s),s.getPlayerOrException().blockPosition());say(s,"Ubicacion guardada. /bwe listar y elige una construccion.");return 1;
    }
    private static int select(CommandSourceStack s,String name)throws CommandSyntaxException{
        if(SurfaceBuilder.busy()||BuildEngine.busy())return fail(s,"Espera a que termine la obra");
        var e=BweCatalog.find(name.trim());if(e==null)return fail(s,"Construccion no encontrada. /bwe listar");
        UUID player=id(s);BlockPos center=SITES.getOrDefault(player,s.getPlayerOrException().blockPosition());
        int margin=MARGINS.getOrDefault(player,5);
        try{
            int ground=SurfaceBuilder.ground(s.getLevel(),center,e,margin);
            SELECTED.put(player,new Selection(e,center,s.getLevel().dimension().location().toString(),ground,margin));
            say(s,"Seleccionada: "+e.title()+" | "+e.sizeX()+" x "+e.sizeZ()+" bloques | suelo Y="+(ground-1));
            say(s,"Se limpiaran "+(e.sizeX()+2*margin)+" x "+(e.sizeZ()+2*margin)+" bloques incluyendo margen. Alejate horizontalmente y usa /bwe crear.");return 1;
        }catch(Exception error){return fail(s,error.getMessage());}
    }
    private static int create(CommandSourceStack s)throws CommandSyntaxException{
        UUID player=id(s);Selection selected=SELECTED.get(player);
        if(selected==null)return fail(s,"Primero /bwe listar y elige una construccion");
        if(!selected.dimension().equals(s.getLevel().dimension().location().toString()))return fail(s,"Vuelve a la dimension elegida");
        String problem=SurfaceBuilder.start(s.getLevel(),selected.entry(),selected.center(),selected.ground(),selected.margin(),player);
        if(problem!=null)return fail(s,problem);
        SELECTED.remove(player);SITES.remove(player);say(s,"Obra iniciada: limpiar, nivelar y construir. /bwe estado");return 1;
    }
    private static int undo(CommandSourceStack s){
        String problem=SurfaceBuilder.remove(s.getLevel());
        if(problem==null){say(s,"Retirando la ultima obra. No se recuperan terreno, inventarios ni entidades anteriores.");return 1;}
        // Compatibility: recover the old v0.7 snapshot if there is no new manual work.
        if(WorldData.get(s.getLevel()).undo()!=null){var result=BuildEngine.undo(s.getLevel());if(!result.ok())return fail(s,result.message());say(s,result.message());return 1;}
        return fail(s,problem);
    }
}
