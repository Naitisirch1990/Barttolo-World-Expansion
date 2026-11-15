package net.barttolo.bwe;

/** Run with tests/run_geometry_tests.sh. Java 17+, no Minecraft required. */
public final class SiteMathTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(SiteMath.overlaps(0,0,10,10,10,10,20,20,0),"Shared corner");
        check(!SiteMath.overlaps(0,0,10,10,27,0,40,10,16),"Gap 17 should be valid");
        check(SiteMath.overlaps(0,0,10,10,26,0,40,10,16),"Margin 16 blocks");
        check(SiteMath.overlaps(-12,-15,12,15,-4,-4,4,4,0),"Contained site");
        check(!SiteMath.overlaps(-500,-50,-400,50,400,-50,500,50,16),"Remote regions");
        check(!SiteMath.overlaps(Integer.MIN_VALUE,0,Integer.MIN_VALUE+8,8,
            Integer.MAX_VALUE-8,0,Integer.MAX_VALUE,8,16),"No integer overflow");
        try { SiteMath.overlaps(3,0,2,1,0,0,1,1,16); throw new AssertionError("Invalid bounds not rejected"); }
        catch (IllegalArgumentException expected) { /* pass */ }
        check(SiteMath.circleIntersectsRect(5,5,0,0,0,10,10),"Circle point inside rectangle");
        check(SiteMath.circleIntersectsRect(13,5,3,0,0,10,10),"Circle touches rectangle edge");
        check(!SiteMath.circleIntersectsRect(14,5,3,0,0,10,10),"Circle does not reach rectangle");
        check(SiteMath.circleIntersectsRect(13,14,5,0,0,10,10),"Diagonal intersection 3-4-5");
        check(!SiteMath.circleIntersectsRect(13,14,4,0,0,10,10),"Diagonal gap");
        check(!SiteMath.circleIntersectsRect(Integer.MAX_VALUE,Integer.MAX_VALUE,32,
            Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE+32,Integer.MIN_VALUE+32),"No overflow at extreme world coordinates");
        try { SiteMath.circleIntersectsRect(0,0,-1,0,0,1,1); throw new AssertionError("Negative radius not rejected"); }
        catch(IllegalArgumentException expected) { /* pass */ }
        System.out.println("OK: 14 pruebas Java de geometria, colisiones y separacion");
    }
}
