package net.barttolo.bwe;

import java.util.BitSet;

/** Flood only spaces connected to the outside below the waterline, keeping sealed hulls dry. */
final class WaterEnvelope {
    static BitSet exterior(int width,int height,int depth,BitSet blocked) {
        int volume=Math.multiplyExact(Math.multiplyExact(width,height),depth);
        BitSet water=new BitSet(volume);int[] queue=new int[volume];int tail=0;
        for(int y=0;y<height;y++)for(int z=0;z<depth;z++)for(int x=0;x<width;x++) {
            if(x!=0&&x!=width-1&&z!=0&&z!=depth-1&&y!=0)continue;
            int i=(y*depth+z)*width+x;
            if(!blocked.get(i)&&!water.get(i)){water.set(i);queue[tail++]=i;}
        }
        for(int head=0;head<tail;head++) {
            int i=queue[head],x=i%width,z=(i/width)%depth,y=i/(width*depth);
            int[] neighbours={x>0?i-1:-1,x+1<width?i+1:-1,z>0?i-width:-1,z+1<depth?i+width:-1,y>0?i-width*depth:-1,y+1<height?i+width*depth:-1};
            for(int next:neighbours)if(next>=0&&!blocked.get(next)&&!water.get(next)){water.set(next);queue[tail++]=next;}
        }
        return water;
    }
}
