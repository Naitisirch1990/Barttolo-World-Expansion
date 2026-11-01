package net.barttolo.bwe;

public final class TemplateScanCursorTest {
    private static int checks;
    private static void expect(boolean value, String description) {
        checks++;
        if(!value) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        var b=TemplateFootprint.centered(-16, 16, 2, 2, false);
        var c=new TemplateScanCursor(b, -2, 0);
        expect(c.total()==12L,"2x2x3 volume");
        expect(c.processed()==0L,"initial cursor");
        expect(c.hasNext(),"not empty");
        var first=c.next();
        expect(first.x()==-17 && first.y()==-2 && first.z()==15,"first negative coordinate");
        var second=c.next();
        expect(second.x()==-16 && second.y()==-2 && second.z()==15,"X increments first");
        var third=c.next();
        expect(third.x()==-17 && third.y()==-2 && third.z()==16,"Z increments second");
        c.next();
        var fifth=c.next();
        expect(fifth.x()==-17 && fifth.y()==-1 && fifth.z()==15,"Y increments after plane");
        while(c.hasNext()) c.next();
        expect(c.processed()==12L,"all positions visited");
        expect(!c.hasNext(),"completed");
        boolean threw=false;
        try { c.next(); } catch(java.util.NoSuchElementException ex) { threw=true; }
        expect(threw,"end of cursor throws");
        var single=new TemplateScanCursor(TemplateFootprint.centered(0,0,1,1,false),100,100);
        expect(single.total()==1L,"one block");
        expect(single.next().y()==100,"one block Y");
        threw=false;
        try { new TemplateScanCursor(b, 4, 3); } catch(IllegalArgumentException ex) { threw=true; }
        expect(threw,"invalid vertical range");
        threw=false;
        try { new TemplateScanCursor(TemplateFootprint.centered(Integer.MAX_VALUE,0,2,2,false),0,1); }
        catch(IllegalArgumentException ex) { threw=true; }
        expect(threw,"world coordinates rejected");
        var large=new TemplateScanCursor(TemplateFootprint.centered(0,0,150,150,false),60,159);
        expect(large.total()==2_250_000L,"large footprint counted with long");
        expect(large.next().x()==-75,"large footprint first X");
        System.out.println("OK: "+checks+" TemplateScanCursor checks (pure Java 17)");
    }
}
