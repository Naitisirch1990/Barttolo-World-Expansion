package net.barttolo.bwe;

import java.util.*;

/** Compact village envelope: fills courtyards between buildings, never the outer bounding rectangle. */
public final class VillageFootprint {
    private record Point(int x,int z) {}
    public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static long cross(Point a,Point b,Point c){return ((long)b.x-a.x)*((long)c.z-a.z)-((long)b.z-a.z)*((long)c.x-a.x);}
    public static Set<Long> withCourtyards(Set<Long> columns){
        if(columns.size()<3)return new HashSet<>(columns);
        ArrayList<Point> points=new ArrayList<>(columns.size());
        for(long k:columns)points.add(new Point((int)(k>>32),(int)k));
        points.sort(Comparator.comparingInt(Point::x).thenComparingInt(Point::z));
        ArrayList<Point> hull=new ArrayList<>();
        for(Point p:points){while(hull.size()>1&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
        int lower=hull.size();
        for(int i=points.size()-2;i>=0;i--){Point p=points.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
        hull.remove(hull.size()-1);
        if(hull.size()<3)return new HashSet<>(columns);
        int x0=points.get(0).x,x1=points.get(points.size()-1).x,z0=points.stream().mapToInt(Point::z).min().orElseThrow(),z1=points.stream().mapToInt(Point::z).max().orElseThrow();
        HashSet<Long> result=new HashSet<>(columns);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            Point p=new Point(x,z);boolean inside=true;
            for(int i=0;i<hull.size();i++)if(cross(hull.get(i),hull.get((i+1)%hull.size()),p)<0){inside=false;break;}
            if(inside)result.add(key(x,z));
        }
        return result;
    }
}
