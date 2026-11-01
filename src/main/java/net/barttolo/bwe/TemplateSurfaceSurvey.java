package net.barttolo.bwe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import java.util.UUID;

/** Read-only, incremental surface survey. NEVER changes or generates terrain.
 *  A median is a suggestion, not authorization to terraform or place NBT. */
public final class TemplateSurfaceSurvey {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COLUMNS_PER_TICK = 1024;
    private static final long MAX_COLUMNS = 1_000_000L;
    private static final long MAX_CHUNKS = 1024L;
    private static final int MARGIN = 7;
    private static Survey active;
    private static String lastResult = "Sin mediciones anteriores.";

    public record Result(boolean ok, String message) {}
    private static final class Survey {
        final ServerLevel world;
        final UUID operator;
        final ResourceLocation id;
        final TemplateFootprint.Bounds bounds;
        final SurfaceProfile profile;
        final long total;
        long processed;
        Survey(ServerLevel world, UUID operator, ResourceLocation id,
               TemplateFootprint.Bounds bounds) {
            this.world=world; this.operator=operator; this.id=id; this.bounds=bounds;
            this.total=bounds.area();
            this.profile=new SurfaceProfile(world.getMinBuildHeight(), world.getMaxBuildHeight());
        }
    }
    private TemplateSurfaceSurvey() {}

    public static Result start(ServerLevel world, BlockPos anchor, Vec3i size,
                               UUID operator, ResourceLocation id) {
        if(active!=null) return new Result(false,"Ya hay una medicion: "+status());
        if(BuildEngine.busy() || WorldData.get(world).undo()!=null)
            return new Result(false,"Hay una obra o respaldo pendiente.");
        if(size.getX()<1 || size.getZ()<1)
            return new Result(false,"Huella invalida.");
        TemplateFootprint.Bounds base, area;
        try {
            base=TemplateFootprint.centered(anchor.getX(),anchor.getZ(),size.getX(),size.getZ(),false);
            area=new TemplateFootprint.Bounds(base.minX()-MARGIN,base.minZ()-MARGIN,
                                               base.maxX()+MARGIN,base.maxZ()+MARGIN);
            if(!area.withinMinecraftCoordinates())
                return new Result(false,"Margen de 7 bloques fuera de limites Minecraft.");
            if(area.chunkCount()>MAX_CHUNKS || area.area()>MAX_COLUMNS)
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
        active=new Survey(world,operator,id,area);
        return new Result(true,"Medicion SOLO LECTURA iniciada para "+id+" | "+
            area.width()+"x"+area.length()+" (margen 7) | "+area.area()+
            " columnas, "+COLUMNS_PER_TICK+"/tick. /bwe superficieestado");
    }

    public static String status() {
        Survey s=active;
        if(s==null) return lastResult;
        return "Superficie "+s.id+": "+s.processed+"/"+s.total+" columnas (solo lectura).";
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
                int surface=s.world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                s.profile.add(surface);
                s.processed++;
            }
            if(s.processed==s.total) {
                int median=s.profile.median();
                stop(s,"SUPERFICIE "+s.id+" | area="+s.total+" columnas (margen 7)"
                    +" | Y="+s.profile.low()+".."+s.profile.high()
                    +" | base sugerida Y="+median
                    +" | corte aprox="+s.profile.cut(median)
                    +" | relleno aprox="+s.profile.fill(median)
                    +". SOLO LECTURA. La altura del mapa no identifica cuevas, cimientos,"
                    +" casas ni liquidos. NO se terraformo ni coloco NBT.");
            }
        } catch(Exception error) {
            LOGGER.error("[BWE] Error midiendo superficie {}",s.id,error);
            stop(s,"Medicion abortada por "+error.getClass().getSimpleName()
                +"; no se modificaron bloques.");
        }
    }
    private static void stop(Survey s,String message) {
        if(active==s) active=null;
        lastResult=message;
        LOGGER.info("[BWE] {}",message);
        var player=s.world.getServer().getPlayerList().getPlayer(s.operator);
        if(player!=null) player.sendSystemMessage(Component.literal("[BWE] "+message));
    }
}
