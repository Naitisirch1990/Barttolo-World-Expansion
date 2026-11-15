import java.util.*;
import net.barttolo.bwe.VillageFootprint;
public class FootprintCheck {
 static long k(int x,int z){return VillageFootprint.key(x,z);}
 static void require(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var triangle=Set.of(k(0,0),k(6,0),k(0,6));var filled=VillageFootprint.withCourtyards(triangle);
  require(filled.containsAll(triangle));require(filled.contains(k(2,2)));require(!filled.contains(k(6,6)));require(filled.size()==28);
  var negative=VillageFootprint.withCourtyards(Set.of(k(-10,-10),k(-4,-10),k(-10,-4)));
  require(negative.size()==28);require(negative.contains(k(-8,-8)));require(!negative.contains(k(-4,-4)));
  var line=Set.of(k(1,1),k(2,2),k(3,3));require(VillageFootprint.withCourtyards(line).equals(line));
  var rect=VillageFootprint.withCourtyards(Set.of(k(100,100),k(103,100),k(103,104),k(100,104)));require(rect.size()==20);
  System.out.println("PASS courtyards, exterior preservation, negative coordinates and degenerate footprint");
 }
}
