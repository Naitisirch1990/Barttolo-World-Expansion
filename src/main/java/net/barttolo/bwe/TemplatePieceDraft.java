package net.barttolo.bwe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Read-only planning of destructive replacement. No inventory/entity backup. */
public final class TemplatePieceDraft {
    public static final int MAX_CHANGES = 200_000;
    public static final int MAX_EXAMINED = 500_000;
    private static final int FOUNDATION_DEPTH = 64;
    public record Prepared(Map<Long, BlockState> changed,
                           TemplatePlacementVolume.Bounds volume,
                           int clearing, int foundation, int solid,
                           int ignoredBlockEntities, int ignoredEntities,
                           int alternativePalettes) {
        public Prepared { changed=Collections.unmodifiableMap(new LinkedHashMap<>(changed)); }
    }
    private TemplatePieceDraft() {}

    public static Prepared prepare(ServerLevel world, BuildEngine.Plan plan) {
        ResourceLocation id=ResourceLocation.tryParse(plan.template());
        if(id==null || !(id.getNamespace().equals("ctov") || id.getNamespace().equals("explorify")))
            throw new IllegalArgumentException("Solo NBT de CTOV o Explorify");
        StructureTemplate template=world.getStructureManager().get(id)
            .orElseThrow(() -> new IllegalArgumentException("La pieza NBT no esta instalada"));
        Vec3i size=template.getSize();
        var footprint=TemplatePlacementVolume.centered(plan.center().getX(),plan.y(),
            plan.center().getZ(),size.getX(),size.getY(),size.getZ(),false);
        TemplateNbtPiece.Blueprint piece=TemplateNbtPiece.read(template);
        LinkedHashMap<Long,BlockState> targets=new LinkedHashMap<>();
        for(var cell:piece.cells()) {
            BlockPos at=new BlockPos((int)footprint.minX()+cell.x(),plan.y()+cell.y(),(int)footprint.minZ()+cell.z());
            BlockState target=cell.state();
            if(target.is(Blocks.JIGSAW) || target.is(Blocks.STRUCTURE_BLOCK)) target=Blocks.AIR.defaultBlockState();
            targets.put(at.asLong(),target);
        }
        return prepareVolume(world,plan,footprint,targets,piece.blockEntityNbt(),piece.entities(),piece.paletteVariants());
    }

    public static Prepared prepareDrawing(ServerLevel world, BuildEngine.Plan plan,
                                         Map<Long,BlockState> targets,int height) {
        var footprint=TemplatePlacementVolume.centered(plan.center().getX(),plan.y(),
            plan.center().getZ(),plan.halfX()*2+1,Math.max(14,height),plan.halfZ()*2+1,false);
        return prepareVolume(world,plan,footprint,targets,0,0,1);
    }

    private static Prepared prepareVolume(ServerLevel world, BuildEngine.Plan plan,
            TemplatePlacementVolume.Bounds footprint,Map<Long,BlockState> targets,
            int specialNbt,int entities,int palettes) {
        if(!world.dimension().location().toString().equals(plan.dimension()))
            throw new IllegalArgumentException("Dimension distinta de la seleccion");
        if(!plan.dimension().equals("minecraft:overworld"))
            throw new IllegalArgumentException("Construccion solo en Overworld");
        if(plan.y()<=world.getMinBuildHeight() || plan.y()>=world.getMaxBuildHeight())
            throw new IllegalArgumentException("Altura de cimentacion fuera del mundo");
        int depth=Math.min(FOUNDATION_DEPTH,plan.y()-world.getMinBuildHeight());
        var clear=TemplatePlacementVolume.clearance(footprint,plan.margin(),depth);
        if(!clear.withinWorld(world.getMinBuildHeight(),world.getMaxBuildHeight()))
            throw new IllegalArgumentException("La estructura excede la altura del mundo");
        int x0=(int)clear.minX(),x1=(int)clear.maxX(),z0=(int)clear.minZ(),z1=(int)clear.maxZ();
        String dim=plan.dimension(); WorldData data=WorldData.get(world);
        if(data.protectedFootprint(dim,x0,z0,x1,z1,8) || data.overlapsCompleted(dim,x0,z0,x1,z1,16))
            throw new IllegalArgumentException("Zona protegida o asentamiento BWE; usa /bwe olvidar para quitar un registro propio");
        BlockPos spawn=world.getSharedSpawnPos();
        if(SiteMath.circleIntersectsRect(spawn.getX(),spawn.getZ(),160,x0,z0,x1,z1))
            throw new IllegalArgumentException("Reserva de spawn de 160 bloques");
        for(long x:new long[]{clear.minX(),clear.maxX()}) for(long z:new long[]{clear.minZ(),clear.maxZ()})
            if(!world.getWorldBorder().isWithinBounds(new BlockPos((int)x,plan.y(),(int)z)))
                throw new IllegalArgumentException("Fuera del borde del mundo");
        for(long cx=clear.minX()>>4;cx<=clear.maxX()>>4;cx++)
            for(long cz=clear.minZ()>>4;cz<=clear.maxZ()>>4;cz++)
                if(!world.hasChunk((int)cx,(int)cz)) throw new IllegalArgumentException("Chunk no cargado; acercate al area");
        AABB safety=new AABB(x0,clear.minY(),z0,x1+1.0,world.getMaxBuildHeight(),z1+1.0);
        if(!world.getEntitiesOfClass(ServerPlayer.class,safety).isEmpty())
            throw new IllegalArgumentException("Aleja a todos los jugadores del area antes de crear; los mobs se eliminaran automaticamente");
        long columns=(long)(x1-x0+1)*(z1-z0+1);
        if(columns*(footprint.maxY()-plan.y()+1)>MAX_EXAMINED)
            throw new IllegalArgumentException("Area demasiado grande para un trabajo");
        LinkedHashMap<Long,BlockState> desired=new LinkedHashMap<>();
        int examined=0;
        for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) {
            int top=Math.max((int)footprint.maxY()+1,world.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));
            for(int y=top-1;y>=plan.y();y--) {
                if(++examined>MAX_EXAMINED) throw new IllegalArgumentException("Limite de analisis excedido");
                // Include AIR too: undo must cover cells changed later by neighbour updates.
                desired.put(BlockPos.asLong(x,y,z),Blocks.AIR.defaultBlockState());
                if(desired.size()>MAX_CHANGES) throw new IllegalArgumentException("Limite de respaldo excedido");
            }
            boolean supported=false;
            for(int y=plan.y()-1;y>=plan.y()-depth;y--) {
                if(++examined>MAX_EXAMINED) throw new IllegalArgumentException("Limite de analisis excedido");
                BlockPos at=new BlockPos(x,y,z); BlockState old=world.getBlockState(at);
                if(y==plan.y()-1 || old.isAir() || !old.getFluidState().isEmpty() || old.canBeReplaced()) {
                    desired.put(at.asLong(),(y>=plan.y()-3?Blocks.DIRT:Blocks.STONE).defaultBlockState());
                }
                if(!old.isAir() && old.getFluidState().isEmpty() && !old.canBeReplaced()) { supported=true;break; }
                if(desired.size()>MAX_CHANGES) throw new IllegalArgumentException("Limite de cimentacion excedido");
            }
            if(!supported) throw new IllegalArgumentException("No hay apoyo a menos de "+depth+" bloques bajo la base; baja la altura elegida");
        }
        for(var entry:targets.entrySet()) {
            BlockPos at=BlockPos.of(entry.getKey());
            if(at.getX()<x0 || at.getX()>x1 || at.getZ()<z0 || at.getZ()>z1
                || at.getY()<clear.minY() || at.getY()>clear.maxY())
                throw new IllegalArgumentException("Dibujo fuera del volumen previsto: "+at);
            desired.put(entry.getKey(),entry.getValue());
            if(desired.size()>MAX_CHANGES) throw new IllegalArgumentException("Limite de respaldo excedido");
        }
        ArrayList<Map.Entry<Long,BlockState>> ordered=new ArrayList<>(desired.entrySet());
        ordered.sort(Comparator.<Map.Entry<Long,BlockState>,Integer>comparing(e -> e.getValue().isAir()?0:1)
            .thenComparingLong(e -> e.getValue().isAir()?-(long)BlockPos.of(e.getKey()).getY():BlockPos.of(e.getKey()).getY()));
        LinkedHashMap<Long,BlockState> result=new LinkedHashMap<>();int air=0,fill=0,solid=0;
        for(var e:ordered) {
            result.put(e.getKey(),e.getValue());
            if(e.getValue().isAir()) air++; else if(BlockPos.of(e.getKey()).getY()<plan.y()) fill++; else solid++;
        }
        return new Prepared(result,clear,air,fill,solid,specialNbt,entities,palettes);
    }
}
