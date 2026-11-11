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
    private record Selection(BweCatalog.Entry entry,BlockPos center,String dimension,int ground,int margin,BweCatalog.Blueprint blueprint,int turns,List<VillageFootprint.Edge> outline) {
        Selection(BweCatalog.Entry e,BlockPos c,String d,int g,int m,BweCatalog.Blueprint b){this(e,c,d,g,m,b,0,VillageFootprint.outline(BweCatalog.terrain(e,b,m)));}
    }
    private record ManualHeight(String dimension,int y) {}
    private static final Map<UUID,ManualHeight> HEIGHTS=new HashMap<>();
    private static final Map<UUID,Selection> SELECTED=new HashMap<>();
    private static final Map<UUID,BlockPos> SITES=new HashMap<>();
    private static final Set<UUID> HIDDEN_BORDERS=new HashSet<>();
    private static int borderTicks;
    private static final Map<UUID,Integer> MARGINS=new HashMap<>();
    public BarttoloWorldExpansion(){BweLootEntry.TYPES.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());BweNetwork.register();MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void onTick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.END){BuildEngine.serverTick();SurfaceBuilder.tick();if(++borderTicks%20==0)drawBorders(event.getServer());}
    }
    @SubscribeEvent public void onStopped(ServerStoppedEvent event){
        BuildEngine.resetForShutdown();SurfaceBuilder.reset();BweCatalog.reset();SELECTED.clear();SITES.clear();MARGINS.clear();HIDDEN_BORDERS.clear();HEIGHTS.clear();
    }
    @SubscribeEvent public void onCommands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("bwe").requires(s->s.hasPermission(2)).executes(c->help(c.getSource()))
            .then(Commands.literal("ayuda").executes(c->help(c.getSource())))
            .then(Commands.literal("listar").executes(c->catalogue(c.getSource()))
                .then(Commands.argument("pagina",IntegerArgumentType.integer(1)).executes(c->listing(c.getSource(),"TODAS",IntegerArgumentType.getInteger(c,"pagina"))))
                .then(Commands.argument("grupo",StringArgumentType.word()).executes(c->listing(c.getSource(),StringArgumentType.getString(c,"grupo").toUpperCase(java.util.Locale.ROOT),1))
                    .then(Commands.argument("pagina",IntegerArgumentType.integer(1)).executes(c->listing(c.getSource(),StringArgumentType.getString(c,"grupo").toUpperCase(java.util.Locale.ROOT),IntegerArgumentType.getInteger(c,"pagina"))))))
            .then(Commands.literal("vista").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(c->preview(c.getSource(),StringArgumentType.getString(c,"id")))))
            .then(Commands.literal("seleccionar").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(c->select(c.getSource(),StringArgumentType.getString(c,"id")))))
            .then(Commands.literal("escanear").executes(c->scan(c.getSource())))
            .then(Commands.literal("borde").executes(c->{UUID who=id(c.getSource());if(!SELECTED.containsKey(who))return fail(c.getSource(),"Primero elige una construccion");if(!HIDDEN_BORDERS.add(who))HIDDEN_BORDERS.remove(who);var player=c.getSource().getPlayerOrException();if(HIDDEN_BORDERS.contains(who))BweNetwork.clearBorder(player);else showBorder(player,SELECTED.get(who));say(c.getSource(),HIDDEN_BORDERS.contains(who)?"Borde oculto":"Borde visible: pared verde virtual de 16 bloques de alto");return 1;}))
            .then(Commands.literal("rotar").executes(c->turn(c.getSource())))
            .then(Commands.literal("altura").then(Commands.argument("y",IntegerArgumentType.integer()).executes(c->height(c.getSource(),IntegerArgumentType.getInteger(c,"y")))))
            .then(Commands.literal("z").then(Commands.argument("z",IntegerArgumentType.integer(-29999984,29999984)).executes(c->position(c.getSource(),null,IntegerArgumentType.getInteger(c,"z")))))
            .then(Commands.literal("x").then(Commands.argument("x",IntegerArgumentType.integer(-29999984,29999984)).executes(c->position(c.getSource(),IntegerArgumentType.getInteger(c,"x"),null))))
            .then(Commands.literal("restaurar").executes(c->{String error=SurfaceBuilder.restoreTerrain(c.getSource().getLevel(),id(c.getSource()));if(error!=null)return fail(c.getSource(),error);say(c.getSource(),"Restaurando bloques originales del ultimo respaldo de esta dimension. /bwe estado");return 1;}))
            .then(Commands.literal("crear").executes(c->create(c.getSource())))
            .then(Commands.literal("estado").executes(c->{say(c.getSource(),SurfaceBuilder.busy()?SurfaceBuilder.status():BuildEngine.status());return 1;}))
            .then(Commands.literal("deshacer").executes(c->undo(c.getSource())))
            .then(Commands.literal("margen").executes(c->{say(c.getSource(),"Margen: "+MARGINS.getOrDefault(id(c.getSource()),1)+" bloques");return 1;})
                .then(Commands.argument("bloques",IntegerArgumentType.integer(1,1)).executes(c->{MARGINS.put(id(c.getSource()),IntegerArgumentType.getInteger(c,"bloques"));say(c.getSource(),"Margen actualizado; selecciona la construccion otra vez");return 1;})))
            .then(Commands.literal("recargar").executes(c->{if(SurfaceBuilder.busy())return fail(c.getSource(),"Espera a que termine la obra");BweCatalog.reset();return catalogue(c.getSource());}))
            .then(Commands.literal("proteger").then(Commands.argument("radio",IntegerArgumentType.integer(1,512))
                .executes(c->{int n=WorldData.get(c.getSource().getLevel()).protect(c.getSource().getLevel().dimension().location().toString(),c.getSource().getPlayerOrException().blockPosition(),IntegerArgumentType.getInteger(c,"radio"));say(c.getSource(),"Proteccion #"+n);return 1;})))
            .then(Commands.literal("desproteger").then(Commands.argument("numero",IntegerArgumentType.integer(1))
                .executes(c->{boolean ok=WorldData.get(c.getSource().getLevel()).unprotect(IntegerArgumentType.getInteger(c,"numero"));if(!ok)return fail(c.getSource(),"Proteccion no encontrada");say(c.getSource(),"Proteccion retirada");return 1;})))
        );
    }
    private static int sMin(CommandSourceStack s){return s.getLevel().getMinBuildHeight();}
    private static UUID id(CommandSourceStack s)throws CommandSyntaxException{return s.getPlayerOrException().getUUID();}
    private static void say(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal("[BWE] "+text),false);}
    private static int fail(CommandSourceStack s,String text){s.sendFailure(Component.literal("[BWE] "+text));return 0;}
    private static int help(CommandSourceStack s){
        say(s,"En el lugar elegido: /bwe escanear -> clic en una construccion -> alejarse -> /bwe crear");
        say(s,"/bwe estado | /bwe deshacer (RESTAURA terreno e inventarios respaldados) | /bwe margen 1");
        say(s,"/bwe rotar: gira 90 grados antes de crear | /bwe altura Y | /bwe borde");
        say(s,"/bwe x X | /bwe z Z: mueve el centro del borde y de la obra");
        say(s,"La altura del suelo se calcula automaticamente. Se limpian bloques y entidades excepto jugadores.");
        return 1;
    }
    private static int catalogue(CommandSourceStack s){
        if(BweCatalog.entries().isEmpty())return fail(s,BweCatalog.problem()+". Usa el JAR completo con las plantillas.");
        say(s,"Catalogo BWE: elige un grupo");
        for(String group:java.util.List.of("IV","BV","GC","EX","CTOV","EXTRAS","TT","ITP","IDAS","HOSTILES")){
            int count=BweCatalog.category(group).size();if(count==0)continue;
            String label=group.equals("HOSTILES")?"Estructuras hostiles":group;
            s.sendSuccess(()->Component.literal("["+label+"] "+count+" estructuras").withStyle(style->style.withColor(group.equals("HOSTILES")?ChatFormatting.GOLD:ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+group))),false);
        }
        return 1;
    }
    private static int listing(CommandSourceStack s,String group,int page){
        var entries=group.equals("TODAS")?BweCatalog.entries():BweCatalog.category(group);int pages=Math.max(1,(entries.size()+5)/6);
        if(entries.isEmpty())return fail(s,"Grupo vacio o desconocido. /bwe listar");
        if(page>pages)return fail(s,"Ultima pagina: "+pages);
        say(s,group+": "+entries.size()+" | pagina "+page+"/"+pages);
        for(var e:entries.subList((page-1)*6,Math.min(entries.size(),page*6))){
            Component option=Component.literal("[Ver] "+e.title()+" "+(e.sizeX()+2)+" x "+(e.sizeZ()+2)+" total ")
                .withStyle(style->style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe vista "+e.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Abrir esquema de la construccion"))))
                .append(Component.literal("[Elegir]").withStyle(style->style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe seleccionar "+e.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(e.description()+". Elegir en tu ubicacion.")))));
            s.sendSuccess(()->option,false);
        }
        s.sendSuccess(()->Component.literal("[Volver al catalogo]").withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar"))),false);
        if(page>1)s.sendSuccess(()->Component.literal("[Anterior]").withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+group+" "+(page-1)))),false);
        if(page<pages)s.sendSuccess(()->Component.literal("[Siguiente]").withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+group+" "+(page+1)))),false);
        return 1;
    }
    private static int preview(CommandSourceStack s,String name)throws CommandSyntaxException{
        var e=BweCatalog.find(name.trim());if(e==null)return fail(s,"Construccion no encontrada. /bwe listar");
        BweNetwork.openPreview(s.getPlayerOrException(),e);return 1;
    }
    private static int scan(CommandSourceStack s)throws CommandSyntaxException{
        UUID who=id(s);HEIGHTS.remove(who);SELECTED.remove(who);HIDDEN_BORDERS.remove(who);BweNetwork.clearBorder(s.getPlayerOrException());
        SITES.put(who,s.getPlayerOrException().blockPosition());say(s,"Ubicacion guardada. Elige una construccion del catalogo.");return catalogue(s);
    }
    private static int select(CommandSourceStack s,String name)throws CommandSyntaxException{
        if(SurfaceBuilder.busy()||BuildEngine.busy())return fail(s,"Espera a que termine la obra");
        var e=BweCatalog.find(name.trim());if(e==null)return fail(s,"Construccion no encontrada. /bwe listar");
        UUID player=id(s);BlockPos center=SITES.getOrDefault(player,s.getPlayerOrException().blockPosition());
        int margin=MARGINS.getOrDefault(player,1);
        try{
            var plan=BweCatalog.compose(s.getLevel(),e);var manual=HEIGHTS.get(player);
            int ground=manual!=null&&manual.dimension().equals(s.getLevel().dimension().location().toString())?manual.y()+1:SurfaceBuilder.ground(s.getLevel(),center,e,margin);
            String heightError=SurfaceBuilder.heightProblem(s.getLevel(),e,plan,ground);if(heightError!=null)return fail(s,heightError);
            SELECTED.put(player,new Selection(e,center,s.getLevel().dimension().location().toString(),ground,margin,plan));HIDDEN_BORDERS.remove(player);showBorder(s.getPlayerOrException(),SELECTED.get(player));
            say(s,"Borde VERDE: contorno real de limpieza, incluido un bloque de margen. /bwe borde para ocultar o mostrar.");
            say(s,"Seleccionada: "+e.title()+" | "+e.sizeX()+" x "+e.sizeZ()+" bloques | "+heightLabel(e)+(ground-1)+(manual!=null&&manual.dimension().equals(s.getLevel().dimension().location().toString())?" (altura manual conservada)":""));
            say(s,"Centro X="+center.getX()+" Z="+center.getZ()+" | /bwe x X | /bwe z Z | "+heightLabel(e)+(ground-1)+" | base del esquema Y="+SurfaceBuilder.originY(e,plan,ground));
            say(s,"Limite total: "+(e.sizeX()+2*margin)+" x "+(e.sizeZ()+2*margin)+" bloques, margen de un bloque. "+(BweCatalog.airship(e)?"Dirigibles: aire alrededor y debajo, sin crear tierra. /bwe altura Y ajusta la base.":BweCatalog.pirate(e)?"Estructura acuatica: agua alrededor, sin plataforma de tierra. /bwe altura Y ajusta el agua.":"Limpieza ajustada a edificios y caminos.")+" Alejate horizontalmente y usa /bwe crear.");return 1;
        }catch(Exception error){return fail(s,error.getMessage());}
    }
    private static int create(CommandSourceStack s)throws CommandSyntaxException{
        UUID player=id(s);Selection selected=SELECTED.get(player);
        if(selected==null)return fail(s,"Primero /bwe listar y elige una construccion");
        if(!selected.dimension().equals(s.getLevel().dimension().location().toString()))return fail(s,"Vuelve a la dimension elegida");
        String problem=SurfaceBuilder.start(s.getLevel(),selected.entry(),selected.blueprint(),selected.center(),selected.ground(),selected.margin(),player);
        if(problem!=null)return fail(s,problem);
        SELECTED.remove(player);SITES.remove(player);HEIGHTS.remove(player);BweNetwork.clearBorder(s.getPlayerOrException());say(s,BweCatalog.airship(selected.entry())?"Obra aerea iniciada: despejar aire y colocar dirigibles, sin cimentacion. /bwe estado":BweCatalog.pirate(selected.entry())?"Construccion acuatica iniciada: despejar, construir y llenar el contorno de agua. /bwe estado":"Obra iniciada: limpiar, nivelar y construir. /bwe estado");return 1;
    }

    private static int turn(CommandSourceStack s)throws CommandSyntaxException{
        if(SurfaceBuilder.busy()||BuildEngine.busy())return fail(s,"Espera a que termine la obra");
        UUID who=id(s);Selection a=SELECTED.get(who);if(a==null)return fail(s,"Primero selecciona una construccion");
        if(!a.dimension().equals(s.getLevel().dimension().location().toString()))return fail(s,"Vuelve a la dimension elegida");
        var e=BweCatalog.turned(a.entry());var b=BweCatalog.turn(a.entry(),a.blueprint());
        Selection next=new Selection(e,a.center(),a.dimension(),a.ground(),a.margin(),b,(a.turns()+1)%4,VillageFootprint.outline(BweCatalog.terrain(e,b,a.margin())));
        SELECTED.put(who,next);HIDDEN_BORDERS.remove(who);showBorder(s.getPlayerOrException(),next);
        say(s,"Giro: "+(next.turns()*90)+" grados | "+(e.sizeX()+2*a.margin())+" x "+(e.sizeZ()+2*a.margin())+" total | "+heightLabel(e)+(a.ground()-1)+". /bwe rotar para otro giro; /bwe crear para construir.");return 1;
    }

    private static String heightLabel(BweCatalog.Entry e){return BweCatalog.airship(e)?"base aerea Y=":BweCatalog.pirate(e)?"agua Y=":"suelo/camino Y=";}
    private static int height(CommandSourceStack s,int y)throws CommandSyntaxException{
        UUID who=id(s);Selection a=SELECTED.get(who);if(a==null)return fail(s,"Primero selecciona una construccion");
        if(!a.dimension().equals(s.getLevel().dimension().location().toString()))return fail(s,"Vuelve a la dimension elegida");
        if(y<sMin(s)||y>=s.getLevel().getMaxBuildHeight())return fail(s,"Altura fuera del mundo");
        String problem=SurfaceBuilder.heightProblem(s.getLevel(),a.entry(),a.blueprint(),y+1);if(problem!=null)return fail(s,problem);
        Selection next=new Selection(a.entry(),a.center(),a.dimension(),y+1,a.margin(),a.blueprint(),a.turns(),a.outline());
        SELECTED.put(who,next);HEIGHTS.put(who,new ManualHeight(a.dimension(),y));HIDDEN_BORDERS.remove(who);showBorder(s.getPlayerOrException(),next);
        say(s,"Altura fijada: "+heightLabel(a.entry())+y+" | base del esquema Y="+SurfaceBuilder.originY(a.entry(),a.blueprint(),y+1)+". Se conserva al cambiar de modelo; /bwe escanear inicia otra ubicacion.");return 1;
    }
    private static int position(CommandSourceStack s,Integer x,Integer z)throws CommandSyntaxException{
        if(SurfaceBuilder.busy()||BuildEngine.busy())return fail(s,"Espera a que termine la obra");
        UUID who=id(s);Selection a=SELECTED.get(who);
        if(a==null)return fail(s,"Primero selecciona una construccion");
        if(!a.dimension().equals(s.getLevel().dimension().location().toString()))return fail(s,"Vuelve a la dimension elegida");
        BlockPos center=new BlockPos(x==null?a.center().getX():x,a.center().getY(),z==null?a.center().getZ():z);
        try{
            var manual=HEIGHTS.get(who);
            int ground;
            if(manual!=null&&manual.dimension().equals(a.dimension())){
                SurfaceBuilder.validateSite(s.getLevel(),center,a.entry(),a.margin());
                ground=manual.y()+1;
            }else ground=SurfaceBuilder.ground(s.getLevel(),center,a.entry(),a.margin());
            String problem=SurfaceBuilder.heightProblem(s.getLevel(),a.entry(),a.blueprint(),ground);
            if(problem!=null)return fail(s,problem);
            Selection next=new Selection(a.entry(),center,a.dimension(),ground,a.margin(),a.blueprint(),a.turns(),a.outline());
            SELECTED.put(who,next);SITES.put(who,center);HIDDEN_BORDERS.remove(who);showBorder(s.getPlayerOrException(),next);
            say(s,"Centro de la obra X="+center.getX()+" Z="+center.getZ()+" | "+heightLabel(a.entry())+(ground-1));return 1;
        }catch(IllegalArgumentException error){return fail(s,error.getMessage());}
    }
    private static void drawBorders(net.minecraft.server.MinecraftServer server){
        for(var item:SELECTED.entrySet()){
            if(HIDDEN_BORDERS.contains(item.getKey()))continue;
            var player=server.getPlayerList().getPlayer(item.getKey());if(player==null)continue;
            if(!player.serverLevel().dimension().location().toString().equals(item.getValue().dimension()))continue;
            showBorder(player,item.getValue());
        }
    }
    private static void showBorder(net.minecraft.server.level.ServerPlayer player,Selection selected){
        int x=selected.center().getX()-selected.entry().sizeX()/2,z=selected.center().getZ()-selected.entry().sizeZ()/2,m=selected.margin();
        int x0=x-m,z0=z-m,x1=x+selected.entry().sizeX()+m,z1=z+selected.entry().sizeZ()+m;
        var edges=selected.outline().stream().map(v->new VillageFootprint.Edge(v.x0()+x,v.z0()+z,v.x1()+x,v.z1()+z)).toList();
        BweNetwork.showBorder(player,x0,z0,x1,z1,selected.ground(),edges);
    }

    private static int undo(CommandSourceStack s)throws CommandSyntaxException{
        String problem=SurfaceBuilder.restoreTerrain(s.getLevel(),id(s));
        if(problem==null){say(s,"Deshaciendo obra: se comprueba y restaura el terreno original respaldado. Alejate del area; /bwe estado");return 1;}
        if(problem.contains("No hay respaldo")&&WorldData.get(s.getLevel()).undo()!=null){var result=BuildEngine.undo(s.getLevel());if(!result.ok())return fail(s,result.message());say(s,result.message());return 1;}
        return fail(s,problem);
    }
}
