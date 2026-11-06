package net.barttolo.bwe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-to-client catalog preview metadata. */
public final class BweNetwork {
    private static final String PROTOCOL = "1";
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
    }

    public static void openPreview(ServerPlayer player, BweCatalog.Entry entry) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new Preview(entry.id(), entry.title(), entry.description(), entry.sizeX(), entry.sizeZ()));
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

