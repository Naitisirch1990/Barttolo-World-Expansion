package net.barttolo.bwe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-to-client catalog preview metadata. */
public final class BweNetwork {
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(BarttoloWorldExpansion.MOD_ID, "main"),
        () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private BweNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, Preview.class, Preview::encode, Preview::decode,
            (message, context) -> {
                context.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> BwePreviewClient.open(message.id, message.title, message.description,
                        message.sizeX, message.sizeZ)));
                context.get().setPacketHandled(true);
            });
        CHANNEL.registerMessage(1, Border.class, Border::encode, Border::decode,
            (message, context) -> {
                context.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> BweBorderClient.accept(message)));
                context.get().setPacketHandled(true);
            },java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void openPreview(ServerPlayer player, BweCatalog.Entry entry) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new Preview(entry.id(), entry.title(), entry.description(), entry.sizeX(), entry.sizeZ()));
    }

    public static void showBorder(ServerPlayer player,int x0,int z0,int x1,int z1,int ground,java.util.List<VillageFootprint.Edge> edges) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new Border(player.serverLevel().dimension().location().toString(),true,x0,z0,x1,z1,ground,edges));
    }
    public static void clearBorder(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new Border("",false,0,0,0,0,0));
    }
    public record Border(String dimension,boolean visible,int x0,int z0,int x1,int z1,int ground,java.util.List<VillageFootprint.Edge> edges) {
        public Border{edges=java.util.List.copyOf(edges);if(edges.size()>8192)throw new IllegalArgumentException("Contorno demasiado complejo");}
        public Border(String d,boolean v,int x0,int z0,int x1,int z1,int y){this(d,v,x0,z0,x1,z1,y,java.util.List.of(new VillageFootprint.Edge(x0,z0,x1,z0),new VillageFootprint.Edge(x1,z0,x1,z1),new VillageFootprint.Edge(x1,z1,x0,z1),new VillageFootprint.Edge(x0,z1,x0,z0)));}
        void encode(FriendlyByteBuf b){b.writeUtf(dimension,256);b.writeBoolean(visible);b.writeInt(x0);b.writeInt(z0);b.writeInt(x1);b.writeInt(z1);b.writeInt(ground);b.writeVarInt(edges.size());for(var e:edges){b.writeInt(e.x0());b.writeInt(e.z0());b.writeInt(e.x1());b.writeInt(e.z1());}}
        static Border decode(FriendlyByteBuf b){String d=b.readUtf(256);boolean v=b.readBoolean();int x0=b.readInt(),z0=b.readInt(),x1=b.readInt(),z1=b.readInt(),y=b.readInt(),n=b.readVarInt();if(n<0||n>8192)throw new IllegalArgumentException("Contorno invalido");var edges=new java.util.ArrayList<VillageFootprint.Edge>();for(int i=0;i<n;i++)edges.add(new VillageFootprint.Edge(b.readInt(),b.readInt(),b.readInt(),b.readInt()));return new Border(d,v,x0,z0,x1,z1,y,edges);}
    }
    private record Preview(String id, String title, String description, int sizeX, int sizeZ) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 256);buffer.writeUtf(title, 256);buffer.writeUtf(description, 512);
            buffer.writeVarInt(sizeX);buffer.writeVarInt(sizeZ);
        }
        private static Preview decode(FriendlyByteBuf buffer) {
            return new Preview(buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(512),
                buffer.readVarInt(), buffer.readVarInt());
        }
    }
}

