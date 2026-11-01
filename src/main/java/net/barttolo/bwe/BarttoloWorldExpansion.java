package net.barttolo.bwe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Barttolo World Expansion 0.6.9. Test server only, operator-only commands. */
@Mod(BarttoloWorldExpansion.MOD_ID)
public final class BarttoloWorldExpansion {
    public static final String MOD_ID = "barttoloworldexpansion";
    private static final Map<UUID, BuildEngine.Plan> PLANS = new HashMap<>();
    private static final List<String> OPTIONAL_TEMPLATES = List.of(
        "explorify:campsite/main/01", "explorify:campsite/main/02", "explorify:campsite/main/03",
        "explorify:campsite/tent/01", "explorify:guide_post/warm/whole", "explorify:guide_post/cold/whole", "explorify:ruins/house/ruins_01",
        "explorify:ruins/house/ruins_02", "explorify:ruins/monument/01",
        "explorify:farmstead/main/01", "explorify:ruins/square/01",
        "ctov:village/plains/house/small_1", "ctov:village/plains/house/small_2",
        "ctov:village/plains/jobsite/bakery", "ctov:village/plains/decos/well"
    );

    public BarttoloWorldExpansion() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if(event.phase == TickEvent.Phase.END) BuildEngine.serverTick();
    }

    /** Drop stale level references when a test server is stopped and restarted
     *  inside the same JVM. The durable undo snapshot remains on disk. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        BuildEngine.resetForShutdown();
        PLANS.clear();
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher=event.getDispatcher();
        dispatcher.register(Commands.literal("bwe").requires(s -> s.hasPermission(2))
            .then(Commands.literal("ayuda").executes(ctx -> help(ctx.getSource())))
            .then(Commands.literal("listar").executes(ctx -> listing(ctx.getSource())))
            .then(Commands.literal("estado").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("asentamientos").executes(ctx -> completedSites(ctx.getSource())))
            .then(Commands.literal("olvidar").then(Commands.argument("numero",IntegerArgumentType.integer(1))
                .then(Commands.literal("SI").executes(ctx -> forgetSite(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"numero"))))))
            .then(Commands.literal("analizar").executes(ctx -> scan(ctx.getSource(), 12))
                .then(Commands.argument("radio",IntegerArgumentType.integer(4,32))
                .executes(ctx -> scan(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"radio")))))
            .then(Commands.literal("planear").then(Commands.argument("tipo",StringArgumentType.word())
                .executes(ctx -> planOriginal(ctx.getSource(),StringArgumentType.getString(ctx,"tipo"),null))))
            .then(Commands.literal("buscar").then(Commands.argument("tipo",StringArgumentType.word())
                .then(Commands.argument("radio",IntegerArgumentType.integer(32,128))
                    .executes(ctx -> find(ctx.getSource(),StringArgumentType.getString(ctx,"tipo"),IntegerArgumentType.getInteger(ctx,"radio"))))))
            .then(Commands.literal("plantilla").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(ctx -> planTemplate(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
            .then(Commands.literal("previsualizar").executes(ctx -> preview(ctx.getSource())))
            .then(Commands.literal("simular").executes(ctx -> simulate(ctx.getSource())))
            .then(Commands.literal("confirmar").then(Commands.literal("SI").executes(ctx -> confirm(ctx.getSource()))))
            .then(Commands.literal("cancelar").executes(ctx -> cancel(ctx.getSource())))
            .then(Commands.literal("deshacer").executes(ctx -> undo(ctx.getSource())))
            .then(Commands.literal("finalizar").then(Commands.literal("SI").executes(ctx -> finalizeWork(ctx.getSource()))))
            .then(Commands.literal("proteger").then(Commands.argument("radio",IntegerArgumentType.integer(16,512))
                .executes(ctx -> protect(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"radio")))))
            .then(Commands.literal("protegidas").executes(ctx -> protectedAreas(ctx.getSource())))
            .then(Commands.literal("protegerarea")
                .then(Commands.argument("x1",IntegerArgumentType.integer())
                .then(Commands.argument("z1",IntegerArgumentType.integer())
                .then(Commands.argument("x2",IntegerArgumentType.integer())
                .then(Commands.argument("z2",IntegerArgumentType.integer())
                .executes(ctx -> protectRect(ctx.getSource(),
                    IntegerArgumentType.getInteger(ctx,"x1"),IntegerArgumentType.getInteger(ctx,"z1"),
                    IntegerArgumentType.getInteger(ctx,"x2"),IntegerArgumentType.getInteger(ctx,"z2"))))))))
            .then(Commands.literal("areas").executes(ctx -> rectangleAreas(ctx.getSource())))
            .then(Commands.literal("liberararea")
                .then(Commands.argument("numero",IntegerArgumentType.integer(1))
                .then(Commands.literal("SI")
                    .executes(ctx -> unprotectRect(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"numero"))))))
            .then(Commands.literal("desproteger").then(Commands.argument("numero",IntegerArgumentType.integer(1))
                .executes(ctx -> unprotect(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"numero")))))
        );
    }

    private static void say(CommandSourceStack s,String msg) { s.sendSuccess(() -> Component.literal("[BWE] "+msg),false); }
    private static int fail(CommandSourceStack s,String msg) { s.sendFailure(Component.literal("[BWE] "+msg)); return 0; }
    private static UUID player(CommandSourceStack s) throws CommandSyntaxException { return s.getPlayerOrException().getUUID(); }
    private static String dim(ServerLevel level) {return level.dimension().location().toString();}

    private static int help(CommandSourceStack s) {
        say(s,"analizar [radio] | planear <aldea|campamento|ruina|torre|refugio|altar|pozo|carreta> | buscar <tipo> <radio> | plantilla <id>");
        say(s,"previsualizar | simular | confirmar SI | estado | cancelar | deshacer | finalizar SI | asentamientos | olvidar <numero> SI | proteger <radio> | protegidas | desproteger <numero> | protegerarea <x1> <z1> <x2> <z2> | areas | liberararea <numero> SI");
        return 1;
    }
    private static int status(CommandSourceStack s) {
        say(s, BuildEngine.status());
        var snapshot=WorldData.get(s.getLevel()).undo();
        if(snapshot!=null) say(s,"Respaldo pendiente: "+snapshot.name()+" / "+snapshot.blocks().size()+
            " bloques. /bwe deshacer o /bwe finalizar SI solo si esta completa.");
        return 1;
    }
    private static int completedSites(CommandSourceStack s) {
        List<WorldData.Site> sites=WorldData.get(s.getLevel()).completedSites();
        if(sites.isEmpty()) { say(s,"No hay obras finalizadas registradas por BWE."); return 1; }
        say(s,"Asentamientos registrados: "+sites.size()+" (incluyen separacion adicional de 16 bloques)");
        int index=0;
        for(WorldData.Site a:sites) {
            if(++index>30) { say(s,"Mostrando 30. Total: "+sites.size()); break; }
            say(s,"#"+index+" "+a.name()+" "+a.dimension()+" X="+a.minX()+".."+a.maxX()+" Z="+a.minZ()+".."+a.maxZ());
        }
        return 1;
    }
    private static int forgetSite(CommandSourceStack s,int number) {
        if(BuildEngine.busy() || WorldData.get(s.getLevel()).undo()!=null)
            return fail(s,"Primero termina o revierte la obra pendiente.");
        if(!WorldData.get(s.getLevel()).forgetSite(number)) return fail(s,"Asentamiento inexistente.");
        say(s,"Registro #"+number+" olvidado. Los edificios no se han modificado.");
        return 1;
    }
    private static int listing(CommandSourceStack s) {
        say(s,"Construcciones propias: "+String.join(", ",BuildEngine.KINDS));
        say(s,"Plantillas opcionales de CTOV y Explorify (requieren mod original instalado):");
        for(String t:OPTIONAL_TEMPLATES) say(s,t);
        return 1;
    }
    private static int scan(CommandSourceStack s,int r) throws CommandSyntaxException {
        BlockPos center=s.getPlayerOrException().blockPosition();
        var result=WorldChecks.scan(s.getLevel(),center,r,r,4);
        say(s,"Radio "+r+" | "+(result.ok()?"APTO":"NO APTO")+": "+result.reason()
            +" | suelo "+result.low()+".."+result.high()+" | agua="+result.liquids()+" | artificial="+result.artificial());
        return result.ok()?1:0;
    }
    private static int planOriginal(CommandSourceStack s,String type,BlockPos where) throws CommandSyntaxException {
        if(!BuildEngine.KINDS.contains(type)) return fail(s,"Tipo desconocido. /bwe listar");
        if(WorldData.get(s.getLevel()).undo()!=null) return fail(s,"Primero /bwe deshacer o /bwe finalizar SI");
        BlockPos center=where==null?s.getPlayerOrException().blockPosition():where;
        int radius=BuildEngine.radius(type);
        var survey=WorldChecks.scan(s.getLevel(),center,radius,radius,4);
        if(!survey.ok()) return fail(s,"No se puede planear: "+survey.reason());
        BuildEngine.Plan plan=new BuildEngine.Plan(type,"",center,dim(s.getLevel()),survey.groundY(),radius,radius,System.currentTimeMillis());
        PLANS.put(player(s),plan);
        say(s,"Plan registrado: "+type+" en "+center.getX()+","+survey.groundY()+","+center.getZ());
        return preview(s);
    }
    private static int find(CommandSourceStack s,String kind,int searchRadius) throws CommandSyntaxException {
        if(!BuildEngine.KINDS.contains(kind)) return fail(s,"Tipo desconocido");
        BlockPos start=s.getPlayerOrException().blockPosition();
        int attempts=0;
        // Strict work limit: at most 16 surveys to avoid tick spikes.
        for(int r=32;r<=searchRadius;r+=16) {
            for(int[] pair:new int[][]{{r,0},{-r,0},{0,r},{0,-r}}) {
                if(attempts++>=16) return fail(s,"No se encontro zona en 16 intentos. Explora otra area.");
                BlockPos site=start.offset(pair[0],0,pair[1]);
                var survey=WorldChecks.scan(s.getLevel(),site,BuildEngine.radius(kind),BuildEngine.radius(kind),4);
                if(survey.ok()) return planOriginal(s,kind,site);
            }
        }
        return fail(s,"No se encontro zona apta entre chunks cargados.");
    }
    private static int planTemplate(CommandSourceStack s,String id) throws CommandSyntaxException {
        if(!OPTIONAL_TEMPLATES.contains(id)) return fail(s,"Plantilla no permitida en esta version; consulta /bwe listar");
        if(WorldData.get(s.getLevel()).undo()!=null) return fail(s,"Hay una obra sin finalizar");
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null) return fail(s,"ID invalido");
        Optional<StructureTemplate> optional=s.getLevel().getStructureManager().get(key);
        if(optional.isEmpty()) return fail(s,"No existe esta plantilla. Instala el mod original en servidor: "+id);
        Vec3i size=optional.get().getSize();
        if(size.getX()<1||size.getY()<1||size.getZ()<1||size.getX()>32||size.getY()>32||size.getZ()>32)
            return fail(s,"Tamano no admitido: "+size);
        int halfX=(size.getX()+1)/2,halfZ=(size.getZ()+1)/2;
        BlockPos center=s.getPlayerOrException().blockPosition();
        var survey=WorldChecks.scan(s.getLevel(),center,halfX,halfZ,4);
        if(!survey.ok()) return fail(s,"Zona no apta: "+survey.reason());
        if(survey.groundY()+size.getY()>=s.getLevel().getMaxBuildHeight())
            return fail(s,"Plantilla supera el limite superior del mundo");
        BuildEngine.Plan plan=new BuildEngine.Plan("plantilla",id,center,dim(s.getLevel()),survey.groundY(),halfX,halfZ,System.currentTimeMillis());
        PLANS.put(player(s),plan);
        say(s,"Plantilla seleccionada: "+id+" dimensiones "+size);
        return preview(s);
    }
    private static int preview(CommandSourceStack s) throws CommandSyntaxException {
        BuildEngine.Plan p=PLANS.get(player(s));
        if(p==null) return fail(s,"Primero /bwe planear o /bwe plantilla");
        say(s,"PREVISUALIZACION TEXTUAL: "+(p.template().isEmpty()?p.kind():p.template())
            +" | desde X="+(p.center().getX()-p.halfX())+" hasta "+(p.center().getX()+p.halfX())
            +" | Z="+(p.center().getZ()-p.halfZ())+" hasta "+(p.center().getZ()+p.halfZ())+" | Y="+p.y()
            +" | "+p.dimension());
        say(s,"Solo vista textual. SAL DEL AREA y usa una COPIA del mundo. Para aplicar: /bwe confirmar SI (5 min), o /bwe cancelar.");
        return 1;
    }
    private static int simulate(CommandSourceStack s) throws CommandSyntaxException {
        BuildEngine.Plan p=PLANS.get(player(s));
        if(p==null) return fail(s,"No existe plan. Usa /bwe planear primero");
        if(!p.dimension().equals(dim(s.getLevel()))) return fail(s,"Dimension distinta del plan");
        BuildEngine.Result r=BuildEngine.simulate(s.getLevel(),p);
        if(r.ok()) say(s,r.message()); else return fail(s,r.message());
        return 1;
    }
    private static int confirm(CommandSourceStack s) throws CommandSyntaxException {
        BuildEngine.Plan p=PLANS.get(player(s));
        if(p==null) return fail(s,"No existe plan");
        if(!p.dimension().equals(dim(s.getLevel()))) return fail(s,"Dimension diferente");
        if(System.currentTimeMillis()-p.created()>300_000) { PLANS.remove(player(s));return fail(s,"Plan caducado (5 min)"); }
        BuildEngine.Result result=BuildEngine.build(s.getLevel(),p,player(s));
        if(!result.ok()) return fail(s,result.message());
        PLANS.remove(player(s));say(s,result.message()+" | bloques="+result.count());return 1;
    }
    private static int cancel(CommandSourceStack s) throws CommandSyntaxException {
        PLANS.remove(player(s));say(s,"Plan cancelado. Terreno intacto.");return 1;
    }
    private static int undo(CommandSourceStack s) {
        BuildEngine.Result r=BuildEngine.undo(s.getLevel());
        if(!r.ok()) return fail(s,r.message());
        say(s,r.message());return 1;
    }
    private static int finalizeWork(CommandSourceStack s) {
        WorldData data=WorldData.get(s.getLevel());
        BuildEngine.Result review=BuildEngine.canFinalize(s.getLevel());
        if(!review.ok()) return fail(s,review.message());
        WorldData.Undo snapshot=data.undo();
        data.registerCompleted(snapshot);
        data.clearUndo();
        say(s,"Obra aceptada y registrada en /bwe asentamientos. Respaldo eliminado; ya NO es posible deshacer con BWE.");
        return 1;
    }
    private static int protect(CommandSourceStack s,int r) throws CommandSyntaxException {
        int number=WorldData.get(s.getLevel()).protect(dim(s.getLevel()),s.getPlayerOrException().blockPosition(),r);
        say(s,"Zona protegida #"+number+" con radio "+r);
        return 1;
    }
    private static int protectedAreas(CommandSourceStack s) {
        List<WorldData.Area> areas=WorldData.get(s.getLevel()).exclusions();
        if(areas.isEmpty()) say(s,"No hay zonas manuales. Proteccion predeterminada de spawn: radio 160.");
        for(int i=0;i<areas.size();i++) {
            var a=areas.get(i);
            say(s,"#"+(i+1)+" "+a.dimension()+" X="+a.x()+" Z="+a.z()+" radio="+a.radius());
        }
        return 1;
    }
    private static int protectRect(CommandSourceStack s,int x1,int z1,int x2,int z2) {
        // Hard limit: a typo must not silently protect thousands of chunks.
        if(Math.abs((long)x2-x1)>1024 || Math.abs((long)z2-z1)>1024)
            return fail(s,"Area demasiado grande (maximo 1024 bloques por eje). Divide el camino en tramos.");
        int index=WorldData.get(s.getLevel()).protectRect(dim(s.getLevel()),x1,z1,x2,z2);
        say(s,"Rectangulo #"+index+" protegido: "+Math.min(x1,x2)+","+Math.min(z1,z2)+" a "+Math.max(x1,x2)+","+Math.max(z1,z2));
        return 1;
    }
    private static int rectangleAreas(CommandSourceStack s) {
        List<WorldData.RectArea> areas=WorldData.get(s.getLevel()).rectangles();
        if(areas.isEmpty()) say(s,"No hay areas rectangulares protegidas.");
        for(int i=0;i<areas.size();i++) {
            var a=areas.get(i);
            say(s,"#"+(i+1)+" "+a.dimension()+" X="+a.minX()+".."+a.maxX()+" Z="+a.minZ()+".."+a.maxZ());
        }
        return 1;
    }
    private static int unprotectRect(CommandSourceStack s,int index) {
        if(BuildEngine.busy()) return fail(s,"Espera que termine la construccion o restauracion.");
        if(!WorldData.get(s.getLevel()).unprotectRect(index)) return fail(s,"Numero de area inexistente.");
        say(s,"Area #"+index+" retirada. No se modificaron bloques.");
        return 1;
    }
    private static int unprotect(CommandSourceStack s,int index) {
        if(!WorldData.get(s.getLevel()).unprotect(index)) return fail(s,"Numero inexistente");
        say(s,"Exclusion #"+index+" retirada.");return 1;
    }
}
