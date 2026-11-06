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
            LevelRenderer.renderLineBox(pose,lines,new AABB(b.x0(),y,b.z0(),b.x1(),y+16,b.z1()),0.12f,1f,0.3f,1f);
            for(int dy=4;dy<16;dy+=4)LevelRenderer.renderLineBox(pose,lines,new AABB(b.x0(),y+dy,b.z0(),b.x1(),y+dy,b.z1()),0.12f,1f,0.3f,1f);
            for(int x:new int[]{b.x0(),b.x1()})for(int z:new int[]{b.z0(),b.z1()})
                LevelRenderer.renderLineBox(pose,lines,new AABB(x-0.08,y,z-0.08,x+0.08,y+32,z+0.08),0.4f,1f,0.6f,1f);
            buffers.endBatch(RenderType.lines());
        }finally{pose.popPose();}
    }
}
