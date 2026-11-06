package net.barttolo.bwe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One virtual perimeter, rendered independently of chunks and particle settings. */
@Mod.EventBusSubscriber(modid=BarttoloWorldExpansion.MOD_ID,value=Dist.CLIENT)
public final class BweBorderClient {
    private static BweNetwork.Border current;
    private static long expires;
    private BweBorderClient() {}
    public static void accept(BweNetwork.Border border) {
        current=border.visible()?border:null;expires=System.nanoTime()+4_000_000_000L;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();var b=current;
        if(b==null||mc.level==null||System.nanoTime()>expires||!mc.level.dimension().location().toString().equals(b.dimension()))return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();
        try{
            pose.translate(-camera.x,-camera.y,-camera.z);
            var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
            double y=b.ground()+0.15;
            for(var edge:b.edges()){
                double x0=Math.min(edge.x0(),edge.x1()),x1=Math.max(edge.x0(),edge.x1()),z0=Math.min(edge.z0(),edge.z1()),z1=Math.max(edge.z0(),edge.z1());
                LevelRenderer.renderLineBox(pose,lines,new AABB(x0,y,z0,x1,y+16,z1),0.12f,1f,0.3f,1f);
                for(int dy=4;dy<16;dy+=4)LevelRenderer.renderLineBox(pose,lines,new AABB(x0,y+dy,z0,x1,y+dy,z1),0.12f,1f,0.3f,1f);
            }
            buffers.endBatch(RenderType.lines());
        }finally{pose.popPose();}
    }
}
