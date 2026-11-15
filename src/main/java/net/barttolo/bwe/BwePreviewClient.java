package net.barttolo.bwe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/** Small in-game top-down plan viewer for one catalog entry. */
@OnlyIn(Dist.CLIENT)
public final class BwePreviewClient {
    private BwePreviewClient() {}

    public static void open(String id, String title, String description, int sizeX, int sizeZ) {
        Minecraft.getInstance().setScreen(new PreviewScreen(id, title, description, sizeX, sizeZ));
    }

    private static final class PreviewScreen extends Screen {
        private final String id, title, description;
        private final int sizeX, sizeZ;
        private final ResourceLocation image;
        private int left, top;

        private PreviewScreen(String id, String title, String description, int sizeX, int sizeZ) {
            super(Component.literal(title));
            this.id=id;this.title=title;this.description=description;this.sizeX=sizeX;this.sizeZ=sizeZ;
            String file=id.toLowerCase(Locale.ROOT).replace(':','_').replace('/','_').replaceAll("[^a-z0-9_.-]","_");
            image=new ResourceLocation(BarttoloWorldExpansion.MOD_ID,"textures/gui/previews/"+file+".png");
        }

        @Override protected void init() {
            left=(width-340)/2;top=(height-290)/2;
            addRenderableWidget(Button.builder(Component.literal("Elegir en esta ubicación"),b->{
                if(minecraft!=null&&minecraft.player!=null&&minecraft.player.connection!=null){
                    minecraft.player.connection.sendCommand("bwe seleccionar "+id);
                }
                onClose();
            }).bounds(left+28,top+258,190,20).build());
            addRenderableWidget(Button.builder(Component.literal("Cerrar"),b->onClose())
                .bounds(left+236,top+258,76,20).build());
        }

        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            renderBackground(graphics);
            graphics.fill(left,top,left+340,top+290,0xE510171C);
            graphics.fill(left+1,top+1,left+339,top+26,0xFF25333D);
            graphics.drawCenteredString(font,title,width/2,top+8,0xFFFFFFFF);
            graphics.drawCenteredString(font,Component.literal(sizeX+" x "+sizeZ+" bloques"),width/2,top+34,0xFF60EAF2);
            graphics.drawCenteredString(font,Component.literal("Esquema superior de la construcción"),width/2,top+48,0xFFDDDDDD);
            int boxX=left+42,boxY=top+68,boxW=256,boxH=174;
            graphics.fill(boxX-2,boxY-2,boxX+boxW+2,boxY+boxH+2,0xFF34414A);
            graphics.fill(boxX,boxY,boxX+boxW,boxY+boxH,0xFF172027);
            graphics.blit(image,boxX,boxY,0,0,256,174,256,174);
            graphics.drawCenteredString(font,Component.literal(description),width/2,top+246,0xFFB8C6CC);
            super.render(graphics,mouseX,mouseY,partialTick);
        }

        @Override public boolean isPauseScreen(){return false;}
    }
}
