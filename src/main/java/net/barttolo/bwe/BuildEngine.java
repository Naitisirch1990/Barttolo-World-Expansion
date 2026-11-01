package net.barttolo.bwe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.*;

/** Original tiny settlement builder + optional placement of a single external NBT piece.
 *  No external mod assets are bundled in this project. */
public final class BuildEngine {
    public static final Set<String> KINDS = Set.of("aldea", "campamento", "ruina", "torre", "refugio", "altar", "pozo", "carreta");
    private static final int MAX_CHANGES = 55_000;
    private static final int BLOCKS_PER_TICK = 200;
    private static Job running;
    /** A restart may leave an incomplete build. Undo tolerates blocks still in BEFORE state. */
    private static UndoJob undoRunning;
    private static final class UndoJob {
        final ServerLevel world;
        final List<WorldData.StoredBlock> blocks;
        final List<UUID> spawned;
        final String name;
        final AABB footprint;
        final Set<UUID> ownedEntities;
        int cursor;
        UndoJob(ServerLevel world, WorldData.Undo snapshot) {
            this.world = world;
            this.blocks = snapshot.blocks();
            this.spawned = snapshot.entities();
            this.name = snapshot.name();
            this.ownedEntities = Set.copyOf(snapshot.entities());
            this.footprint = undoFootprint(snapshot);
            this.cursor = blocks.size() - 1;
        }
    }
    private static final class Job {
        final ServerLevel world;
        final Plan plan;
        final UUID operator;
        final Iterator<Map.Entry<Long, BlockState>> remaining;
        final List<WorldData.StoredBlock> originals;
        final List<UUID> reservedEntities;
        final Map<Long, net.minecraft.nbt.CompoundTag> expectedBefore = new HashMap<>();
        final int total;
        int placed;
        Job(ServerLevel world, Plan plan, UUID operator, Map<Long,BlockState> changes,
            List<WorldData.StoredBlock> originals, List<UUID> reservedEntities) {
            this.world = world;
            this.plan = plan;
            this.operator = operator;
            this.remaining = changes.entrySet().iterator();
            this.originals = originals;
            this.reservedEntities = reservedEntities;
            for(WorldData.StoredBlock block : originals) expectedBefore.put(block.pos(), block.before());
            this.total = changes.size();
        }
    }
    /** Never retain a ServerLevel from a stopped integrated/dedicated server.
     *  Incomplete work is intentionally NOT finalized: its persisted Undo
     *  snapshot can be inspected and rolled back after the next start. */
    public static void resetForShutdown() {
        running = null;
        undoRunning = null;
    }
    public static boolean busy() { return running != null || undoRunning != null; }
    public static String status() {
        if(undoRunning != null) return "Restaurando " + undoRunning.name + ": " +
            (undoRunning.blocks.size()-1-undoRunning.cursor) + "/" + undoRunning.blocks.size() + " bloques (200/tick).";
        if(running == null) return "Sin construcciones en curso.";
        return running.plan.kind() + " " + running.placed + "/" + running.total + " bloques (200/tick).";
    }
    public static void serverTick() {
        if(undoRunning != null) { undoTick(); return; }
        Job job = running;
        if(job == null) return;
        try {
            // The area can become occupied after /bwe confirmar SI. Stop before
            // the next batch instead of placing blocks under newly arrived entities.
            // A partial job keeps its durable undo snapshot for /bwe deshacer.
            if (occupied(job.world, job.plan))
                throw new IllegalStateException("Entidad entro en la huella durante la obra");
            int applied = 0;
            while (job.remaining.hasNext() && applied++ < BLOCKS_PER_TICK) {
                var entry = job.remaining.next();
                BlockPos at = BlockPos.of(entry.getKey());
                if (!job.world.hasChunk(at.getX() >> 4, at.getZ() >> 4))
                    throw new IllegalStateException("Chunk descargado durante la obra en " + at);
                // Avoid premature physics updates for multi-block beds and planted crops.
                // Revalidate every block before changing it. If someone builds while
                // the job runs, stop instead of overwriting their work.
                if(!WorldData.encode(job.world.getBlockState(at)).equals(job.expectedBefore.get(entry.getKey())))
                    throw new IllegalStateException("Bloque cambiado desde el plan en " + at);
                if(!job.world.setBlock(at, entry.getValue(), 2))
                    throw new IllegalStateException("No se pudo colocar el bloque en " + at);
                job.placed++;
            }
            if(job.remaining.hasNext()) return;
            int spawned = 0;
            if(job.plan.kind().equals("aldea")) {
                int index=0;
                for(int[] at:new int[][]{{-12,-12},{14,-12},{-12,14},{14,14}}) {
                    Villager villager = EntityType.VILLAGER.create(job.world);
                    UUID reserved = job.reservedEntities.get(index++);
                    if(villager != null) {
                        // Persist the UUID BEFORE spawning. A crash during entity creation
                        // must never leave an untracked villager behind on rollback.
                        villager.setUUID(reserved);
                        villager.moveTo(job.plan.center().getX()+at[0]+.5,job.plan.y()+1,job.plan.center().getZ()+at[1]+.5,0,0);
                        if(job.world.addFreshEntity(villager)) spawned++;
                    }
                }
            }
            ArrayList<WorldData.StoredBlock> finalBlocks = new ArrayList<>(job.originals.size());
            for(WorldData.StoredBlock b:job.originals) {
                finalBlocks.add(new WorldData.StoredBlock(b.pos(), b.before(),
                    WorldData.encode(job.world.getBlockState(BlockPos.of(b.pos())))));
            }
            WorldData.get(job.world).setUndo(new WorldData.Undo(job.plan.dimension(),job.plan.kind(),finalBlocks,job.reservedEntities));
            job.world.getServer().overworld().getDataStorage().save();
            var operator = job.world.getServer().getPlayerList().getPlayer(job.operator);
            if(operator != null) operator.sendSystemMessage(Component.literal("[BWE] Obra terminada: " + job.plan.kind()
                 + " (" + job.total + " bloques). /bwe deshacer o /bwe finalizar SI"));
            running = null;
        } catch(Exception error) {
            System.err.println("[BWE] Fallo durante construccion, snapshot conservado: " + error);
            var operator = job.world.getServer().getPlayerList().getPlayer(job.operator);
            if(operator != null) operator.sendSystemMessage(Component.literal("[BWE] ERROR: obra detenida. No uses /bwe finalizar. Restaura copia del mundo si es necesario."));
            running = null;
        }
    }
    /** Undo is deliberately incremental. No mass block updates in a single tick. */
    private static void undoTick() {
        UndoJob job = undoRunning;
        if(job == null) return;
        try {
            // Do not restore solid blocks through players, animals, dropped items
            // or vehicles that entered after /bwe deshacer. Ignore only villagers
            // spawned by this exact BWE job, which are removed after rollback.
            if (undoOccupied(job))
                throw new IllegalStateException("Entidad entro en el area durante la restauracion");
            int examined = 0;
            while(job.cursor >= 0 && examined < BLOCKS_PER_TICK) {
                examined++;
                WorldData.StoredBlock b = job.blocks.get(job.cursor);
                BlockPos at = BlockPos.of(b.pos());
                if(!job.world.hasChunk(at.getX() >> 4, at.getZ() >> 4))
                    throw new IllegalStateException("Chunk descargado durante rollback: " + at);
                var current = WorldData.encode(job.world.getBlockState(at));
                if(current.equals(b.before())) { // Never alter blocks already restored or never built.
                    job.cursor--;
                    continue;
                }
                if(b.after().isEmpty() || !current.equals(b.after()))
                    throw new IllegalStateException("Bloque modificado por un jugador en " + at);
                if(!job.world.setBlock(at, WorldData.decode(b.before()), 2))
                    throw new IllegalStateException("No se pudo restaurar " + at);
                job.cursor--;
            }
            if(job.cursor >= 0) return;
            for(UUID id : job.spawned) {
                var entity = job.world.getEntity(id);
                if(entity != null) entity.discard();
            }
            WorldData.get(job.world).clearUndo();
            job.world.getServer().overworld().getDataStorage().save();
            System.out.println("[BWE] Restauracion completa de " + job.name + " (" + job.blocks.size() + " registros)");
            undoRunning = null;
        } catch(Exception e) {
            System.err.println("[BWE] Restauracion incompleta; backup conservado. " + e);
            undoRunning = null;
        }
    }
    private BuildEngine() {}

    public record Plan(String kind, String template, BlockPos center, String dimension,
                       int y, int halfX, int halfZ, long created) {}
    public record Result(boolean ok, String message, int count) {}

    /** Shared, deliberately conservative entity guard for both dry-run and build.
     *  Rejects every existing entity, including dropped items, vehicles and decorations.
     *  Does not move or discard anything. */
    private static boolean occupied(ServerLevel world, Plan plan) {
        AABB footprint = new AABB(plan.center().getX()-plan.halfX(),plan.y()-3,
            plan.center().getZ()-plan.halfZ(),plan.center().getX()+plan.halfX()+1,
            plan.y()+18,plan.center().getZ()+plan.halfZ()+1);
        return !world.getEntitiesOfClass(Entity.class, footprint).isEmpty();
    }

    /** Exact block-change bounding box, expanded by one block for entities.
     *  This deliberately does not rely on a stale player plan after restart. */
    private static AABB undoFootprint(WorldData.Undo snapshot) {
        if (snapshot.blocks().isEmpty()) return null;
        int minX=Integer.MAX_VALUE, minY=Integer.MAX_VALUE, minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE, maxY=Integer.MIN_VALUE, maxZ=Integer.MIN_VALUE;
        for (WorldData.StoredBlock block : snapshot.blocks()) {
            BlockPos pos=BlockPos.of(block.pos());
            minX=Math.min(minX,pos.getX()); minY=Math.min(minY,pos.getY()); minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX()); maxY=Math.max(maxY,pos.getY()); maxZ=Math.max(maxZ,pos.getZ());
        }
        return new AABB(minX-1.0,minY-1.0,minZ-1.0,maxX+2.0,maxY+2.0,maxZ+2.0);
    }

    private static boolean undoOccupied(UndoJob job) {
        return job.footprint != null && job.world.getEntitiesOfClass(Entity.class, job.footprint)
            .stream().anyMatch(entity -> !job.ownedEntities.contains(entity.getUUID()));
    }

    public static int radius(String kind) {
        return switch (kind) { case "aldea" -> 22; case "campamento" -> 9; case "ruina" -> 10; case "refugio" -> 7; case "altar" -> 7; case "pozo" -> 6; case "carreta" -> 10; default -> 8; };
    }

    private static final class Drawing {
        final ServerLevel world;
        final Map<Long, BlockState> placements = new LinkedHashMap<>();
        final int ground;
        final BlockPos center;
        final Palette palette;
        Drawing(ServerLevel world, BlockPos center, int ground) {
            this.world=world; this.center=center; this.ground=ground;
            String biome=world.getBiome(center).unwrapKey().map(k -> k.location().getPath()).orElse("plains");
            if (biome.contains("desert") || biome.contains("badlands")) {
                palette = new Palette(Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(),
                    Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE_SLAB.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState());
            } else if (biome.contains("savanna")) {
                palette = new Palette(Blocks.ACACIA_LOG.defaultBlockState(),
                    Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.ACACIA_SLAB.defaultBlockState(),
                    Blocks.COARSE_DIRT.defaultBlockState());
            } else if (biome.contains("jungle") || biome.contains("bamboo")) {
                palette = new Palette(Blocks.JUNGLE_LOG.defaultBlockState(),
                    Blocks.JUNGLE_PLANKS.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.JUNGLE_PLANKS.defaultBlockState(), Blocks.JUNGLE_SLAB.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState());
            } else if (biome.contains("swamp") || biome.contains("mangrove")) {
                palette = new Palette(Blocks.MANGROVE_LOG.defaultBlockState(),
                    Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.MUD.defaultBlockState(),
                    Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.MANGROVE_SLAB.defaultBlockState(),
                    Blocks.PACKED_MUD.defaultBlockState());
            } else if (biome.contains("taiga") || biome.contains("snow") || biome.contains("frozen")) {
                palette = new Palette(Blocks.SPRUCE_LOG.defaultBlockState(),
                    Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState());
            } else {
                palette = new Palette(Blocks.OAK_LOG.defaultBlockState(),
                    Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        void put(int dx,int dy,int dz,BlockState state) {
            BlockPos pos=center.offset(dx,ground+dy-center.getY(),dz);
            // Key uses world coordinates, planning remains side-effect free.
            placements.put(pos.asLong(),state);
        }
        void rect(int x0,int z0,int x1,int z1,int y, BlockState state) {
            for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) put(x,y,z,state);
        }
        void column(int x,int z,int y0,int y1,BlockState state) {
            for(int y=y0;y<=y1;y++) put(x,y,z,state);
        }
        /** Only remove natural blocks inside actual construction footprints, not across an entire biome.
         *  Each footprint supports a small amount of terrain variation. All edits are still
         *  preflighted before touching a single block and can be undone in a test world. */
        void clearFootprint(int x0,int z0,int x1,int z1,int headroom) {
            for (int x=x0; x<=x1; x++) for (int z=z0;z<=z1;z++) {
                int gx=center.getX()+x, gz=center.getZ()+z;
                int soilY=WorldChecks.ground(world,gx,gz);
                if (soilY==Integer.MIN_VALUE || Math.abs((soilY+1)-ground)>4)
                    throw new IllegalArgumentException("Terreno irregular: "+gx+","+gz);
                // Refuse tall trunks instead of cutting them halfway and leaving floating crowns.
                for(int y=ground+headroom+1; y<=Math.min(world.getMaxBuildHeight()-1,ground+headroom+8); y++) {
                    if(world.getBlockState(new BlockPos(gx,y,gz)).is(BlockTags.LOGS))
                        throw new IllegalArgumentException("Arbol alto fuera del limite de limpieza en "+gx+","+gz);
                }
                // Replace only above-ground vegetation and uneven natural obstacles.
                for (int y=ground;y<=ground+headroom;y++) put(x,y-ground,z,Blocks.AIR.defaultBlockState());
                // Fill terrain under the local target level, including the immediate foundation.
                for (int y=soilY+1;y<ground;y++) put(x,y-ground,z,Blocks.DIRT.defaultBlockState());
                put(x,-1,z,palette.surface);
            }
        }
        void corridor(int x0,int z0,int x1,int z1,BlockState surface) {
            clearFootprint(x0,z0,x1,z1,3);
            rect(x0,z0,x1,z1,-1,surface);
        }
        void lamp(int x,int z) {
            put(x,0,z,Blocks.COBBLESTONE_WALL.defaultBlockState());
            column(x,z,1,3,Blocks.OAK_FENCE.defaultBlockState());
            put(x,4,z,Blocks.GLOWSTONE.defaultBlockState());
        }
        void house(int x,int z) {
            clearFootprint(x-1,z-1,x+8,z+8,7);
            rect(x,z,x+7,z+7,-1,palette.wall);
            rect(x+1,z+1,x+6,z+6,0,Blocks.OAK_PLANKS.defaultBlockState());
            for(int dy=1;dy<=4;dy++) {
                for(int a=0;a<8;a++) {
                    put(x+a,dy,z,(a==0 || a==7) ? palette.log : palette.wall);
                    put(x+a,dy,z+7,(a==0 || a==7) ? palette.log : palette.wall);
                    put(x,dy,z+a,(a==0 || a==7) ? palette.log : palette.wall);
                    put(x+7,dy,z+a,(a==0 || a==7) ? palette.log : palette.wall);
                }
            }
            for(int dy=0;dy<=1;dy++) put(x+3,dy,z+7,Blocks.AIR.defaultBlockState());
            put(x+3,2,z,Blocks.GLASS_PANE.defaultBlockState());
            put(x+7,2,z+3,Blocks.GLASS_PANE.defaultBlockState());
            for(int x1=x-1;x1<=x+8;x1++) for(int z1=z-1;z1<=z+8;z1++)
                put(x1,5,z1,palette.roof);
            rect(x,z,x+7,z+7,6,palette.roofSlab);
            put(x+2,1,z+2,Blocks.CRAFTING_TABLE.defaultBlockState());
            put(x+5,1,z+2,Blocks.STONECUTTER.defaultBlockState());
            // Complete bed pair instead of a carpet placeholder: enables village sleeping POI.
            put(x+2,1,z+4,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,Direction.SOUTH));
            put(x+2,1,z+5,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,Direction.SOUTH));
            put(x+4,1,z+5,Blocks.TORCH.defaultBlockState());
        }
        void basicPath() {
            corridor(-2,-22,2,22,palette.path);
            corridor(-22,-2,22,2,palette.path);
            // Village bell square.
            corridor(-3,-3,3,3,Blocks.COBBLESTONE.defaultBlockState());
            put(0,0,0,Blocks.OAK_FENCE.defaultBlockState());
            put(0,1,0,Blocks.BELL.defaultBlockState());
            lamp(-4,-4); lamp(4,4);
        }
        void farm(int x,int z) {
            clearFootprint(x,z,x+6,z+7,3);
            rect(x,z,x+6,z+7,-1,Blocks.FARMLAND.defaultBlockState());
            for(int xx=x;xx<=x+6;xx++) for(int zz=z;zz<=z+7;zz++)
                if (xx == x+3) put(xx,-1,zz,Blocks.WATER.defaultBlockState());
                else put(xx,0,zz,Blocks.WHEAT.defaultBlockState());
            lamp(x-1,z-1);
        }
        void draw(String kind) {
            switch(kind) {
                case "aldea" -> {
                    basicPath();
                    house(-17,-17); house(10,-17); house(-17,10); house(10,10);
                    farm(12,-3);
                    lamp(7,8); lamp(-8,7);
                }
                case "campamento" -> {
                    clearFootprint(-7,-5,7,7,5);
                    rect(-3,-3,3,3,-1,Blocks.COARSE_DIRT.defaultBlockState());
                    put(0,0,0,Blocks.CAMPFIRE.defaultBlockState());
                    for(int x=-4;x<=4;x+=8) {
                        rect(x-2,1,x+2,5,0,Blocks.OAK_PLANKS.defaultBlockState());
                        rect(x-2,1,x+2,5,3,Blocks.WHITE_WOOL.defaultBlockState());
                        column(x-2,1,1,2,Blocks.OAK_FENCE.defaultBlockState());
                        column(x+2,1,1,2,Blocks.OAK_FENCE.defaultBlockState());
                        column(x-2,5,1,2,Blocks.OAK_FENCE.defaultBlockState());
                        column(x+2,5,1,2,Blocks.OAK_FENCE.defaultBlockState());
                    }
                    put(5,0,-4,Blocks.CRAFTING_TABLE.defaultBlockState());
                }
                case "ruina" -> {
                    clearFootprint(-6,-6,6,6,8);
                    rect(-5,-5,5,5,-1,Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                    for(int a=-5;a<=5;a++) for(int y=0;y<=3;y++) {
                        if ((a+y)%3 != 0) {
                            put(a,y,-5,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                            put(-5,y,a,Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    column(5,-5,0,6,Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                    column(5,5,0,3,Blocks.STONE_BRICKS.defaultBlockState());
                    put(0,0,0,Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                }
                case "refugio" -> {
                    clearFootprint(-5,-5,5,5,7);
                    house(-4,-4);
                    lamp(4,4);
                    put(3,0,-3,Blocks.CRAFTING_TABLE.defaultBlockState());
                }
                case "pozo" -> {
                    clearFootprint(-5,-5,5,5,6);
                    rect(-3,-3,3,3,-1,Blocks.COBBLESTONE.defaultBlockState());
                    rect(-2,-2,2,2,0,Blocks.STONE_BRICKS.defaultBlockState());
                    rect(-1,-1,1,1,0,Blocks.BLUE_TERRACOTTA.defaultBlockState());
                    for(int x=-3;x<=3;x+=6) for(int z=-3;z<=3;z+=6)
                        column(x,z,0,3,Blocks.OAK_FENCE.defaultBlockState());
                    rect(-4,-4,4,4,4,Blocks.SPRUCE_SLAB.defaultBlockState());
                    put(0,3,0,Blocks.GLOWSTONE.defaultBlockState());
                }
                case "carreta" -> {
                    clearFootprint(-8,-6,8,6,6);
                    rect(-4,-2,4,2,0,Blocks.OAK_PLANKS.defaultBlockState());
                    for(int x=-3;x<=3;x+=6) for(int z=-3;z<=3;z+=6)
                        put(x,0,z,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    for(int x=-4;x<=4;x+=8) for(int z=-2;z<=2;z+=4)
                        column(x,z,1,2,Blocks.OAK_FENCE.defaultBlockState());
                    rect(-5,-3,5,3,3,Blocks.WHITE_WOOL.defaultBlockState());
                    column(5,0,0,1,Blocks.OAK_FENCE.defaultBlockState());
                    column(6,0,0,1,Blocks.OAK_FENCE.defaultBlockState());
                    put(0,1,0,Blocks.HAY_BLOCK.defaultBlockState());
                }
                case "altar" -> {
                    clearFootprint(-6,-6,6,6,7);
                    rect(-4,-4,4,4,-1,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    for (int[] spot:new int[][]{{-3,-3},{-3,3},{3,-3},{3,3}}) {
                        column(spot[0],spot[1],0,3,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                        put(spot[0],4,spot[1],Blocks.GLOWSTONE.defaultBlockState());
                    }
                    put(0,0,0,Blocks.LODESTONE.defaultBlockState());
                }
                default -> {
                    clearFootprint(-6,-6,6,6,13);
                    rect(-4,-4,4,4,-1,Blocks.COBBLESTONE.defaultBlockState());
                    for(int x=-4;x<=4;x+=8) for(int z=-4;z<=4;z+=8) column(x,z,0,9,Blocks.SPRUCE_LOG.defaultBlockState());
                    rect(-5,-5,5,5,10,Blocks.SPRUCE_PLANKS.defaultBlockState());
                    for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++)
                        if(x==-5 || x==5 || z==-5 || z==5) put(x,11,z,Blocks.OAK_FENCE.defaultBlockState());
                    put(0,11,0,Blocks.GLOWSTONE.defaultBlockState());
                }
            }
        }
    }
    private record Palette(BlockState log, BlockState wall, BlockState surface,
                           BlockState roof, BlockState roofSlab, BlockState path) {}

    public static Result build(ServerLevel world, Plan p, UUID operator) {
        if(busy()) return new Result(false,"Ya existe una construccion en curso: "+status(),0);
        if(WorldData.get(world).undo()!=null) return new Result(false,"Hay una obra sin finalizar: /bwe deshacer o /bwe finalizar",0);
        WorldChecks.Survey review=WorldChecks.scanFull(world,p.center(),p.halfX(),p.halfZ(),4);
        if(!review.ok() || review.groundY()!=p.y()) return new Result(false,"La revision cambio: "+review.reason(),0);
        if(occupied(world,p))
            return new Result(false,"Hay entidades dentro del area (jugadores, mobs u objetos). Despejenla antes de confirmar.",0);
        // External NBT pieces often contain jigsaws, chests and loot/BE metadata.
        // The old implementation could not undo their block entity NBT: fail closed.
        if(!p.template().isEmpty()) return new Result(false,
            "Plantillas CTOV/Explorify solo en catalogo hasta implementar colocacion reversible de NBT.",0);
        Drawing drawing=new Drawing(world,p.center(),p.y());
        try { drawing.draw(p.kind()); }
        catch (IllegalArgumentException e) { return new Result(false,"Terreno inseguro: "+e.getMessage(),0); }
        Map<Long,BlockState> changed= new LinkedHashMap<>();
        for(var entry:drawing.placements.entrySet()) {
            BlockPos pos=BlockPos.of(entry.getKey());
            if(!world.hasChunk(pos.getX()>>4,pos.getZ()>>4)) return new Result(false,"Chunk sin cargar",0);
            if(!world.getWorldBorder().isWithinBounds(pos)) return new Result(false,"Bloque fuera del borde del mundo",0);
            BlockState old=world.getBlockState(pos);
            if(old.hasBlockEntity() || !WorldChecks.natural(old)) return new Result(false,"Bloque preexistente potencialmente construido en "+pos+"; cancelado",0);
            if(!old.equals(entry.getValue())) changed.put(entry.getKey(),entry.getValue());
        }
        if(changed.size()>MAX_CHANGES) return new Result(false,"Demasiados bloques a modificar: "+changed.size(),0);
        ArrayList<WorldData.StoredBlock> undoBlocks=new ArrayList<>(changed.size());
        for(var entry:changed.entrySet()) {
            BlockState before=world.getBlockState(BlockPos.of(entry.getKey()));
            undoBlocks.add(new WorldData.StoredBlock(entry.getKey(),WorldData.encode(before),WorldData.encode(entry.getValue())));
        }
        // Snapshot BEFORE modifying any block, with EXPECTED after state, so restart
        // halfway through a build can be rolled back (BEFORE and AFTER both accepted).
        // Only one undo slot is active.
        WorldData data=WorldData.get(world);
        ArrayList<UUID> reservedEntities = new ArrayList<>();
        if(p.kind().equals("aldea")) for(int i=0;i<4;i++) reservedEntities.add(UUID.randomUUID());
        data.setUndo(new WorldData.Undo(p.dimension(),p.kind(),undoBlocks,reservedEntities));
        // Save undo to disk before applying any edits. The job then writes 200 blocks per tick.
        world.getServer().overworld().getDataStorage().save();
        running = new Job(world,p,operator,changed,undoBlocks,reservedEntities);
        return new Result(true,"Obra iniciada por etapas. /bwe estado para ver el avance.",changed.size());
    }

    public static Result undo(ServerLevel world) {
        if(busy()) return new Result(false,"Hay otra operacion en curso: "+status(),0);
        WorldData data=WorldData.get(world);
        WorldData.Undo snapshot=data.undo();
        if(snapshot==null) return new Result(false,"No hay construccion pendiente",0);
        if(!snapshot.dimension().equals(world.dimension().location().toString()))
            return new Result(false,"Entra en la misma dimension de la construccion",0);
        // Validate ALL entries before starting rollback: never overwrite later player edits.
        // Includes half-built worlds after unexpected restart.
        for(WorldData.StoredBlock b : snapshot.blocks()) {
            BlockPos pos=BlockPos.of(b.pos());
            if(!world.hasChunk(pos.getX()>>4,pos.getZ()>>4))
                return new Result(false,"Carga todos los chunks antes de deshacer",0);
            // Decode every backed-up state BEFORE restoring the first block.
            // Otherwise a corrupt/obsolete property encountered late in the
            // rollback could leave the world half restored.
            try { WorldData.decode(b.before()); }
            catch(RuntimeException error) {
                return new Result(false,"Respaldo incompatible en "+pos+": "+error.getMessage()+". No se modifico el mundo.",0);
            }
            var state=WorldData.encode(world.getBlockState(pos));
            if(!state.equals(b.before()) && (b.after().isEmpty() || !state.equals(b.after())))
                return new Result(false,"Hay cambios posteriores en "+pos+". Se conserva el backup y NO se sobrescribe.",0);
        }
        UndoJob job = new UndoJob(world, snapshot);
        if (undoOccupied(job))
            return new Result(false,"Hay jugadores, mobs u objetos en la zona. Despejala antes de deshacer; respaldo conservado.",0);
        undoRunning = job;
        return new Result(true,"Restauracion iniciada por etapas, consulta /bwe estado",snapshot.blocks().size());
    }

    /** Finalize only a wholly finished build; never discard an incomplete crash snapshot. */
    public static Result canFinalize(ServerLevel world) {
        if(busy()) return new Result(false,"Hay una obra o restauracion en curso",0);
        WorldData.Undo snapshot=WorldData.get(world).undo();
        if(snapshot==null) return new Result(false,"No hay obra pendiente",0);
        if(!snapshot.dimension().equals(world.dimension().location().toString()))
            return new Result(false,"Debes estar en la dimension original",0);
        for(WorldData.StoredBlock b : snapshot.blocks()) {
            BlockPos at=BlockPos.of(b.pos());
            if(!world.hasChunk(at.getX()>>4,at.getZ()>>4))
                return new Result(false,"Chunks no cargados. Acercate a la obra.",0);
            if(b.after().isEmpty() || !b.after().equals(WorldData.encode(world.getBlockState(at))))
                return new Result(false,"La obra no coincide con el estado final en " + at + "; NO se elimina el respaldo.",0);
        }
        return new Result(true,"Obra completa. Respaldo listo para finalizar.",snapshot.blocks().size());
    }

    /** Non-destructive, operator-facing check before any block is edited. */
    public static Result simulate(ServerLevel world, Plan p) {
        if(busy()) return new Result(false,"Construccion o restauracion en curso",0);
        if(WorldData.get(world).undo()!=null)
            return new Result(false,"Existe una obra pendiente. Usa /bwe deshacer o /bwe finalizar SI",0);
        WorldChecks.Survey review=WorldChecks.scanFull(world,p.center(),p.halfX(),p.halfZ(),4);
        if(!review.ok() || review.groundY()!=p.y()) return new Result(false,"Terreno cambiado: "+review.reason(),0);
        if(occupied(world,p)) return new Result(false,
            "SIMULACION CANCELADA: hay entidades en el volumen de la obra.",0);
        if(!p.template().isEmpty())
            return new Result(false,"Las plantillas externas estan en modo catalogo: colocacion suspendida hasta verificar rollback de NBT y entidades.",0);
        Drawing d=new Drawing(world,p.center(),p.y());
        try { d.draw(p.kind()); }
        catch(IllegalArgumentException ex) { return new Result(false,"Terreno inseguro: "+ex.getMessage(),0); }
        int changed=0;
        for(var entry:d.placements.entrySet()) {
            BlockPos pos=BlockPos.of(entry.getKey());
            if(!world.hasChunk(pos.getX()>>4,pos.getZ()>>4)) return new Result(false,"Chunk no cargado en "+pos,0);
            if(!world.getWorldBorder().isWithinBounds(pos)) return new Result(false,"Fuera del borde del mundo: "+pos,0);
            BlockState old=world.getBlockState(pos);
            if(old.hasBlockEntity() || !WorldChecks.natural(old)) return new Result(false,"Bloque protegido/preexistente en "+pos,0);
            if(!old.equals(entry.getValue())) changed++;
        }
        if(changed>MAX_CHANGES) return new Result(false,"Limite de seguridad excedido",0);
        return new Result(true,"SIMULACION SIN CAMBIOS: "+p.kind()+" modificaria "+changed+" bloques. Area: " +
            (p.halfX()*2+1)+"x"+(p.halfZ()*2+1)+". Recuerda proteger bases y misiones.",changed);
    }
}
