package net.barttolo.bwe;

import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic tests for layered roof/ground detection (no Minecraft). */
public final class TerrainColumnProbeLayeredTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var soil = TerrainColumnProbe.findGroundLayered(65,-64,
            y -> y<=64 ? TerrainColumnProbe.SOIL : TerrainColumnProbe.NONE);
        check(soil.found() && soil.groundY()==65 && soil.ignoredAbove()==0,"soil flat");
        var roof = TerrainColumnProbe.findGroundLayered(85,-64,
            y -> (y==83||y==82) ? TerrainColumnProbe.ROCK :
                (y==64||y==63) ? TerrainColumnProbe.SOIL : TerrainColumnProbe.NONE);
        check(roof.found() && roof.groundY()==65 && roof.ignoredAbove()==20,
            "thin sandstone roof ignored");
        var stone = TerrainColumnProbe.findGroundLayered(81,-64,
            y -> y>=61&&y<=64 ? TerrainColumnProbe.ROCK : TerrainColumnProbe.NONE);
        check(stone.found() && stone.groundY()==65 && stone.ignoredAbove()==16,
            "rock needs four layers");
        var thin = TerrainColumnProbe.findGroundLayered(81,-64,
            y -> y>=63&&y<=64 ? TerrainColumnProbe.ROCK : TerrainColumnProbe.NONE);
        check(!thin.found(),"thin rock alone cannot be ground");
        var unknown = TerrainColumnProbe.findGroundLayered(81,-64,
            y -> TerrainColumnProbe.NONE);
        check(!unknown.found() && unknown.ignoredAbove()==48,"unknown bounded");
        AtomicInteger reads = new AtomicInteger();
        TerrainColumnProbe.findGroundLayered(120,-64,y -> {
            reads.incrementAndGet(); return TerrainColumnProbe.NONE;
        });
        check(reads.get()==48,"max 48 reads");
        var negative = TerrainColumnProbe.findGroundLayered(-58,-64,
            y -> y<=-59 ? TerrainColumnProbe.SOIL : TerrainColumnProbe.NONE);
        check(negative.found() && negative.groundY()==-58,"negative heights");
        var voidColumn = TerrainColumnProbe.findGroundLayered(-64,-64,
            y -> TerrainColumnProbe.SOIL);
        check(!voidColumn.found(),"void");
        try {
            TerrainColumnProbe.findGroundLayered(65,-64,y -> 3);
            throw new AssertionError("must reject unknown type");
        } catch (IllegalArgumentException expected) { checks++; }
        try {
            TerrainColumnProbe.findGroundLayered(65,-64,null);
            throw new AssertionError("must reject null");
        } catch (IllegalArgumentException expected) { checks++; }
        System.out.println("OK: " + checks + " layered terrain probe checks (Java 17)");
    }
}
