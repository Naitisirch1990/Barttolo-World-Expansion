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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Strict non-destructive preparation of a SINGLE external CTOV/Explorify NBT piece.
 * Produces final block states and a safety footprint but NEVER calls setBlock.
 *
 * The future writer must use exactly this verified draft, persist all BEFORE
 * states prior to the first edit and recheck protection/player occupancy per tick.
 * NBT block-entity payloads and entities are explicitly outside this draft.
 */
public final class TemplatePieceDraft {
    public static final int MAX_CHANGES = 55_000;
    public static final int MAX_EXAMINED = 160_000;
    private static final int FOUNDATION_DEPTH = 12;

    public record Prepared(Map<Long, BlockState> changed,
                           TemplatePlacementVolume.Bounds volume,
                           int clearing, int foundation, int solid,
                           int ignoredBlockEntities, int ignoredEntities,
                           int alternativePalettes) {
        public Prepared {
            changed = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(changed));
        }
    }

    private TemplatePieceDraft() {}

    public static Prepared prepare(ServerLevel world, BuildEngine.Plan plan) {
        if (!world.dimension().location().toString().equals("minecraft:overworld"))
            throw new IllegalArgumentException("Las plantillas externas se preparan solo en Overworld");
        ResourceLocation id = ResourceLocation.tryParse(plan.template());
        if (id == null || !(id.getNamespace().equals("ctov") || id.getNamespace().equals("explorify")))
            throw new IllegalArgumentException("Solo NBT de CTOV o Explorify");
        Optional<StructureTemplate> loaded = world.getStructureManager().get(id);
        if (loaded.isEmpty()) throw new IllegalArgumentException("La pieza NBT ya no esta disponible");
        StructureTemplate template = loaded.get();
        Vec3i size = template.getSize();
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1)
            throw new IllegalArgumentException("Dimensiones NBT invalidas");
        var footprint = TemplatePlacementVolume.centered(
            plan.center().getX(), plan.y(), plan.center().getZ(),
            size.getX(), size.getY(), size.getZ(), false);
        var clear = TemplatePlacementVolume.clearance(footprint, plan.margin(), FOUNDATION_DEPTH);
        if (!clear.withinWorld(world.getMinBuildHeight(), world.getMaxBuildHeight()))
            throw new IllegalArgumentException("La pieza o su cimentacion queda fuera del mundo");
        long baseVolume;
        try { baseVolume = clear.blockCount(); }
        catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Volumen demasiado grande", overflow);
        }
        if (baseVolume > MAX_EXAMINED)
            throw new IllegalArgumentException("Volumen provisional mayor que el limite de ensayo "
                + MAX_EXAMINED + " bloques (pieza aun disponible en catalogo)");
        int x0=(int)clear.minX(), z0=(int)clear.minZ();
        int x1=(int)clear.maxX(), z1=(int)clear.maxZ();
        String dim=world.dimension().location().toString();
        WorldData data=WorldData.get(world);
        if (data.protectedFootprint(dim,x0,z0,x1,z1,8)
            || data.overlapsCompleted(dim,x0,z0,x1,z1,16))
            throw new IllegalArgumentException("Colision con proteccion o asentamiento BWE");
        var spawn=world.getSharedSpawnPos();
        if (SiteMath.circleIntersectsRect(spawn.getX(), spawn.getZ(), 160, x0,z0,x1,z1))
            throw new IllegalArgumentException("La pieza invade la reserva de spawn");
        for (long x : new long[]{clear.minX(),clear.maxX()})
            for (long z : new long[]{clear.minZ(),clear.maxZ()})
                if (!world.getWorldBorder().isWithinBounds(new BlockPos((int)x,plan.y(),(int)z)))
                    throw new IllegalArgumentException("Fuera del worldborder");
        // Do this before getHeight/getBlockState, which otherwise may load terrain.
        for (long cx=clear.minX()>>4; cx<=clear.maxX()>>4; cx++)
            for (long cz=clear.minZ()>>4; cz<=clear.maxZ()>>4; cz++)
                if (!world.hasChunk((int)cx,(int)cz))
                    throw new IllegalArgumentException("Chunk no cargado: " + cx + "," + cz);

        AABB safety = new AABB(clear.minX(),clear.minY(),clear.minZ(),
            clear.maxX()+1.0,world.getMaxBuildHeight(),clear.maxZ()+1.0);
        if (!world.getEntitiesOfClass(ServerPlayer.class, safety).isEmpty())
            throw new IllegalArgumentException("Hay jugadores dentro de la futura obra; alejalos primero");

        TemplateNbtPiece.Blueprint piece=TemplateNbtPiece.read(template);
        // Compute the FINAL state of every affected position: absent NBT blocks
        // are AIR, including the full vertical headroom and the selected margin.
        LinkedHashMap<Long,BlockState> desired=new LinkedHashMap<>();
        int examined=0;
        for (int x=x0; x<=x1; x++) for (int z=z0; z<=z1; z++) {
            int top=Math.max((int)footprint.maxY()+1,
                world.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));
            if (top>world.getMaxBuildHeight()) throw new IllegalArgumentException("Altura fuera del mundo");
            for (int y=top-1; y>=plan.y(); y--) {
                if (++examined>MAX_EXAMINED)
                    throw new IllegalArgumentException("Demasiados bloques para analizar en un comando");
                BlockPos pos=new BlockPos(x,y,z);
                if (!world.getBlockState(pos).isAir())
                    desired.put(pos.asLong(),Blocks.AIR.defaultBlockState());
                if (desired.size()>MAX_CHANGES) throw new IllegalArgumentException("Obra demasiado grande para el respaldo actual");
            }
            boolean supported=false;
            for (int y=plan.y()-1; y>=plan.y()-FOUNDATION_DEPTH; y--) {
                if (++examined>MAX_EXAMINED)
                    throw new IllegalArgumentException("Demasiados bloques para analizar en un comando");
                BlockPos pos=new BlockPos(x,y,z);
                BlockState old=world.getBlockState(pos);
                if (old.isAir() || !old.getFluidState().isEmpty() || old.canBeReplaced()) {
                    if (!old.equals(Blocks.DIRT.defaultBlockState()))
                        desired.put(pos.asLong(),Blocks.DIRT.defaultBlockState());
                } else {
                    supported=true;
                    break;
                }
                if (desired.size()>MAX_CHANGES) throw new IllegalArgumentException("Cimentacion demasiado grande para el respaldo actual");
            }
            if (!supported)
                throw new IllegalArgumentException("No hay soporte debajo de X="+x+" Z="+z
                    +" en "+FOUNDATION_DEPTH+" bloques; elige una zona mas estable");
        }

        for (var cell:piece.cells()) {
            BlockPos at=new BlockPos((int)footprint.minX()+cell.x(),
                plan.y()+cell.y(),(int)footprint.minZ()+cell.z());
            BlockState target=cell.state();
            // Assembly-only connector/marker blocks must not appear as physical
            // blocks in a standalone house or campsite.
            if (target.is(Blocks.JIGSAW) || target.is(Blocks.STRUCTURE_BLOCK))
                target=Blocks.AIR.defaultBlockState();
            if (world.getBlockState(at).equals(target))
                desired.remove(at.asLong());
            else desired.put(at.asLong(),target);
            if (desired.size()>MAX_CHANGES)
                throw new IllegalArgumentException("Pieza demasiado grande para el respaldo actual");
        }
        if (desired.isEmpty())
            throw new IllegalArgumentException("La pieza no produce cambios; no se crea respaldo vacio");

        // Air first (top-down), then solids bottom-up to reduce unsupported
        // placement while preserving a single final change per position.
        ArrayList<Map.Entry<Long, BlockState>> ordered=new ArrayList<>(desired.entrySet());
        ordered.sort(Comparator.<Map.Entry<Long,BlockState>,Integer>comparing(e ->
            e.getValue().isAir() ? 0 : 1).thenComparingLong(e -> {
            int y=BlockPos.of(e.getKey()).getY();
            return e.getValue().isAir() ? -(long)y : (long)y;
        }));
        LinkedHashMap<Long,BlockState> finalPlan=new LinkedHashMap<>();
        int clearing=0,foundation=0,solid=0;
        for (var entry:ordered) {
            finalPlan.put(entry.getKey(),entry.getValue());
            if (entry.getValue().isAir()) clearing++;
            else if (entry.getValue().is(Blocks.DIRT)) foundation++;
            else solid++;
        }
        return new Prepared(finalPlan,clear,clearing,foundation,solid,
            piece.blockEntityNbt(),piece.entities(),piece.paletteVariants());
    }
}
