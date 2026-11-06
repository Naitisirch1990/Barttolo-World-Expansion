package net.barttolo.bwe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-to-client catalog preview metadata. */
public final class BweNetwork {
    private static final String PROTOCOL = "2";
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

    public static void showBorder(ServerPlayer player,int x0,int z0,int x1,int z1,int ground) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new Border(player.serverLevel().dimension().location().toString(),true,x0,z0,x1,z1,ground));
    }
    public static void clearBorder(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new Border("",false,0,0,0,0,0));
    }
    public record Border(String dimension,boolean visible,int x0,int z0,int x1,int z1,int ground) {
        void encode(FriendlyByteBuf b){b.writeUtf(dimension,256);b.writeBoolean(visible);b.writeInt(x0);b.writeInt(z0);b.writeInt(x1);b.writeInt(z1);b.writeInt(ground);}
        static Border decode(FriendlyByteBuf b){return new Border(b.readUtf(256),b.readBoolean(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt());}
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

