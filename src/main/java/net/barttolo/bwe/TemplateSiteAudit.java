package net.barttolo.bwe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.util.UUID;

/** Incremental, read-only audit of an existing site's full 3D volume.
 *  Does not place templates, inspect inventory NBT, or assemble jigsaw structures.
 *  Exactly one audit may run per JVM; reset on server shutdown. */
public final class TemplateSiteAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BLOCKS_PER_TICK = 1024;
    private static final long MAX_SCAN_BLOCKS = 8_000_000L;
    private static Audit active;
    // Keep the final report in memory so an offline operator can read it later.
    // Cleared on server shutdown; never persisted or treated as build approval.
    private static String lastResult = "No hay auditoria de plantilla anterior.";

    private static final class Audit {
        final ServerLevel world;
        final UUID operator;
        final String templateId;
        final TemplateScanCursor cursor;
        long air, fluids, blockEntities, nonNatural;
        Audit(ServerLevel world, UUID operator, String templateId, TemplateScanCursor cursor) {
            this.world=world; this.operator=operator; this.templateId=templateId; this.cursor=cursor;
        }
    }
    public record Result(boolean ok, String message) {}
    private TemplateSiteAudit() {}

    /** Server-thread-only guard: avoid running two expensive read-only scans at once. */
    public static boolean isRunning() { return active != null; }

    public static Result start(ServerLevel world, BlockPos anchor, Vec3i size,
                               UUID operator, ResourceLocation templateId) {
        return startInternal(world, anchor, size, operator, templateId, false);
    }

    /** Read-only audit of the full proposed demolition/terraform volume.
     *  Uses the operator's CURRENT Y, not the surface survey's suggested median.
     *  No approval to destroy blocks or move entities is implied. */
    public static Result startClearance(ServerLevel world, BlockPos anchor, Vec3i size,
                                        UUID operator, ResourceLocation templateId) {
        return startInternal(world, anchor, size, operator, templateId, true);
    }

    private static Result startInternal(ServerLevel world, BlockPos anchor, Vec3i size,
                                        UUID operator, ResourceLocation templateId,
                                        boolean clearance) {
        if(active!=null) return new Result(false,"Ya hay una auditoria: "+status());
        if(TemplateSurfaceSurvey.isRunning())
            return new Result(false,"Hay una medicion de superficie en curso; espera o cancelala antes de auditar.");
        if(BuildEngine.busy() || WorldData.get(world).undo()!=null)
            return new Result(false,"Hay una obra o respaldo pendiente.");
        if(size.getX()<1 || size.getY()<1 || size.getZ()<1)
            return new Result(false,"Dimensiones de plantilla invalidas.");
        TemplateFootprint.Bounds bounds;
        TemplateScanCursor cursor;
        try {
            bounds=TemplateFootprint.centered(anchor.getX(),anchor.getZ(),size.getX(),size.getZ(),false);
            long bottom=anchor.getY();
            long top=(long)anchor.getY()+size.getY()-1L;
            if(clearance) {
                int margin=TemplateSurfaceSurvey.marginFor(operator);
                bounds=TemplateFootprint.expanded(bounds,margin);
                ClearanceVolume.Estimate estimate=ClearanceVolume.calculate(
                    bounds.width(),bounds.length(),size.getY(),margin,
                    anchor.getY(),world.getMinBuildHeight(),world.getMaxBuildHeight());
                if(!estimate.withinWorld())
                    return new Result(false,"Despeje fuera de altura mundial: Y="
                        +estimate.minY()+".."+estimate.maxY()+".");
                bottom=estimate.minY();
                top=estimate.maxY();
            }
            if(!bounds.withinMinecraftCoordinates() || bottom<world.getMinBuildHeight()
                || top>=world.getMaxBuildHeight())
                return new Result(false,"Huella o altura fuera de los limites del mundo.");
            if(bounds.chunkCount()>1024L)
                return new Result(false,"Mas de 1024 chunks: auditoria no iniciada; sigue catalogada.");
            cursor=new TemplateScanCursor(bounds,(int)bottom,(int)top);
        } catch(ArithmeticException | IllegalArgumentException ex) {
            return new Result(false,"Volumen invalido: "+ex.getMessage());
        }
        if(cursor.total()>MAX_SCAN_BLOCKS)
            return new Result(false,"Volumen de "+cursor.total()+" bloques excede el maximo de "
                +MAX_SCAN_BLOCKS+" por auditoria; sigue disponible en el catalogo.");
        // Full clearance may overlap a protected area even when the template itself does not.
        if(clearance) {
            String dim=world.dimension().location().toString();
            if(!dim.equals("minecraft:overworld"))
                return new Result(false,"Despeje limitado al Overworld.");
            WorldData data=WorldData.get(world);
            int x0=(int)bounds.minX(), z0=(int)bounds.minZ();
            int x1=(int)bounds.maxX(), z1=(int)bounds.maxZ();
            // Match the conservative buffers used by /bwe verificarplantilla.
            // An expanded clearance area must not bypass those exclusions.
            if(data.protectedFootprint(dim,x0,z0,x1,z1,8)
                || data.overlapsCompleted(dim,x0,z0,x1,z1,16))
                return new Result(false,"El despeje o su zona de seguridad toca un area protegida o asentamiento BWE.");
            BlockPos spawn=world.getSharedSpawnPos();
            if(SiteMath.circleIntersectsRect(spawn.getX(),spawn.getZ(),160,x0,z0,x1,z1))
                return new Result(false,"El margen toca la reserva del spawn.");
            for(long x:new long[]{bounds.minX(),bounds.maxX()})
                for(long z:new long[]{bounds.minZ(),bounds.maxZ()})
                    if(!world.getWorldBorder().isWithinBounds(new BlockPos((int)x,anchor.getY(),(int)z)))
                        return new Result(false,"El margen sale del worldborder.");
        }
        // Never force-load or generate chunks, even at the start.
        for(long cx=bounds.minX()>>4;cx<=bounds.maxX()>>4;cx++)
            for(long cz=bounds.minZ()>>4;cz<=bounds.maxZ()>>4;cz++)
                if(!world.hasChunk((int)cx,(int)cz))
                    return new Result(false,"Chunk sin cargar: auditoria no iniciada.");
        active=new Audit(world,operator,templateId.toString()
            +(clearance?" [DESPEJE COMPLETO]":" [PIEZA]"),cursor);
        return new Result(true,"Auditoria SOLO LECTURA iniciada: "+templateId
            +(clearance?" | despeje con margen "+TemplateSurfaceSurvey.marginFor(operator)
                +" y Y provisional del operador (NO base mediana)":" | solo volumen de la pieza")
            +" | "+cursor.total()+" bloques; "+BLOCKS_PER_TICK
            +"/tick. /bwe auditoria para progreso.");
    }

    public static String status() {
        Audit a=active;
        if(a==null) return lastResult;
        return "Auditoria "+a.templateId+": "+a.cursor.processed()+"/"+a.cursor.total()
            +" bloques | aire="+a.air+" | liquidos="+a.fluids
            +" | posibles block entities="+a.blockEntities
            +". No se esta modificando el mundo.";
    }

    public static Result cancel() {
        if(active==null) return new Result(false,"No hay auditoria que cancelar.");
        String id=active.templateId;
        active=null;
        lastResult="Auditoria "+id+" cancelada; no se modificaron bloques.";
        return new Result(true,lastResult);
    }

    public static void resetForShutdown() {
        active=null;
        lastResult="No hay auditoria de plantilla anterior.";
    }

    public static void serverTick() {
        Audit a=active;
        if(a==null) return;
        try {
            if(BuildEngine.busy() || WorldData.get(a.world).undo()!=null) {
                stop(a,"Auditoria interrumpida: otra obra o respaldo activo.");
                return;
            }
            int budget=BLOCKS_PER_TICK;
            while(budget-->0 && a.cursor.hasNext()) {
                TemplateScanCursor.Position p=a.cursor.next();
                if(!a.world.hasChunk(p.x()>>4,p.z()>>4)) {
                    stop(a,"Auditoria interrumpida: chunk descargado; NO se genero terreno.");
                    return;
                }
                BlockState state=a.world.getBlockState(new BlockPos(p.x(),p.y(),p.z()));
                if(state.isAir()) a.air++;
                if(state.hasBlockEntity()) a.blockEntities++;
                // Count categories independently: waterlogged stairs/chests are
                // both fluid-bearing AND non-natural. Exclude only pure fluid blocks.
                if(!state.getFluidState().isEmpty()) a.fluids++;
                if(!WorldChecks.natural(state) && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA))
                    a.nonNatural++;
            }
            if(!a.cursor.hasNext()) {
                stop(a,"AUDITORIA COMPLETA (solo lectura) "+a.templateId
                    +" | volumen="+a.cursor.total()+" | aire="+a.air
                    +" | liquidos="+a.fluids+" | bloques con block entity="+a.blockEntities
                    +" | bloques no clasificados como naturales="+a.nonNatural
                    +" (categorias no excluyentes). NO demuestra que el sitio sea seguro para construir: minerales, "
                    +"jigsaw, inventarios NBT, entidades y cambios posteriores requieren "
                    +"comprobaciones adicionales. NO se coloco ninguna plantilla.");
            }
        } catch(Exception error) {
            LOGGER.error("[BWE] Fallo en auditoria de plantilla {}",a.templateId,error);
            stop(a,"Auditoria abortada por error "+error.getClass().getSimpleName()
                +"; NO se modificaron bloques.");
        }
    }

    private static void stop(Audit a,String message) {
        if(active==a) {
            active=null;
            lastResult=message;
        }
        LOGGER.info("[BWE] {}",message);
        var player=a.world.getServer().getPlayerList().getPlayer(a.operator);
        if(player!=null) player.sendSystemMessage(Component.literal("[BWE] "+message));
    }
}
