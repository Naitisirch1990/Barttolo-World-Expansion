package net.barttolo.bwe;

public final class TemplateFootprintTest {
    private static int checks;
    private static void expect(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var odd = TemplateFootprint.centered(0, 0, 5, 3, false);
        expect(odd.minX() == -2 && odd.maxX() == 2, "odd X");
        expect(odd.minZ() == -1 && odd.maxZ() == 1, "odd Z");
        expect(odd.area() == 15, "odd area");
        var even = TemplateFootprint.centered(0, 0, 4, 2, false);
        expect(even.minX() == -2 && even.maxX() == 1, "even X");
        expect(even.minZ() == -1 && even.maxZ() == 0, "even Z");
        expect(even.chunkCount() == 4, "negative chunk floor division");
        var large = TemplateFootprint.centered(0, 0, 150, 150, false);
        expect(large.area() == 22_500, "large template accepted");
        expect(large.chunkCount() == 100, "150x150 chunk coverage");
        var rotated = TemplateFootprint.centered(30, 40, 4, 9, true);
        expect(rotated.width() == 9 && rotated.length() == 4, "quarter turn");
        expect(TemplateFootprint.centered(-1, -1, 1, 1, false).chunkCount() == 1, "negative one block");
        expect(TemplateFootprint.centered(16, 16, 1, 1, false).chunkCount() == 1, "positive boundary");
        expect(!TemplateFootprint.centered(Integer.MAX_VALUE, 0, 500, 1, false).withinMinecraftCoordinates(), "far world coordinate");
        expect(TemplateFootprint.centered(0, 0, 1, 1, false).withinMinecraftCoordinates(), "world origin");
        var clearance = TemplateFootprint.expanded(odd, 7);
        expect(clearance.width()==19 && clearance.length()==17, "7-block margin dimensions");
        expect(clearance.area()==323, "clearance area");
        expect(clearance.minX()==-9 && clearance.maxX()==9, "expanded X");
        expect(clearance.minZ()==-8 && clearance.maxZ()==8, "expanded Z");
        expect(TemplateFootprint.expanded(odd,0).equals(odd), "zero expansion identity");
        expect(TemplateFootprint.expanded(odd,10).width()==25, "maximum margin");
        expect(TemplateFootprint.expanded(odd,7).chunkCount()==4, "negative margin chunk coverage");
        boolean badMargin=false;
        try { TemplateFootprint.expanded(odd,-1); }
        catch(IllegalArgumentException expected) { badMargin=true; }
        expect(badMargin,"negative margin rejected");
        badMargin=false;
        try { TemplateFootprint.expanded(odd,11); }
        catch(IllegalArgumentException expected) { badMargin=true; }
        expect(badMargin,"margin beyond maximum rejected");
        boolean overflow=false;
        try { TemplateFootprint.expanded(new TemplateFootprint.Bounds(Long.MIN_VALUE,0,0,0),1); }
        catch(ArithmeticException expected) { overflow=true; }
        expect(overflow,"coordinate overflow rejected");
        boolean threw = false;
        try { TemplateFootprint.centered(0, 0, 0, 1, false); }
        catch (IllegalArgumentException e) { threw = true; }
        expect(threw, "zero size rejected");
        System.out.println("OK: " + checks + " TemplateFootprint checks (pure Java 17)");
    }
}
