package net.barttolo.bwe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
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

/** Barttolo World Expansion 0.6.18. Test server only, operator-only commands. */
@Mod(BarttoloWorldExpansion.MOD_ID)
public final class BarttoloWorldExpansion {
    public static final String MOD_ID = "barttoloworldexpansion";
    private static final Map<UUID, BuildEngine.Plan> PLANS = new HashMap<>();
    private record ScanSite(BlockPos center, String dimension, long created) {}
    private static final Map<UUID, ScanSite> SCANS = new HashMap<>();
    private static final List<String> ORDERED_KINDS = List.of(
        "campamento", "refugio", "pozo", "carreta", "torre", "ruina", "altar", "aldea");
    private static final int CATALOG_PAGE_SIZE = 8;
    public BarttoloWorldExpansion() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if(event.phase == TickEvent.Phase.END) { BuildEngine.serverTick(); TemplateSiteAudit.serverTick(); TemplateSurfaceSurvey.serverTick(); }
    }

    /** Drop stale level references when a test server is stopped and restarted
     *  inside the same JVM. The durable undo snapshot remains on disk. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        BuildEngine.resetForShutdown();
        TemplateSiteAudit.resetForShutdown();
        TemplateSurfaceSurvey.resetForShutdown();
        PLANS.clear();
        SCANS.clear();
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher=event.getDispatcher();
        dispatcher.register(Commands.literal("bwe").requires(s -> s.hasPermission(2))
            .then(Commands.literal("ayuda").executes(ctx -> help(ctx.getSource())))
            .then(Commands.literal("listar").executes(ctx -> listing(ctx.getSource(),1))
                .then(Commands.argument("pagina",IntegerArgumentType.integer(1))
                    .executes(ctx -> listing(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"pagina")))))
            .then(Commands.literal("inspeccionar").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(ctx -> inspectTemplate(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
            .then(Commands.literal("verificarplantilla").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(ctx -> verifyTemplate(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
            .then(Commands.literal("auditarplantilla").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(ctx -> auditTemplate(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
            .then(Commands.literal("superficie").then(Commands.argument("id",StringArgumentType.greedyString())
                .executes(ctx -> surfaceTemplate(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
            .then(Commands.literal("margen").executes(ctx -> marginStatus(ctx.getSource()))
                .then(Commands.argument("bloques",IntegerArgumentType.integer(5,10))
                    .executes(ctx -> configureMargin(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx,"bloques")))))
            .then(Commands.literal("superficieestado").executes(ctx -> surfaceStatus(ctx.getSource())))
            .then(Commands.literal("cancelarsuperficie").executes(ctx -> cancelSurface(ctx.getSource())))
            .then(Commands.literal("auditoria").executes(ctx -> auditStatus(ctx.getSource())))
            .then(Commands.literal("cancelarauditoria").executes(ctx -> cancelAudit(ctx.getSource())))
            .then(Commands.literal("plantillas").executes(ctx -> templates(ctx.getSource())))
            .then(Commands.literal("escanear").executes(ctx -> scan(ctx.getSource(),12))
                .then(Commands.argument("radio",IntegerArgumentType.integer(4,32))
                    .executes(ctx -> scan(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"radio")))))
            .then(Commands.literal("seleccionar").then(Commands.argument("tipo",StringArgumentType.word())
                .executes(ctx -> select(ctx.getSource(),StringArgumentType.getString(ctx,"tipo")))))
            .then(Commands.literal("crear").executes(ctx -> create(ctx.getSource())))
            .then(Commands.literal("estado").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("diagnosticar").executes(ctx -> diagnose(ctx.getSource())))
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
        say(s,"Uso sencillo: /bwe escanear -> /bwe listar (clic para seleccionar) -> /bwe crear");
        say(s,"/bwe estado | deshacer | finalizar SI | proteger <radio> | protegerarea <x1> <z1> <x2> <z2> | protegidas | areas | desproteger <numero> | liberararea <numero> SI");
        say(s,"Avanzado: /bwe margen [5..10] (temporal) | superficie <ctov:id|explorify:id> | superficieestado | cancelarsuperficie (SOLO LECTURA)");
        say(s,"/bwe verificarplantilla <id> | auditarplantilla <id> | auditoria | cancelarauditoria | diagnosticar | asentamientos | plantillas");
        return 1;
    }
    private static int diagnose(CommandSourceStack s) {
        BuildEngine.Result r=BuildEngine.diagnose(s.getLevel());
        if(!r.ok()) return fail(s,r.message());
        say(s,r.message());return 1;
    }
    private static int status(CommandSourceStack s) {
        say(s, BuildEngine.status());
        var snapshot=WorldData.get(s.getLevel()).undo();
        if(snapshot!=null) say(s,"Respaldo pendiente: "+snapshot.name()+" / "+snapshot.blocks().size()+
            " bloques. "+(snapshot.completed() ? "COMPLETO: revisar antes de /bwe finalizar SI." :
            "INCOMPLETO: /bwe finalizar SI bloqueado; conserva respaldo y usa /bwe deshacer."));
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
    /** Read-only discovery: use templates already exposed by the server's resource manager.
     *  Do not copy third-party NBT or offer placement without block-entity-safe undo.
     *  Only the visible page loads NBT, keeping command execution bounded.
     */
    private static List<ResourceLocation> externalTemplates(ServerLevel level) {
        try (var stream = level.getStructureManager().listTemplates()) {
            return stream.filter(key -> key.getNamespace().equals("ctov")
                    || key.getNamespace().equals("explorify"))
                .distinct().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        }
    }
    private static int listing(CommandSourceStack s,int page) {
        List<ResourceLocation> ids=externalTemplates(s.getLevel());
        int pages=Math.max(1,(ids.size()+CATALOG_PAGE_SIZE-1)/CATALOG_PAGE_SIZE);
        if(page>pages) return fail(s,"Pagina inexistente. Ultima pagina: "+pages);
        say(s,"Catalogo CTOV/Explorify: "+ids.size()+" plantillas detectadas | pagina "+page+"/"+pages
            +" | INSPECCION SOLAMENTE: construccion externa aun bloqueada.");
        int begin=(page-1)*CATALOG_PAGE_SIZE;
        for(int i=begin;i<Math.min(ids.size(),begin+CATALOG_PAGE_SIZE);i++) {
            ResourceLocation key=ids.get(i);
            Optional<StructureTemplate> found=s.getLevel().getStructureManager().get(key);
            String dimensions=found.map(t -> {
                Vec3i size=t.getSize();
                return size.getX()+" x "+size.getZ()+" bloques";
            }).orElse("dimensiones no disponibles");
            String id=key.toString();
            Component option=Component.literal("  ["+id+"] "+dimensions)
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bwe inspeccionar "+id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Ver dimensiones; NO construye"))));
            s.sendSuccess(() -> option,false);
        }
        if(page==1) {
            say(s,"Estructuras BWE construibles (clic para seleccionar; antes /bwe escanear):");
            for(String type : ORDERED_KINDS) {
                Component option = Component.literal("  [" + type.toUpperCase(Locale.ROOT) + "]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bwe seleccionar " + type))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Seleccionar " + type))));
                s.sendSuccess(() -> option,false);
            }
        }
        if(page>1) {
            Component prev=Component.literal("  [Pagina anterior]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+(page-1))));
            s.sendSuccess(() -> prev,false);
        }
        if(page<pages) {
            Component next=Component.literal("  [Pagina siguiente]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/bwe listar "+(page+1))));
            s.sendSuccess(() -> next,false);
        }
        if(ids.isEmpty()) say(s,"No se encontraron plantillas con namespace ctov/explorify en los recursos del servidor.");
        return 1;
    }
    private static int templates(CommandSourceStack s) { return listing(s,1); }
    /** Read-only preflight of ONE NBT piece; jigsaw connectors do not describe a whole village. */
    private static int inspectTemplate(CommandSourceStack s,String id) {
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null || !(key.getNamespace().equals("ctov") || key.getNamespace().equals("explorify")))
            return fail(s,"Solo se inspeccionan plantillas CTOV y Explorify.");
        if(!externalTemplates(s.getLevel()).contains(key))
            return fail(s,"La plantilla no aparece en los recursos cargados: "+id);
        Optional<StructureTemplate> found=s.getLevel().getStructureManager().get(key);
        if(found.isEmpty()) return fail(s,"La plantilla existe en el catalogo pero no se pudo leer: "+id);
        StructureTemplate template=found.get();
        Vec3i size=template.getSize();
        if(size.getX()<1 || size.getY()<1 || size.getZ()<1)
            return fail(s,"Plantilla sin dimensiones validas: "+id);
        long area=(long)size.getX()*size.getZ();
        say(s,"Plantilla "+id+" | huella X x Z: "+size.getX()+" x "+size.getZ()
            +" bloques ("+area+" bloques cuadrados) | altura: "+size.getY()+" (informativa).");
        // Targeted inspection of one loaded NBT; no world blocks are read or written.
        try {
            StructurePlaceSettings settings=new StructurePlaceSettings();
            int jigsaws=template.filterBlocks(BlockPos.ZERO,settings,Blocks.JIGSAW,true).size();
            int structureMarkers=template.filterBlocks(BlockPos.ZERO,settings,Blocks.STRUCTURE_BLOCK,true).size();
            int vanillaContainers=template.filterBlocks(BlockPos.ZERO,settings,Blocks.CHEST,true).size()
                +template.filterBlocks(BlockPos.ZERO,settings,Blocks.BARREL,true).size();
            say(s,"Indicadores: conectores jigsaw="+jigsaws+" | bloques structure="+structureMarkers
                +" | cofres/barriles vanilla="+vanillaContainers+".");
            if(jigsaws>0) say(s,"PIEZA JIGSAW: dimensiones de esta pieza NBT, NO de la aldea completa.");
            if(structureMarkers>0 || vanillaContainers>0)
                say(s,"Bloques especiales: requiere respaldo NBT/entidades antes de permitir colocacion.");
        } catch(RuntimeException error) {
            return fail(s,"No se pudo inspeccionar la paleta: "+error.getClass().getSimpleName());
        }
        if(area>22500L) say(s,"Huella superior a 150 x 150: catalogada sin limite artificial, no autorizada para colocar.");
        say(s,"SOLO LECTURA: no coloca, no terraforma y /bwe crear NO construye plantillas externas.");
        return 1;
    }
    /** Read-only preflight at the operator's current block, for ONE unrotated NBT piece.
     *  Does not load/generate chunks, examine terrain or authorize construction. */
    private static int verifyTemplate(CommandSourceStack s,String id) throws CommandSyntaxException {
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null || !(key.getNamespace().equals("ctov") || key.getNamespace().equals("explorify")))
            return fail(s,"Solo se verifican plantillas CTOV y Explorify.");
        ServerLevel world=s.getLevel();
        if(!externalTemplates(world).contains(key))
            return fail(s,"Plantilla no disponible en los recursos cargados: "+id);
        Optional<StructureTemplate> found=world.getStructureManager().get(key);
        if(found.isEmpty()) return fail(s,"No se pudo leer la plantilla: "+id);
        Vec3i size=found.get().getSize();
        if(size.getY()<1) return fail(s,"Altura invalida de la plantilla.");
        BlockPos anchor=s.getPlayerOrException().blockPosition();
        TemplateFootprint.Bounds footprint;
        try {
            footprint=TemplateFootprint.centered(anchor.getX(),anchor.getZ(),size.getX(),size.getZ(),false);
        } catch(IllegalArgumentException error) {
            return fail(s,"Dimensiones invalidas: "+error.getMessage());
        }
        say(s,"HUELLA PROPUESTA (sin rotacion) "+id+" X="+footprint.minX()+".."+footprint.maxX()
            +" Z="+footprint.minZ()+".."+footprint.maxZ()+" | "+footprint.width()+"x"+footprint.length()
            +" | area="+footprint.area()+" | chunks="+footprint.chunkCount()
            +" | Y="+anchor.getY()+".."+((long)anchor.getY()+size.getY()-1L));
        if(!footprint.withinMinecraftCoordinates())
            return fail(s,"Fuera de las coordenadas permitidas de Minecraft.");
        if(footprint.chunkCount()>1024L)
            return fail(s,"Huella muy extensa para comprobar chunks en un comando (mas de 1024); sigue catalogada.");
        if(!world.dimension().location().toString().equals("minecraft:overworld"))
            return fail(s,"Verificacion preliminar limitada al Overworld.");
        if(anchor.getY()<world.getMinBuildHeight()
            || (long)anchor.getY()+size.getY()-1L>=world.getMaxBuildHeight())
            return fail(s,"Altura Y fuera de los limites del mundo.");
        if(BuildEngine.busy() || WorldData.get(world).undo()!=null)
            return fail(s,"Hay una obra o respaldo pendiente; no se prepara otra.");
        int x0=(int)footprint.minX(),z0=(int)footprint.minZ();
        int x1=(int)footprint.maxX(),z1=(int)footprint.maxZ();
        WorldData data=WorldData.get(world);
        if(data.protectedFootprint(dim(world),x0,z0,x1,z1,8)
            || data.overlapsCompleted(dim(world),x0,z0,x1,z1,16))
            return fail(s,"La huella toca una zona protegida o un asentamiento BWE.");
        BlockPos spawn=world.getSharedSpawnPos();
        if(SiteMath.circleIntersectsRect(spawn.getX(),spawn.getZ(),160,x0,z0,x1,z1))
            return fail(s,"La huella toca la reserva de spawn (160 bloques).");
        for(long x:new long[]{footprint.minX(),footprint.maxX()})
            for(long z:new long[]{footprint.minZ(),footprint.maxZ()})
                if(!world.getWorldBorder().isWithinBounds(new BlockPos((int)x,anchor.getY(),(int)z)))
                    return fail(s,"La huella sale del worldborder.");
        // Chunk existence only: never call getChunk/getHeight on an unloaded location.
        for(long cx=footprint.minX()>>4;cx<=footprint.maxX()>>4;cx++)
            for(long cz=footprint.minZ()>>4;cz<=footprint.maxZ()>>4;cz++)
                if(!world.hasChunk((int)cx,(int)cz))
                    return fail(s,"Hay chunks sin cargar dentro de la huella. No se genero terreno.");
        say(s,"PREVERIFICACION COMPLETADA: limites, protecciones y chunks cargados. NO comprueba terreno, inventarios, jigsaw ni entidades; NO coloca bloques.");
        return 1;
    }

    /** Read-only incremental volume scan, only after the same footprint preflight. */
    private static int auditTemplate(CommandSourceStack s,String id) throws CommandSyntaxException {
        if(verifyTemplate(s,id)!=1) return 0;
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null) return fail(s,"ID de plantilla invalido.");
        Optional<StructureTemplate> found=s.getLevel().getStructureManager().get(key);
        if(found.isEmpty()) return fail(s,"Plantilla ya no disponible.");
        TemplateSiteAudit.Result result=TemplateSiteAudit.start(s.getLevel(),
            s.getPlayerOrException().blockPosition(),found.get().getSize(),
            s.getPlayerOrException().getUUID(),key);
        if(!result.ok()) return fail(s,result.message());
        say(s,result.message());
        return 1;
    }
    /** Heightmap-only terrain profile with seven-block buffer; never edits the world. */
    private static int surfaceTemplate(CommandSourceStack s,String id) throws CommandSyntaxException {
        if(verifyTemplate(s,id)!=1) return 0;
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null) return fail(s,"ID de plantilla invalido.");
        Optional<StructureTemplate> found=s.getLevel().getStructureManager().get(key);
        if(found.isEmpty()) return fail(s,"Plantilla ya no disponible.");
        TemplateSurfaceSurvey.Result result=TemplateSurfaceSurvey.start(s.getLevel(),
            s.getPlayerOrException().blockPosition(),found.get().getSize(),
            s.getPlayerOrException().getUUID(),key);
        if(!result.ok()) return fail(s,result.message());
        say(s,result.message());
        return 1;
    }
    private static int marginStatus(CommandSourceStack s) throws CommandSyntaxException {
        say(s,"Margen de medicion para tu proxima superficie: "
            +TemplateSurfaceSurvey.marginFor(player(s))+" bloques. Solo lectura; NO cambia /bwe crear.");
        return 1;
    }
    private static int configureMargin(CommandSourceStack s,int margin) throws CommandSyntaxException {
        TemplateSurfaceSurvey.Result result=TemplateSurfaceSurvey.configureMargin(player(s),margin);
        if(!result.ok()) return fail(s,result.message());
        say(s,result.message());
        return 1;
    }
    private static int surfaceStatus(CommandSourceStack s) {
        say(s,TemplateSurfaceSurvey.status());
        return 1;
    }
    private static int cancelSurface(CommandSourceStack s) {
        TemplateSurfaceSurvey.Result result=TemplateSurfaceSurvey.cancel();
        if(!result.ok()) return fail(s,result.message());
        say(s,result.message());
        return 1;
    }
    private static int auditStatus(CommandSourceStack s) {
        say(s,TemplateSiteAudit.status());
        return 1;
    }
    private static int cancelAudit(CommandSourceStack s) {
        TemplateSiteAudit.Result result=TemplateSiteAudit.cancel();
        if(!result.ok()) return fail(s,result.message());
        say(s,result.message());
        return 1;
    }

    private static int scan(CommandSourceStack s,int r) throws CommandSyntaxException {
        BlockPos center=s.getPlayerOrException().blockPosition();
        var result=WorldChecks.scan(s.getLevel(),center,r,r,4);
        UUID id=player(s);
        PLANS.remove(id); // A new scan must not leave an old selection active.
        if(result.ok()) SCANS.put(id,new ScanSite(center,dim(s.getLevel()),System.currentTimeMillis()));
        else SCANS.remove(id);
        say(s,"Radio "+r+" | "+(result.ok()?"APTO":"NO APTO")+": "+result.reason()
            +" | suelo "+result.low()+".."+result.high()+" | agua="+result.liquids()+" | artificial="+result.artificial());
        if(result.ok()) say(s,"Zona guardada. Usa /bwe listar, elige una construccion y luego /bwe crear.");
        return result.ok()?1:0;
    }
    /** Selects a type for the LAST scanned location, even after the player walks away. */
    private static int select(CommandSourceStack s,String type) throws CommandSyntaxException {
        if(!BuildEngine.KINDS.contains(type)) return fail(s,"Tipo desconocido. /bwe listar");
        if(BuildEngine.busy() || WorldData.get(s.getLevel()).undo()!=null)
            return fail(s,"Primero termina o deshaz la obra pendiente.");
        UUID id=player(s);
        ScanSite site=SCANS.get(id);
        if(site==null) return fail(s,"Primero /bwe escanear en el lugar deseado.");
        if(!site.dimension().equals(dim(s.getLevel()))) {
            SCANS.remove(id);
            return fail(s,"Dimension distinta: vuelve a escanear.");
        }
        if(System.currentTimeMillis()-site.created()>900_000L) {
            SCANS.remove(id);
            return fail(s,"Escaneo caducado: vuelve a /bwe escanear.");
        }
        int radius=BuildEngine.radius(type);
        var survey=WorldChecks.scanFull(s.getLevel(),site.center(),radius,radius,4);
        if(!survey.ok()) return fail(s,"La zona no es apta para "+type+": "+survey.reason());
        PLANS.put(id,new BuildEngine.Plan(type,"",site.center(),site.dimension(),
            survey.groundY(),radius,radius,System.currentTimeMillis()));
        say(s,"Seleccionado: "+type+" en X="+site.center().getX()+" Z="+site.center().getZ()
            +". Alejate de los bloques a construir y usa /bwe crear.");
        return 1;
    }
    /** No preview or extra confirmation. BuildEngine rechecks the whole area before editing. */
    private static int create(CommandSourceStack s) throws CommandSyntaxException {
        UUID id=player(s);
        BuildEngine.Plan plan=PLANS.get(id);
        if(plan==null) return fail(s,"Primero /bwe escanear y /bwe listar para seleccionar.");
        if(!plan.dimension().equals(dim(s.getLevel()))) return fail(s,"Dimension diferente de la seleccion.");
        BuildEngine.Result result=BuildEngine.build(s.getLevel(),plan,id);
        if(!result.ok()) return fail(s,result.message());
        PLANS.remove(id);
        SCANS.remove(id);
        say(s,result.message()+" | bloques="+result.count());
        return 1;
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
    /** Legacy alias: no misleading plan for an unimplemented feature. */
    private static int planTemplate(CommandSourceStack s,String id) {
        return inspectTemplate(s,id);
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
    /** Cancels only a plan that has NOT started. Never imply that a running build stopped. */
    private static int cancel(CommandSourceStack s) throws CommandSyntaxException {
        if(BuildEngine.busy())
            return fail(s,"Hay una construccion o restauracion en curso. /bwe cancelar NO la detiene; espera y usa /bwe deshacer si corresponde.");
        if(WorldData.get(s.getLevel()).undo()!=null)
            return fail(s,"Existe un respaldo de obra. /bwe cancelar NO lo elimina; usa /bwe deshacer o revisa /bwe estado.");
        if(PLANS.remove(player(s))==null)
            return fail(s,"No tienes un plan sin ejecutar que cancelar.");
        say(s,"Plan sin ejecutar cancelado. Esta orden no modifico el terreno.");
        return 1;
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
