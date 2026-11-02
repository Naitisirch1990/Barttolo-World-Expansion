package net.barttolo.bwe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict, read-only decode of ONE NBT structure piece from already-loaded
 * datapack/mod resources. This never writes blocks, spawns entities or bundles
 * CTOV/Explorify assets; jigsaw assembly is a separate future operation.
 *
 * The first palette is used deterministically for inspection. Alternative
 * palettes, block-entity NBT and entities are counted, NOT installed.
 * NO returned data may be placed without a durable BEFORE snapshot, bounds
 * checks, proper block order and a server-thread tick-bounded world writer.
 */
public final class TemplateNbtPiece {
    public static final int MAX_VOLUME = 8_000_000;
    public static final int MAX_BLOCKS = 250_000;

    public record Cell(int x, int y, int z, BlockState state, boolean hasBlockEntityNbt) {}
    public record Blueprint(List<Cell> cells, long implicitAir, int explicitAir,
                            int jigsaws, int structureBlocks, int blockEntityNbt,
                            int entities, int paletteVariants) {
        public Blueprint {
            cells = List.copyOf(cells);
        }
    }

    private TemplateNbtPiece() {}

    public static Blueprint read(StructureTemplate template) {
        var dimensions = template.getSize();
        int width = dimensions.getX(), height = dimensions.getY(), depth = dimensions.getZ();
        if (width <= 0 || height <= 0 || depth <= 0)
            throw new IllegalArgumentException("Invalid template size");
        long volume = (long) width * height * depth;
        if (volume > MAX_VOLUME)
            throw new IllegalArgumentException("Template volume exceeds the per-piece inspection limit");
        CompoundTag data = template.save(new CompoundTag());
        if (!data.contains("blocks", Tag.TAG_LIST))
            throw new IllegalArgumentException("NBT has no blocks list");
        ListTag blocks = data.getList("blocks", Tag.TAG_COMPOUND);
        if (blocks.size() > MAX_BLOCKS)
            throw new IllegalArgumentException("Too many template blocks for a single piece");

        ListTag palette;
        int variants;
        if (data.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = data.getList("palettes", Tag.TAG_LIST);
            if (palettes.isEmpty() || !(palettes.get(0) instanceof ListTag))
                throw new IllegalArgumentException("Empty or malformed NBT palettes");
            palette = (ListTag) palettes.get(0);
            variants = palettes.size();
            for (int i=1; i<palettes.size(); i++) {
                if (!(palettes.get(i) instanceof ListTag alternative)
                    || alternative.size() != palette.size())
                    throw new IllegalArgumentException("Incompatible alternative NBT palette");
            }
        } else {
            if (!data.contains("palette", Tag.TAG_LIST))
                throw new IllegalArgumentException("NBT has no state palette");
            palette = data.getList("palette", Tag.TAG_COMPOUND);
            variants = 1;
        }
        if (palette.isEmpty() && !blocks.isEmpty())
            throw new IllegalArgumentException("NBT contains blocks but no palette states");

        ArrayList<BlockState> states = new ArrayList<>(palette.size());
        for (int i=0; i<palette.size(); i++) {
            if (!(palette.get(i) instanceof CompoundTag stateTag)
                || !stateTag.contains("Name", Tag.TAG_STRING))
                throw new IllegalArgumentException("Invalid palette state at " + i);
            ResourceLocation id = ResourceLocation.tryParse(stateTag.getString("Name"));
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
                throw new IllegalArgumentException("Unknown template block: " + stateTag.getString("Name"));
            if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag props = stateTag.getCompound("Properties");
                var definition = BuiltInRegistries.BLOCK.get(id).getStateDefinition();
                for (String name : props.getAllKeys()) {
                    var property = definition.getProperty(name);
                    if (property == null || !props.contains(name, Tag.TAG_STRING)
                        || property.getValue(props.getString(name)).isEmpty())
                        throw new IllegalArgumentException("Invalid property " + name + " for " + id);
                }
            }
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stateTag));
        }

        ArrayList<Cell> entries = new ArrayList<>(blocks.size());
        Set<Long> occupied = new HashSet<>();
        int explicitAir=0, jigsaws=0, structureBlocks=0, blockEntityNbt=0;
        for (int i=0; i<blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains("pos", Tag.TAG_LIST) || !block.contains("state", Tag.TAG_INT))
                throw new IllegalArgumentException("Missing block coordinates/palette index at " + i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3)
                throw new IllegalArgumentException("Invalid NBT block position at " + i);
            int x=pos.getInt(0),y=pos.getInt(1),z=pos.getInt(2);
            if (x<0 || x>=width || y<0 || y>=height || z<0 || z>=depth)
                throw new IllegalArgumentException("Block outside template size at " + i);
            long linear=((long)y*depth+z)*width+x;
            if (!occupied.add(linear))
                throw new IllegalArgumentException("Duplicate block position at " + i);
            int index=block.getInt("state");
            if (index<0 || index>=states.size())
                throw new IllegalArgumentException("Palette index outside bounds at " + i);
            BlockState state = states.get(index);
            if (state.isAir()) explicitAir++;
            if (state.is(Blocks.JIGSAW)) jigsaws++;
            if (state.is(Blocks.STRUCTURE_BLOCK)) structureBlocks++;
            boolean hasNbt=block.contains("nbt", Tag.TAG_COMPOUND);
            if (hasNbt) blockEntityNbt++;
            entries.add(new Cell(x,y,z,state,hasNbt));
        }
        int entities=data.getList("entities", Tag.TAG_COMPOUND).size();
        return new Blueprint(entries, volume-blocks.size(), explicitAir,
            jigsaws, structureBlocks, blockEntityNbt, entities, variants);
    }
}
