package net.barttolo.bwe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/** Read-only, incremental surface survey. NEVER changes or generates terrain.
 *  A median is a suggestion, not authorization to terraform or place NBT. */
public final class TemplateSurfaceSurvey {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COLUMNS_PER_TICK = 16; // <= 16 * 48 = 768 block-state reads/tick
    private static final long MAX_COLUMNS = 1_000_000L;
    private static final long MAX_CHUNKS = 1024L;
    private static final int DEFAULT_MARGIN = 7;
    private static final Map<UUID,Integer> OPERATOR_MARGINS = new HashMap<>();
    private static Survey active;
    private static String lastResult = "Sin mediciones anteriores.";

    public record Result(boolean ok, String message) {}
    private static final class Survey {
        final ServerLevel world;
        final UUID operator;
        final ResourceLocation id;
        final TemplateFootprint.Bounds bounds; // ONLY the structure's foundation
        final TemplateFootprint.Bounds clearance; // foundation + safety margin
        final int margin;
        final int templateHeight;
        final SurfaceProfile profile;
        final long total;
        long processed;
        long unresolved;
        long coveredObstacles;
        Survey(ServerLevel world, UUID operator, ResourceLocation id,
               TemplateFootprint.Bounds foundation, TemplateFootprint.Bounds clearance, int margin, int templateHeight) {
            this.world=world; this.operator=operator; this.id=id;
            this.bounds=foundation; this.clearance=clearance; this.margin=margin;
            this.templateHeight=templateHeight;
            this.total=foundation.area();
            this.profile=new SurfaceProfile(world.getMinBuildHeight(), world.getMaxBuildHeight());
        }
    }
    private TemplateSurfaceSurvey() {}

    /** Server-thread-only guard: only one expensive read-only scan at a time. */
    public static boolean isRunning() { return active != null; }

    /** Operator preference is temporary and affects read-only surveys only.
     *  Future terraform/build commands MUST NOT assume this grants permission to write.
     */
    public static int marginFor(UUID operator) {
        return OPERATOR_MARGINS.getOrDefault(operator, DEFAULT_MARGIN);
    }
    public static Result configureMargin(UUID operator, int margin) {
        if(margin<5 || margin>10)
            return new Result(false,"Margen permitido: 5..10 bloques.");
        OPERATOR_MARGINS.put(operator,margin);
        return new Result(true,"Margen de medicion configurado en "+margin
            +" bloques (temporal hasta reinicio). SOLO LECTURA: no limpia ni terraforma.");
    }

    public static Result start(ServerLevel world, BlockPos anchor, Vec3i size,
                               UUID operator, ResourceLocation id) {
        if(active!=null) return new Result(false,"Ya hay una medicion: "+status());
        if(TemplateSiteAudit.isRunning())
            return new Result(false,"Hay una auditoria 3D en curso; espera o cancelala antes de medir superficie.");
        if(BuildEngine.busy() || WorldData.get(world).undo()!=null)
            return new Result(false,"Hay una obra o respaldo pendiente.");
        if(size.getX()<1 || size.getY()<1 || size.getZ()<1)
            return new Result(false,"Huella invalida.");
        int margin=marginFor(operator);
        TemplateFootprint.Bounds base, area;
        try {
            base=TemplateFootprint.centered(anchor.getX(),anchor.getZ(),size.getX(),size.getZ(),false);
            area=TemplateFootprint.expanded(base,margin);
            if(!area.withinMinecraftCoordinates())
                return new Result(false,"Margen de "+margin+" bloques fuera de limites Minecraft.");
            if(area.chunkCount()>MAX_CHUNKS || base.area()>MAX_COLUMNS)
                return new Result(false,"Area demasiado extensa para medir en una sesion; sigue catalogada.");
        } catch(ArithmeticException | IllegalArgumentException error) {
            return new Result(false,"Huella invalida: "+error.getMessage());
        }
        String dim=world.dimension().location().toString();
        if(!dim.equals("minecraft:overworld"))
            return new Result(false,"Medicion limitada al Overworld.");
        int x0=(int)area.minX(),z0=(int)area.minZ(),x1=(int)area.maxX(),z1=(int)area.maxZ();
        WorldData data=WorldData.get(world);
        if(data.protectedFootprint(dim,x0,z0,x1,z1,0)
            || data.overlapsCompleted(dim,x0,z0,x1,z1,0))
            return new Result(false,"El margen toca una zona protegida o asentamiento BWE.");
        BlockPos spawn=world.getSharedSpawnPos();
        if(SiteMath.circleIntersectsRect(spawn.getX(),spawn.getZ(),160,x0,z0,x1,z1))
            return new Result(false,"El margen toca la reserva de spawn.");
        for(long x:new long[]{area.minX(),area.maxX()})
            for(long z:new long[]{area.minZ(),area.maxZ()})
                if(!world.getWorldBorder().isWithinBounds(new BlockPos((int)x,anchor.getY(),(int)z)))
                    return new Result(false,"El margen sale del worldborder.");
        for(long cx=area.minX()>>4;cx<=area.maxX()>>4;cx++)
            for(long cz=area.minZ()>>4;cz<=area.maxZ()>>4;cz++)
                if(!world.hasChunk((int)cx,(int)cz))
                    return new Result(false,"Chunk sin cargar dentro del margen: no se genero terreno.");
        active=new Survey(world,operator,id,base,area,margin,size.getY());
        return new Result(true,"Medicion SOLO LECTURA iniciada para "+id
            +" | base="+base.width()+"x"+base.length()+" ("+base.area()+" columnas)"
            +" | zona de despeje propuesta="+area.width()+"x"+area.length()
            +" (margen "+margin+") | "+COLUMNS_PER_TICK+"/tick. /bwe superficieestado");
    }

    public static String status() {
        Survey s=active;
        if(s==null) return lastResult;
        return "Superficie "+s.id+": "+s.processed+"/"+s.total
            +" columnas de la BASE (margen de despeje "+s.margin+", solo lectura).";
    }
    public static Result cancel() {
        if(active==null) return new Result(false,"No hay medicion activa.");
        String id=active.id.toString();
        active=null;
        lastResult="Medicion cancelada para "+id+"; no se modificaron bloques.";
        return new Result(true,lastResult);
    }
    public static void resetForShutdown() {
        active=null;
        OPERATOR_MARGINS.clear();
        lastResult="Sin mediciones anteriores.";
    }
    public static void serverTick() {
        Survey s=active;
        if(s==null) return;
        try {
            if(BuildEngine.busy() || WorldData.get(s.world).undo()!=null) {
                stop(s,"Medicion interrumpida por una obra o respaldo pendiente.");
                return;
            }
            int budget=COLUMNS_PER_TICK;
            while(budget-->0 && s.processed<s.total) {
                long index=s.processed;
                int x=(int)(s.bounds.minX()+index%s.bounds.width());
                int z=(int)(s.bounds.minZ()+index/s.bounds.width());
                if(!s.world.hasChunk(x>>4,z>>4)) {
                    stop(s,"Medicion interrumpida: chunk descargado; no se genero terreno.");
                    return;
                }
                int topY=s.world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                TerrainColumnProbe.Result candidate=TerrainColumnProbe.findGroundLayered(
                    topY,s.world.getMinBuildHeight(),
                    y -> classifyNaturalGround(s.world.getBlockState(new BlockPos(x,y,z))));
                if(candidate.found()) {
                    s.profile.add(candidate.groundY());
                    if(candidate.ignoredAbove()>0) s.coveredObstacles++;
                } else s.unresolved++;
                s.processed++;
            }
            if(s.processed==s.total) {
                String coverage=" | suelo reconocido="+s.profile.count()+"/"+s.total
                    +" | obstaculos sobre base="+s.coveredObstacles
                    +" | sin clasificar="+s.unresolved;
                if(s.profile.count()==0 || s.unresolved>s.total/10L) {
                    stop(s,"SUPERFICIE "+s.id+coverage
                        +" | SIN BASE SUGERIDA: terreno no identificable con seguridad"
                        +" (profundidad maxima "+TerrainColumnProbe.MAX_DEPTH+")."
                        +" No se modificaron bloques.");
                    return;
                }
                int median=s.profile.median();
                // This is only a geometric estimate. It does not count actual block
                // changes or reserve undo storage; never authorize edits from it.
                ClearanceVolume.Estimate clearance=ClearanceVolume.calculate(
                    s.clearance.width(),s.clearance.length(),s.templateHeight,s.margin,
                    median,s.world.getMinBuildHeight(),s.world.getMaxBuildHeight());
                if(!clearance.withinWorld()) {
                    stop(s,"SUPERFICIE "+s.id+coverage
                        +" | SIN BASE SUGERIDA: volumen vertical propuesto Y="
                        +clearance.minY()+".."+clearance.maxY()
                        +" excede limites del mundo. SOLO LECTURA.");
                    return;
                }
                stop(s,"SUPERFICIE "+s.id+" | base="+s.total+" columnas"
                    +" | margen de despeje="+s.margin+" (area "+s.clearance.area()+")"
                    +coverage+" | despeje geometrico="+clearance.blocks()+" bloques (Y="
                    +clearance.minY()+".."+clearance.maxY()+", NO son cambios reales)"
                    +" | Y="+s.profile.low()+".."+s.profile.high()
                    +" | base sugerida Y="+median
                    +" | corte aprox="+s.profile.cut(median)
                    +" | relleno aprox="+s.profile.fill(median)
                    +(s.unresolved>0?" (solo columnas reconocidas)":"")
                    +". SOLO LECTURA. Heuristica: techos gruesos de piedra pueden"
                    +" confundirse con terreno. No detecta cuevas ni garantiza cimientos."
                    +" NO se terraformo ni coloco NBT.");
            }
        } catch(Exception error) {
            LOGGER.error("[BWE] Error midiendo superficie {}",s.id,error);
            stop(s,"Medicion abortada por "+error.getClass().getSimpleName()
                +"; no se modificaron bloques.");
        }
    }
    /** Treat masonry-like natural blocks as uncertain unless at least four
     *  consecutive layers exist. Two layers of sandstone/stone may be a roof.
     *  This remains a heuristic, never permission to replace a building. */
    private static int classifyNaturalGround(BlockState state) {
        if(state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.GRANITE) || state.is(Blocks.ANDESITE)
            || state.is(Blocks.DIORITE) || state.is(Blocks.TUFF)
            || state.is(Blocks.CALCITE) || state.is(Blocks.SANDSTONE)
            || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.TERRACOTTA)
            || state.is(Blocks.PACKED_MUD) || state.is(Blocks.SNOW_BLOCK))
            return TerrainColumnProbe.ROCK;
        return isLikelyNaturalGround(state) ? TerrainColumnProbe.SOIL : TerrainColumnProbe.NONE;
    }
    /** Conservative vanilla-ground heuristic; unknown modded blocks remain unknown.
     *  This is not an assertion that a block was generated naturally. */
    private static boolean isLikelyNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
            || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM)
            || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD)
            || state.is(Blocks.PACKED_MUD) || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
            || state.is(Blocks.CLAY) || state.is(Blocks.STONE)
            || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRANITE)
            || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
            || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE)
            || state.is(Blocks.TERRACOTTA) || state.is(Blocks.SANDSTONE)
            || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SNOW_BLOCK);
    }
    private static void stop(Survey s,String message) {
        if(active==s) active=null;
        lastResult=message;
        LOGGER.info("[BWE] {}",message);
        var player=s.world.getServer().getPlayerList().getPlayer(s.operator);
        if(player!=null) player.sendSystemMessage(Component.literal("[BWE] "+message));
    }
}
