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
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final Set<String> KINDS = Set.of("aldea", "campamento", "ruina", "torre", "refugio", "altar", "pozo", "carreta", "herreria", "posada", "granja", "establo", "fortin", "mercado", "aldea_grande");
    private static final int MAX_CHANGES = 200_000;
    private static final int BLOCKS_PER_TICK = 200;
    // Suppress intermediate shape updates and drops while a multiblock structure is incomplete.
    // UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS
    private static final int WRITE_FLAGS = 2 | 16 | 32;
    private static Job running;
    /** A restart may leave an incomplete build. Undo tolerates blocks still in BEFORE state. */
    private static UndoJob undoRunning;
    private static final class UndoJob {
        final ServerLevel world;
        final List<WorldData.StoredBlock> blocks;
        final List<UUID> spawned;
        final String name;
        final AABB footprint;
        int cursor;
        int ticks;
        boolean waiting;
        UndoJob(ServerLevel world, WorldData.Undo snapshot) {
            this.world = world;
            this.blocks = new ArrayList<>(snapshot.blocks());
            // Cursor walks backwards: sort descending so supports restore bottom-up.
            this.blocks.sort(Comparator.comparingInt((WorldData.StoredBlock b) ->
                BlockPos.of(b.pos()).getY()).reversed());
            this.spawned = snapshot.entities();
            this.name = snapshot.name();
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
        final int total;
        final TemplatePlacementVolume.Bounds externalClearance;
        final AABB workArea;
        boolean waitingForEntities;
        int placed;
        Job(ServerLevel world, Plan plan, UUID operator, Map<Long,BlockState> changes,
            List<WorldData.StoredBlock> originals, List<UUID> reservedEntities) {
            this(world,plan,operator,changes,originals,reservedEntities,null);
        }
        Job(ServerLevel world, Plan plan, UUID operator, Map<Long,BlockState> changes,
            List<WorldData.StoredBlock> originals, List<UUID> reservedEntities,
            TemplatePlacementVolume.Bounds externalClearance) {
            this.externalClearance = externalClearance;
            this.world = world;
            this.plan = plan;
            this.operator = operator;
            this.remaining = changes.entrySet().iterator();
            this.originals = originals;
            this.reservedEntities = reservedEntities;
            this.total = changes.size();
            this.workArea = externalClearance == null
                ? undoFootprint(new WorldData.Undo(plan.dimension(),plan.kind(),originals,reservedEntities))
                : new AABB(externalClearance.minX(),externalClearance.minY(),externalClearance.minZ(),
                    externalClearance.maxX()+1.0,world.getMaxBuildHeight(),externalClearance.maxZ()+1.0);
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
            (undoRunning.blocks.size()-1-undoRunning.cursor) + "/" + undoRunning.blocks.size() + " bloques (200/tick)." + (undoRunning.waiting?" EN ESPERA: aleja a los jugadores.":"");
        if(running == null) return "Sin construcciones en curso.";
        return running.plan.kind() + " " + running.placed + "/" + running.total
            + " bloques (200/tick)." + (running.waitingForEntities ? " EN ESPERA: aleja a los jugadores del area." : "");
    }
    public static void serverTick() {
        if(undoRunning != null) { undoTick(); return; }
        Job job = running;
        if(job == null) return;
        try {
            // External pieces may clear AIR under players: check the ENTIRE
            // clearance, including air removal above the operator.
            if (job.externalClearance != null) {
                var b=job.externalClearance;
                AABB area=new AABB(b.minX(),b.minY(),b.minZ(),
                    b.maxX()+1.0,job.world.getMaxBuildHeight(),b.maxZ()+1.0);
                if (!job.world.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,area).isEmpty()) {
                    job.waitingForEntities=true;
                    return;
                }
                String dim=job.world.dimension().location().toString();
                WorldData data=WorldData.get(job.world);
                if (!dim.equals(job.plan.dimension())
                    || data.protectedFootprint(dim,(int)b.minX(),(int)b.minZ(),(int)b.maxX(),(int)b.maxZ(),8)
                    || data.overlapsCompleted(dim,(int)b.minX(),(int)b.minZ(),(int)b.maxX(),(int)b.maxZ(),16))
                    throw new IllegalStateException("Cambio de dimension o proteccion durante obra NBT");
                var spawn=job.world.getSharedSpawnPos();
                if (SiteMath.circleIntersectsRect(spawn.getX(),spawn.getZ(),160,
                    (int)b.minX(),(int)b.minZ(),(int)b.maxX(),(int)b.maxZ()))
                    throw new IllegalStateException("Reserva de spawn afecta obra NBT");
                for (long x:new long[]{b.minX(),b.maxX()})
                    for (long z:new long[]{b.minZ(),b.maxZ()})
                        if (!job.world.getWorldBorder().isWithinBounds(
                            new BlockPos((int)x,job.plan.y(),(int)z)))
                            throw new IllegalStateException("Worldborder cambiado durante obra NBT");
            }
            // Authorised destructive work: discard non-player entities without death drops
            // or slime splitting. Players are never removed or built through.
            if (!clearEntities(job.world, job.workArea)) {
                job.waitingForEntities = true;
                return;
            }
            job.waitingForEntities = false;
            int applied = 0;
            while (job.remaining.hasNext() && applied++ < BLOCKS_PER_TICK) {
                var entry = job.remaining.next();
                BlockPos at = BlockPos.of(entry.getKey());
                if (!job.world.hasChunk(at.getX() >> 4, at.getZ() >> 4))
                    throw new IllegalStateException("Chunk descargado durante la obra en " + at);
                if (job.externalClearance != null && !job.world.getWorldBorder().isWithinBounds(at))
                    throw new IllegalStateException("Worldborder excluyo bloque NBT " + at);
                // Replace all states in the authorised volume, including physics changes.
                var actual = WorldData.encode(job.world.getBlockState(at));
                var desired = WorldData.encode(entry.getValue());
                if(!actual.equals(desired) || entry.getValue().hasBlockEntity()) {
                    job.world.removeBlockEntity(at);
                    if(actual.equals(desired)) job.world.setBlock(at,Blocks.AIR.defaultBlockState(),WRITE_FLAGS);
                    // Clear container data even if the target has the same block state.
                    job.world.removeBlockEntity(at); // inventory data is intentionally discarded
                    job.world.setBlock(at, entry.getValue(), WRITE_FLAGS);
                    if(!WorldData.encode(job.world.getBlockState(at)).equals(desired))
                        throw new IllegalStateException("No se pudo colocar bloque en " + at);
                }
                job.placed++;
            }
            if(job.remaining.hasNext()) return;
            int spawned = 0;
            if(isVillage(job.plan.kind())) {
                int index=0;
                for(int[] at:new int[][]{{-12,-12},{14,-12},{-12,14},{14,14}}) {
                    Villager villager = EntityType.VILLAGER.create(job.world);
                    UUID reserved = job.reservedEntities.get(index++);
                    if(villager == null)
                        throw new IllegalStateException("No se pudo crear el aldeano " + index);
                    // UUIDs were persisted before the first block was modified.
                    villager.setUUID(reserved);
                    villager.moveTo(job.plan.center().getX()+at[0]+.5,job.plan.y()+1,job.plan.center().getZ()+at[1]+.5,0,0);
                    if(!job.world.addFreshEntity(villager))
                        throw new IllegalStateException("No se pudo incorporar el aldeano " + index);
                    spawned++;
                }
            }
            if(isVillage(job.plan.kind()) && spawned != job.reservedEntities.size())
                throw new IllegalStateException("Aldeanos incompletos; conservar respaldo");
            ArrayList<WorldData.StoredBlock> finalBlocks = new ArrayList<>(job.originals.size());
            for(WorldData.StoredBlock b:job.originals) {
                finalBlocks.add(new WorldData.StoredBlock(b.pos(), b.before(),
                    WorldData.encode(job.world.getBlockState(BlockPos.of(b.pos())))));
            }
            // Mark complete only AFTER all blocks and villagers were placed.
            WorldData.get(job.world).setUndo(new WorldData.Undo(job.plan.dimension(),job.plan.kind(),finalBlocks,job.reservedEntities,true));
            job.world.getServer().overworld().getDataStorage().save();
            var operator = job.world.getServer().getPlayerList().getPlayer(job.operator);
            if(operator != null) operator.sendSystemMessage(Component.literal("[BWE] Obra terminada: " + job.plan.kind()
                 + " (" + job.total + " bloques). /bwe deshacer o /bwe finalizar SI"));
            running = null;
        } catch(Exception error) {
            LOGGER.error("[BWE] Fallo durante construccion de {} tras {}/{} bloques; snapshot conservado",
                job.plan.kind(), job.placed, job.total, error);
            var operator = job.world.getServer().getPlayerList().getPlayer(job.operator);
            if(operator != null) operator.sendSystemMessage(Component.literal("[BWE] ERROR tras "
                + job.placed + "/" + job.total + " bloques: " + error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage())
                + ". Respaldo conservado. NO finalizar."));
            running = null;
        }
    }
    /** Undo is deliberately incremental. No mass block updates in a single tick. */
    private static void undoTick() {
        UndoJob job = undoRunning;
        if(job == null) return;
        try {
            // Players pause the operation; all other occupants are discarded.
            if (!clearEntities(job.world, job.footprint)) { job.waiting=true; return; }
            job.waiting=false;
            if (++job.ticks % 20 == 0)
                announce(job.world, "Restaurando " + job.name + ": "
                    + (job.blocks.size()-1-job.cursor) + "/" + job.blocks.size());
            int examined = 0;
            while(job.cursor >= 0 && examined < BLOCKS_PER_TICK) {
                examined++;
                WorldData.StoredBlock b = job.blocks.get(job.cursor);
                BlockPos at = BlockPos.of(b.pos());
                if(!job.world.hasChunk(at.getX() >> 4, at.getZ() >> 4))
                    throw new IllegalStateException("Chunk descargado durante rollback: " + at);
                var current = WorldData.encode(job.world.getBlockState(at));
                if(current.equals(b.before())) { // Skip states already restored.
                    job.cursor--;
                    continue;
                }
                // Deshacer intentionally overwrites subsequent edits in the saved volume.
                job.world.removeBlockEntity(at);
                BlockState original = WorldData.decode(b.before());
                job.world.setBlock(at, original, WRITE_FLAGS);
                if(!job.world.getBlockState(at).equals(original))
                    throw new IllegalStateException("No se pudo restaurar " + at);
                job.cursor--;
            }
            if(job.cursor >= 0) return;
            for(UUID id : job.spawned) {
                var entity = job.world.getEntity(id);
                if(entity != null) entity.discard();
            }
            announce(job.world,"Restauracion completa: " + job.name + " (" + job.blocks.size() + " bloques). Ya puedes crear otra obra.");
            WorldData.get(job.world).clearUndo();
            job.world.getServer().overworld().getDataStorage().save();
            System.out.println("[BWE] Restauracion completa de " + job.name + " (" + job.blocks.size() + " registros)");
            undoRunning = null;
        } catch(Exception e) {
            LOGGER.error("[BWE] Restauracion incompleta; backup conservado", e);
            announce(job.world,"Restauracion detenida: " + e.getMessage() + ". Respaldo conservado; usa /bwe deshacer para reintentar.");
            undoRunning = null;
        }
    }
    private BuildEngine() {}

    public record Plan(String kind, String template, BlockPos center, String dimension,
                       int y, int halfX, int halfZ, long created, int margin) {
        public Plan(String kind, String template, BlockPos center, String dimension,
                    int y, int halfX, int halfZ, long created) {
            this(kind, template, center, dimension, y, halfX, halfZ, created, 7);
        }
    }
    public record Result(boolean ok, String message, int count) {}

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

    private static void announce(ServerLevel world, String text) {
        for (var player:world.players())
            if(player.hasPermissions(2)) player.sendSystemMessage(Component.literal("[BWE] " + text));
    }

    /** Returns false while players occupy the volume or a bounded purge is incomplete. */
    private static boolean clearEntities(ServerLevel world, AABB area) {
        if(area==null) return true;
        var entities=world.getEntitiesOfClass(Entity.class,area);
        // Check ALL players before deleting anything. Never kill or discard a player.
        if(entities.stream().anyMatch(e -> e instanceof net.minecraft.world.entity.player.Player)) return false;
        int removed=0;
        for(Entity entity:entities) {
            if(entity instanceof net.minecraft.world.entity.player.Player) continue;
            if(removed++ >= 256) return false;
            entity.discard();
        }
        return true;
    }

    private static boolean isVillage(String kind) {
        return kind.equals("aldea") || kind.equals("aldea_grande");
    }

    public static int radius(String kind) {
        return switch (kind) { case "aldea" -> 22; case "aldea_grande" -> 30; case "mercado", "fortin" -> 14; case "posada", "granja", "establo" -> 12; case "herreria" -> 10; case "campamento" -> 9; case "ruina" -> 10; case "refugio" -> 7; case "altar" -> 7; case "pozo" -> 6; case "carreta" -> 10; default -> 8; };
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
            // The shared preparation clears the entire selected area and margin first.
            // Local drawings only choose their final surface and interior air.
            for (int x=x0; x<=x1; x++) for (int z=z0; z<=z1; z++) {
                for (int y=0;y<=headroom;y++) put(x,y,z,Blocks.AIR.defaultBlockState());
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
            for(int dy=0;dy<=4;dy++) {
                for(int a=0;a<8;a++) {
                    put(x+a,dy,z,(a==0 || a==7) ? palette.log : palette.wall);
                    put(x+a,dy,z+7,(a==0 || a==7) ? palette.log : palette.wall);
                    put(x,dy,z+a,(a==0 || a==7) ? palette.log : palette.wall);
                    put(x+7,dy,z+a,(a==0 || a==7) ? palette.log : palette.wall);
                }
            }
            for(int dy=1;dy<=2;dy++) put(x+3,dy,z+7,Blocks.AIR.defaultBlockState());
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
                case "aldea", "aldea_grande" -> {
                    basicPath();
                    house(-17,-17); house(10,-17); house(-17,10); house(10,10);
                    farm(12,-3);
                    if(kind.equals("aldea_grande")) {
                        house(-28,-5); house(21,-5); house(-5,-28); house(-5,21);
                        corridor(-28,-1,28,1,palette.path);
                        corridor(-1,-28,1,28,palette.path);
                    }
                    lamp(7,8); lamp(-8,7);
                }
                case "herreria" -> {
                    house(-4,-4);
                    rect(-7,-5,-5,5,0,Blocks.STONE_BRICKS.defaultBlockState());
                    put(-6,1,-2,Blocks.ANVIL.defaultBlockState());
                    put(-6,1,1,Blocks.SMITHING_TABLE.defaultBlockState());
                    column(-6,4,1,7,Blocks.BRICKS.defaultBlockState());
                    put(-6,8,4,Blocks.CAMPFIRE.defaultBlockState());
                }
                case "posada" -> {
                    house(-9,-4); house(1,-4);
                    corridor(-2,-6,2,7,palette.path);
                    rect(-10,-5,9,5,7,palette.roofSlab);
                    for(int x=-7;x<=7;x+=7) {
                        put(x,0,7,Blocks.OAK_FENCE.defaultBlockState());
                        put(x,1,7,Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
                        put(x,0,9,Blocks.OAK_STAIRS.defaultBlockState());
                    }
                    lamp(-10,9); lamp(10,9);
                }
                case "granja" -> {
                    farm(-9,-9); farm(3,-9); farm(-9,2); farm(3,2);
                    corridor(-1,-10,1,10,palette.path);
                    for(int x=-10;x<=10;x++) {put(x,0,-10,Blocks.OAK_FENCE.defaultBlockState());put(x,0,10,Blocks.OAK_FENCE.defaultBlockState());}
                    for(int z=-10;z<=10;z++) {put(-10,0,z,Blocks.OAK_FENCE.defaultBlockState());put(10,0,z,Blocks.OAK_FENCE.defaultBlockState());}
                }
                case "establo" -> {
                    rect(-9,-7,9,7,0,palette.wall);
                    for(int x=-9;x<=9;x+=6) for(int z=-7;z<=7;z+=14) column(x,z,1,4,palette.log);
                    rect(-10,-8,10,8,5,palette.roof);
                    for(int x=-9;x<=9;x++) {put(x,1,-7,Blocks.OAK_FENCE.defaultBlockState());put(x,1,7,Blocks.OAK_FENCE.defaultBlockState());}
                    for(int z=-7;z<=7;z++) {put(-9,1,z,Blocks.OAK_FENCE.defaultBlockState());put(9,1,z,Blocks.OAK_FENCE.defaultBlockState());}
                    for(int x=-7;x<=7;x+=7) {put(x,1,-5,Blocks.HAY_BLOCK.defaultBlockState());put(x,1,5,Blocks.HAY_BLOCK.defaultBlockState());}
                    put(0,1,7,Blocks.OAK_FENCE_GATE.defaultBlockState());
                }
                case "fortin" -> {
                    rect(-11,-11,11,11,0,Blocks.STONE_BRICKS.defaultBlockState());
                    for(int y=1;y<=5;y++) for(int a=-11;a<=11;a++) {
                        put(a,y,-11,Blocks.STONE_BRICKS.defaultBlockState());put(a,y,11,Blocks.STONE_BRICKS.defaultBlockState());
                        put(-11,y,a,Blocks.STONE_BRICKS.defaultBlockState());put(11,y,a,Blocks.STONE_BRICKS.defaultBlockState());
                    }
                    for(int a=-11;a<=11;a+=2) for(int v:new int[]{-11,11}) {put(a,6,v,Blocks.STONE_BRICKS.defaultBlockState());put(v,6,a,Blocks.STONE_BRICKS.defaultBlockState());}
                    for(int x=-1;x<=1;x++) for(int y=1;y<=3;y++) put(x,y,11,Blocks.AIR.defaultBlockState());
                    house(-4,-5); lamp(-8,-8); lamp(8,8);
                }
                case "mercado" -> {
                    corridor(-12,-12,12,12,Blocks.COBBLESTONE.defaultBlockState());
                    int i=0;
                    for(int x:new int[]{-9,3}) for(int z:new int[]{-9,3}) {
                        for(int xx=x;xx<=x+5;xx+=5) for(int zz=z;zz<=z+5;zz+=5) column(xx,zz,0,3,Blocks.OAK_FENCE.defaultBlockState());
                        rect(x-1,z-1,x+6,z+6,4,(i++%2==0?Blocks.RED_WOOL:Blocks.YELLOW_WOOL).defaultBlockState());
                        rect(x,z+1,x+5,z+1,0,Blocks.SPRUCE_PLANKS.defaultBlockState());
                        put(x+1,1,z+1,Blocks.MELON.defaultBlockState());put(x+4,1,z+1,Blocks.HAY_BLOCK.defaultBlockState());
                    }
                    lamp(0,0);
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
        // External pieces have their own destructive, bounded and protection-aware
        // preparation. They do NOT use the natural-only vanilla survey above.
        if(!p.template().isEmpty()) return buildExternal(world,p,operator);
        TemplatePieceDraft.Prepared draft;
        try { draft=prepareOriginal(world,p); }
        catch(IllegalArgumentException error) { return new Result(false,error.getMessage(),0); }
        Map<Long,BlockState> changed=draft.changed();
        if(changed.isEmpty()) return new Result(false,"Sin cambios para construir",0);
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
        if(isVillage(p.kind())) for(int i=0;i<4;i++) reservedEntities.add(UUID.randomUUID());
        data.setUndo(new WorldData.Undo(p.dimension(),p.kind(),undoBlocks,reservedEntities));
        // Save undo to disk before applying any edits. The job then writes 200 blocks per tick.
        world.getServer().overworld().getDataStorage().save();
        running = new Job(world,p,operator,changed,undoBlocks,reservedEntities,draft.volume());
        return new Result(true,"Obra iniciada: limpieza total de bloques y entidades (excepto jugadores), cimentacion y construccion. /bwe estado muestra el avance.",changed.size());
    }

    private static TemplatePieceDraft.Prepared prepareOriginal(ServerLevel world, Plan p) {
        Drawing drawing=new Drawing(world,p.center(),p.y());
        drawing.draw(p.kind());
        int height=drawing.placements.keySet().stream().mapToInt(v -> BlockPos.of(v).getY()-p.y()+1).max().orElse(14);
        return TemplatePieceDraft.prepareDrawing(world,p,drawing.placements,height);
    }

    /** EXPERIMENTAL: first single-piece CTOV/Explorify placement.
     *  Operates ONLY after the admin explicitly uses /bwe crear or /bwe confirmar SI.
     *  All final changes are materialized/read before snapshotting and no block is
     *  edited until the original state snapshot has been saved to server storage.
     *  CTOV/Explorify structure files are read from installed resources, never bundled.
     *  Inventory contents and entities are not part of the rollback contract.
     */
    private static Result buildExternal(ServerLevel world, Plan p, UUID operator) {
        try {
            TemplatePieceDraft.Prepared draft=TemplatePieceDraft.prepare(world,p);
            Map<Long,BlockState> changed=draft.changed();
            if(changed.isEmpty()) return new Result(false,"No hay cambios; respaldo vacio rechazado.",0);
            if(changed.size()>MAX_CHANGES)
                return new Result(false,"Pieza NBT supera limite de cambios: "+changed.size(),0);
            ArrayList<WorldData.StoredBlock> originals=new ArrayList<>(changed.size());
            for(var entry:changed.entrySet()) {
                BlockPos at=BlockPos.of(entry.getKey());
                if(!world.hasChunk(at.getX()>>4,at.getZ()>>4))
                    throw new IllegalArgumentException("Chunk descargado antes de respaldar "+at);
                originals.add(new WorldData.StoredBlock(entry.getKey(),
                    WorldData.encode(world.getBlockState(at)),WorldData.encode(entry.getValue())));
            }
            WorldData data=WorldData.get(world);
            // Persist BEFORE the first edit; remains after a failed job for undo.
            data.setUndo(new WorldData.Undo(p.dimension(),p.template(),originals,List.of()));
            world.getServer().overworld().getDataStorage().save();
            running=new Job(world,p,operator,changed,originals,List.of(),draft.volume());
            return new Result(true,"Colocacion NBT experimental iniciada por lotes (200 bloques/tick): "
                +p.template()+". NO se recuperan NBT de inventarios ni entidades. "
                +"Consulta /bwe estado y luego /bwe deshacer ANTES de finalizar.",changed.size());
        } catch(RuntimeException error) {
            LOGGER.error("[BWE] Preparacion NBT cancelada SIN modificar terreno: {}",p.template(),error);
            return new Result(false,"No se inicio la obra NBT: "+error.getMessage(),0);
        }
    }

    /** Read-only diagnosis for a pending undo. Does not load chunks or mutate blocks. */
    public static Result diagnose(ServerLevel world) {
        WorldData.Undo snapshot = WorldData.get(world).undo();
        if(snapshot == null) return new Result(false,"No hay respaldo pendiente.",0);
        if(!snapshot.dimension().equals(world.dimension().location().toString()))
            return new Result(false,"El respaldo pertenece a " + snapshot.dimension()
                + "; entra en esa dimension para diagnosticar.",0);
        int original=0, target=0, conflict=0, unloaded=0;
        String first="";
        for(WorldData.StoredBlock b : snapshot.blocks()) {
            BlockPos at=BlockPos.of(b.pos());
            if(!world.hasChunk(at.getX() >> 4,at.getZ() >> 4)) { unloaded++; continue; }
            var current=WorldData.encode(world.getBlockState(at));
            if(current.equals(b.before())) original++;
            else if(!b.after().isEmpty() && current.equals(b.after())) target++;
            else {
                conflict++;
                if(first.isEmpty()) first=" | primer conflicto " + at
                    + " | original=" + b.before() + " | destino=" + b.after() + " | actual=" + current;
            }
        }
        return new Result(true,"Diagnostico SOLO LECTURA: " + snapshot.name()
            + " | " + (snapshot.completed()?"COMPLETO":"INCOMPLETO")
            + " | original=" + original + " | destino=" + target
            + " | discrepancias=" + conflict + " | chunks sin cargar=" + unloaded
            + first, snapshot.blocks().size());
    }

    public static Result undo(ServerLevel world) {
        if(undoRunning!=null) return new Result(false,"Ya se esta restaurando: "+status(),0);
        if(running!=null && running.world!=world) return new Result(false,"La obra esta en otra dimension",0);
        WorldData data=WorldData.get(world);
        WorldData.Undo snapshot=data.undo();
        if(snapshot==null) return new Result(false,"No hay construccion pendiente",0);
        if(snapshot.blocks().isEmpty())
            return new Result(false,"Respaldo vacio o corrupto: no se puede deshacer automaticamente",0);
        if(!snapshot.dimension().equals(world.dimension().location().toString()))
            return new Result(false,"Entra en la misma dimension de la construccion",0);
        // Validate ALL backed-up state encodings before restoring the authorised volume.
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
        }
        UndoJob job = new UndoJob(world, snapshot);
        running = null; // Stop a partial/paused build before restoring its complete snapshot.
        undoRunning = job;
        return new Result(true,"Restauracion TOTAL iniciada: sobrescribe cambios del area respaldada y elimina entidades salvo jugadores. Alejate y consulta /bwe estado",snapshot.blocks().size());
    }

    /** Finalize only a wholly finished build; never discard an incomplete crash snapshot. */
    public static Result canFinalize(ServerLevel world) {
        if(busy()) return new Result(false,"Hay una obra o restauracion en curso",0);
        WorldData.Undo snapshot=WorldData.get(world).undo();
        if(snapshot==null) return new Result(false,"No hay obra pendiente",0);
        if(!snapshot.completed())
            return new Result(false,"Obra incompleta o respaldo anterior sin marca de finalizacion. Usa /bwe deshacer en una copia; no se descarta el respaldo.",0);
        if(snapshot.blocks().isEmpty())
            return new Result(false,"Respaldo vacio o corrupto; no se puede finalizar",0);
        if(!snapshot.dimension().equals(world.dimension().location().toString()))
            return new Result(false,"Debes estar en la dimension original",0);
        // Explicit acceptance: later physics/player edits must not trap the undo slot.
        return new Result(true,"Obra completa. Respaldo listo para finalizar.",snapshot.blocks().size());
    }

    /** Non-destructive, operator-facing check before any block is edited. */
    public static Result simulate(ServerLevel world, Plan p) {
        if(busy()) return new Result(false,"Construccion o restauracion en curso",0);
        if(WorldData.get(world).undo()!=null)
            return new Result(false,"Existe una obra pendiente. Usa /bwe deshacer o /bwe finalizar SI",0);
        if(!p.template().isEmpty()) {
            try {
                TemplatePieceDraft.Prepared draft=TemplatePieceDraft.prepare(world,p);
                return new Result(true,
                    "SIMULACION NBT SIN CAMBIOS: "+p.template()
                    +" | despejar="+draft.clearing()
                    +" | cimentacion y tierra="+draft.foundation()
                    +" | otros bloques="+draft.solid()
                    +" | total="+draft.changed().size()
                    +" | margen="+p.margin()
                    +" | sin recuperar inventarios ni entidades"
                    +" | NBT especial="+draft.ignoredBlockEntities()
                    +" | entidades NBT="+draft.ignoredEntities()
                    +" | paletas="+draft.alternativePalettes()
                    +". PREPARADO PARA PRUEBA EN COPIA: /bwe crear o /bwe confirmar SI.", draft.changed().size());
            } catch(IllegalArgumentException error) {
                return new Result(false,"SIMULACION NBT CANCELADA: "+error.getMessage(),0);
            }
        }
        try {
            var draft=prepareOriginal(world,p);
            int changed=draft.changed().size();
            if(changed==0) return new Result(false,"Sin cambios",0);
            return new Result(true,"SIMULACION SIN CAMBIOS: " + p.kind() + " | " + changed
                + " posiciones respaldadas | limpieza TOTAL, margen=" + p.margin()
                + ". Se eliminaran entidades excepto jugadores. /bwe crear",changed);
        } catch(IllegalArgumentException error) {
            return new Result(false,"No se puede construir: " + error.getMessage(),0);
        }
    }
}
