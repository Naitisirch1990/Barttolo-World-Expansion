package net.barttolo.bwe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Persistent private-build exclusions and a single reversible block snapshot. */
public final class WorldData extends SavedData {
    public record Area(String dimension, int x, int z, int radius) {
        boolean contains(String dim, int px, int pz, int margin) {
            long dx = (long)px-x, dz = (long)pz-z, r=(long)radius+margin;
            if(Math.abs(dx)>r || Math.abs(dz)>r) return false;
            return dimension.equals(dim) && dx*dx+dz*dz<=r*r;
        }
    }
    /** Additional manual exclusions; useful for long RingWorld mission paths. */
    public record RectArea(String dimension,int minX,int minZ,int maxX,int maxZ) {
        boolean intersects(String dim,int x0,int z0,int x1,int z1,int margin) {
            return dimension.equals(dim) && SiteMath.overlaps(x0,z0,x1,z1,minX,minZ,maxX,maxZ,margin);
        }
    }
    /** Completed works survive after the undo snapshot has been accepted. */
    public record Site(String dimension, String name, int minX, int minZ, int maxX, int maxZ) {
        boolean overlaps(String dim, int west, int north, int east, int south, int margin) {
            return dimension.equals(dim) && SiteMath.overlaps(
                west,north,east,south,minX,minZ,maxX,maxZ,margin);
        }
    }
    public record StoredBlock(long pos, CompoundTag before, CompoundTag after) {}
    /** Completion is persisted only after all blocks AND required villagers have been created.
     *  Old backups lacking this flag remain recoverable but cannot be finalized. */
    public record Undo(String dimension, String name, List<StoredBlock> blocks, List<UUID> entities,
                       boolean completed) {
        public Undo(String dimension, String name, List<StoredBlock> blocks, List<UUID> entities) {
            this(dimension, name, blocks, entities, false);
        }
    }

    private final ArrayList<Area> exclusions = new ArrayList<>();
    private final ArrayList<RectArea> rectangles = new ArrayList<>();
    private final ArrayList<Site> sites = new ArrayList<>();
    private Undo undo;

    public static WorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, "barttolo_world_expansion");
    }

    public static WorldData load(CompoundTag root) {
        WorldData data = new WorldData();
        ListTag exclusions = root.getList("Protected", Tag.TAG_COMPOUND);
        for (int i = 0; i < exclusions.size(); i++) {
            CompoundTag t = exclusions.getCompound(i);
            data.exclusions.add(new Area(t.getString("Dimension"), t.getInt("X"), t.getInt("Z"), t.getInt("Radius")));
        }
        ListTag protectedRects = root.getList("ProtectedRects",Tag.TAG_COMPOUND);
        for(int i=0;i<protectedRects.size();i++) {
            CompoundTag t=protectedRects.getCompound(i);
            int x0=t.getInt("MinX"),z0=t.getInt("MinZ"),x1=t.getInt("MaxX"),z1=t.getInt("MaxZ");
            if(x0<=x1 && z0<=z1) data.rectangles.add(new RectArea(t.getString("Dimension"),x0,z0,x1,z1));
        }
        ListTag sites = root.getList("CompletedSites", Tag.TAG_COMPOUND);
        for (int i = 0; i < sites.size(); i++) {
            CompoundTag t = sites.getCompound(i);
            data.sites.add(new Site(t.getString("Dimension"), t.getString("Name"),
                t.getInt("MinX"), t.getInt("MinZ"), t.getInt("MaxX"), t.getInt("MaxZ")));
        }
        if (root.contains("Undo", Tag.TAG_COMPOUND)) {
            CompoundTag u = root.getCompound("Undo");
            List<StoredBlock> blocks = new ArrayList<>();
            ListTag entries = u.getList("Blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag e = entries.getCompound(i);
                blocks.add(new StoredBlock(e.getLong("Pos"), e.getCompound("Before"), e.getCompound("After")));
            }
            List<UUID> entities = new ArrayList<>();
            ListTag uuids = u.getList("Entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < uuids.size(); i++) {
                CompoundTag e = uuids.getCompound(i);
                if (e.hasUUID("ID")) entities.add(e.getUUID("ID"));
            }
            // Missing/wrongly typed flag is treated as incomplete (fail closed).
            data.undo = new Undo(u.getString("Dimension"), u.getString("Name"), blocks, entities,
                u.contains("Completed", Tag.TAG_BYTE) && u.getBoolean("Completed"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag protectedList = new ListTag();
        for (Area a : exclusions) {
            CompoundTag t = new CompoundTag();
            t.putString("Dimension", a.dimension());
            t.putInt("X", a.x()); t.putInt("Z", a.z()); t.putInt("Radius", a.radius());
            protectedList.add(t);
        }
        root.put("Protected", protectedList);
        ListTag rectangleList=new ListTag();
        for(RectArea a:rectangles) {
            CompoundTag t=new CompoundTag();
            t.putString("Dimension",a.dimension());
            t.putInt("MinX",a.minX()); t.putInt("MinZ",a.minZ());
            t.putInt("MaxX",a.maxX()); t.putInt("MaxZ",a.maxZ());
            rectangleList.add(t);
        }
        root.put("ProtectedRects",rectangleList);
        ListTag completed = new ListTag();
        for (Site a : sites) {
            CompoundTag t = new CompoundTag();
            t.putString("Dimension", a.dimension()); t.putString("Name", a.name());
            t.putInt("MinX", a.minX()); t.putInt("MinZ", a.minZ());
            t.putInt("MaxX", a.maxX()); t.putInt("MaxZ", a.maxZ());
            completed.add(t);
        }
        root.put("CompletedSites", completed);
        if (undo != null) {
            CompoundTag u = new CompoundTag();
            u.putString("Dimension", undo.dimension());
            u.putString("Name", undo.name());
            u.putBoolean("Completed", undo.completed());
            ListTag blocks = new ListTag();
            for (StoredBlock b : undo.blocks()) {
                CompoundTag t = new CompoundTag();
                t.putLong("Pos", b.pos());
                t.put("Before", b.before()); t.put("After", b.after());
                blocks.add(t);
            }
            u.put("Blocks", blocks);
            ListTag uuids = new ListTag();
            for (UUID uuid : undo.entities()) {
                CompoundTag t = new CompoundTag(); t.putUUID("ID", uuid); uuids.add(t);
            }
            u.put("Entities", uuids);
            root.put("Undo", u);
        }
        return root;
    }

    public List<Area> exclusions() { return Collections.unmodifiableList(exclusions); }
    public List<RectArea> rectangles() { return Collections.unmodifiableList(rectangles); }
    public int protectRect(String dimension,int x0,int z0,int x1,int z1) {
        rectangles.add(new RectArea(dimension,Math.min(x0,x1),Math.min(z0,z1),Math.max(x0,x1),Math.max(z0,z1)));
        setDirty(); return rectangles.size();
    }
    public boolean unprotectRect(int number) {
        if(number<1 || number>rectangles.size()) return false;
        rectangles.remove(number-1); setDirty(); return true;
    }
    /** Check whole construction footprint, not just its centre. */
    public boolean protectedFootprint(String dim,int x0,int z0,int x1,int z1,int margin) {
        for(Area a:exclusions) if(a.dimension().equals(dim)
            && SiteMath.circleIntersectsRect(a.x(),a.z(),a.radius()+margin,x0,z0,x1,z1)) return true;
        for(RectArea a:rectangles) if(a.intersects(dim,x0,z0,x1,z1,margin)) return true;
        return false;
    }
    public List<Site> completedSites() { return Collections.unmodifiableList(sites); }
    /** The site is recorded only after explicit finalization of the completed build. */
    public void registerCompleted(Undo snapshot) {
        if (!snapshot.completed() || snapshot.blocks().isEmpty())
            throw new IllegalStateException("No se puede registrar una obra incompleta o sin bloques");
        int minX=Integer.MAX_VALUE, maxX=Integer.MIN_VALUE, minZ=Integer.MAX_VALUE, maxZ=Integer.MIN_VALUE;
        for (StoredBlock b : snapshot.blocks()) {
            BlockPos pos = BlockPos.of(b.pos());
            minX=Math.min(minX,pos.getX()); maxX=Math.max(maxX,pos.getX());
            minZ=Math.min(minZ,pos.getZ()); maxZ=Math.max(maxZ,pos.getZ());
        }
        sites.add(new Site(snapshot.dimension(), snapshot.name(), minX,minZ,maxX,maxZ));
        setDirty();
    }
    /** Only forget the index: does NOT delete any existing buildings or blocks. */
    public boolean forgetSite(int number) {
        if(number<1 || number>sites.size()) return false;
        sites.remove(number-1);
        setDirty();
        return true;
    }
    /** Reject both intersection and closely adjacent construction volumes. */
    public boolean overlapsCompleted(String dimension, int x0, int z0, int x1, int z1, int margin) {
        for (Site s : sites) if (s.overlaps(dimension,x0,z0,x1,z1,margin)) return true;
        return false;
    }
    public int protect(String dimension, BlockPos center, int radius) {
        exclusions.add(new Area(dimension, center.getX(), center.getZ(), radius));
        setDirty(); return exclusions.size();
    }
    public boolean unprotect(int number) {
        if (number < 1 || number > exclusions.size()) return false;
        exclusions.remove(number - 1); setDirty(); return true;
    }
    public boolean isProtected(String dimension, int x, int z, int margin) {
        for (Area a : exclusions) if (a.contains(dimension, x, z, margin)) return true;
        return false;
    }
    public Undo undo() { return undo; }
    public void setUndo(Undo snapshot) { undo = snapshot; setDirty(); }
    public void clearUndo() { undo = null; setDirty(); }

    public static CompoundTag encode(BlockState state) {
        CompoundTag t = new CompoundTag();
        t.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        CompoundTag props = new CompoundTag();
        state.getValues().forEach((property, value) -> props.putString(property.getName(), propertyValue(state, property)));
        t.put("Properties", props);
        return t;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** Never silently downgrade a backed-up block state to a default value.
     *  A changed/removed mod or an invalid property must stop rollback instead of
     *  restoring the wrong orientation, half, waterlogging, or other metadata. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String value) {
        Optional<?> parsed = property.getValue(value);
        if (parsed.isEmpty())
            throw new IllegalArgumentException("Invalid saved value '" + value + "' for property " + property.getName());
        return state.setValue(property, (Comparable) parsed.get());
    }

    public static BlockState decode(CompoundTag t) {
        // A truncated snapshot must never silently restore default block properties.
        // For example, losing "facing" or "half" would corrupt orientation/geometry.
        if (!t.contains("Name", Tag.TAG_STRING) || !t.contains("Properties", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Missing or invalid block-state fields in backup");
        ResourceLocation id = ResourceLocation.tryParse(t.getString("Name"));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
            throw new IllegalArgumentException("Unknown block in backup: " + t.getString("Name"));
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        CompoundTag props = t.getCompound("Properties");
        // Encode always writes every property. Require that exact set on decode;
        // omitted keys must not fall back to a potentially wrong default.
        if (props.getAllKeys().size() != block.getStateDefinition().getProperties().size())
            throw new IllegalArgumentException("Incomplete block properties for " + id);
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            String key = property.getName();
            if (!props.contains(key, Tag.TAG_STRING))
                throw new IllegalArgumentException("Missing/invalid property '" + key + "' for " + id);
            state = applyProperty(state, property, props.getString(key));
        }
        return state;
    }
}
