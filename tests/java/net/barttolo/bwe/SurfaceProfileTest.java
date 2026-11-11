package net.barttolo.bwe;

/** Deterministic Java 17 tests: no Minecraft dependencies. */
public final class SurfaceProfileTest {
    private static int checks;
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        SurfaceProfile p=new SurfaceProfile(-64,320);
        for(int h:new int[]{63,64,64,65,100}) p.add(h);
        check(p.count()==5,"count");
        check(p.low()==63,"minimum");
        check(p.high()==100,"maximum");
        check(p.median()==64,"median ignores outlier");
        check(p.cut(64)==37,"cut estimate");
        check(p.fill(64)==1,"fill estimate");
        check(p.cut(100)==0,"no cuts above max");
        check(p.cut(63)==41 && p.fill(63)==0,"all columns cut to low");
        SurfaceProfile even=new SurfaceProfile(-64,320);
        even.add(60); even.add(61); even.add(80); even.add(90);
        check(even.median()==61,"lower median for even count");
        check(even.cut(61)==48,"even cut");
        check(even.fill(61)==1,"even fill");
        SurfaceProfile negative=new SurfaceProfile(-64,320);
        negative.add(-64);negative.add(-62);negative.add(-60);
        check(negative.median()==-62,"negative terrain heights");
        check(negative.cut(-62)==2,"negative cut");
        check(negative.fill(-62)==2,"negative fill");
        SurfaceProfile flat=new SurfaceProfile(0,255);
        for(int i=0;i<10000;i++) flat.add(70);
        check(flat.median()==70 && flat.cut(70)==0 && flat.fill(70)==0,"large flat site");
        try { flat.add(256); throw new AssertionError("out of bounds not rejected"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { new SurfaceProfile(300,200); throw new AssertionError("invalid range not rejected"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { new SurfaceProfile(-10000,10000); throw new AssertionError("huge range not rejected"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { new SurfaceProfile(0,10).median(); throw new AssertionError("empty profile not rejected"); }
        catch(IllegalStateException expected) { checks++; }
        System.out.println("OK: "+checks+" comprobaciones Java 17 de alturas, mediana y movimiento de tierras");
    }
}
