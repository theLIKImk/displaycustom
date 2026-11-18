package io.github.displaycustom;
import java.io.StringReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MotionTest {
 private Model demo(){return Model.read(new InputStreamReader(getClass().getResourceAsStream("/fixtures/motion_demo.json"),StandardCharsets.UTF_8));}
 @Test void discretePoseAndInheritedVisibilityHaveExactBoundaries(){
  var m=demo();var p=m.parts().getFirst();var clip=m.animations().get("stop_motion");
  assertTrue(m.motion(p,clip,.99,false).visible());assertFalse(m.motion(p,clip,1,false).visible());
  assertFalse(m.motion(p,clip,1.49,false).visible());assertTrue(m.motion(p,clip,1.5,false).visible());
  assertTrue(m.motion(p,null,1,false).visible());assertTrue(m.motion(p,clip,2,true).visible());
  var a=m.matrix(p,List.of("stop_motion"),.49,false);var b=m.matrix(p,List.of("stop_motion"),0,false);
  assertTrue(a.equals(b,1e-6f));assertFalse(a.equals(m.matrix(p,List.of("stop_motion"),.5,false),1e-6f));
  assertFalse(m.motion(p,clip,.4,false).snapFrom(m.motion(p,clip,.2,false)));
  assertTrue(m.motion(p,clip,.5,false).snapFrom(m.motion(p,clip,.49,false)));
  assertTrue(m.motion(p,clip,1,false).snapFrom(m.motion(p,clip,.99,false)));
  assertTrue(m.motion(p,clip,2,true).snapFrom(m.motion(p,clip,1.99,true)));
 }
 @Test void frameOverrideRestoresLinearAndTextStyleHonorsStep(){
  var m=demo();var p=m.parts().getFirst();
  var first=new Model.Keyframe(0,new float[3],new float[3],new float[]{1,1,1},null,null,null,"linear");
  var last=new Model.Keyframe(1,new float[]{2,0,0},new float[3],new float[]{1,1,1});
  var clip=new Model.Animation(1,false,Map.of(p.id(),List.of(first,last)),null,"step");
  assertEquals(1,Model.sample(p,clip,.5,false).position()[0]);assertFalse(Model.frameState(p.id(),clip,.5,false).step());
  var text=m.parts().stream().filter(n->n.entityType().equals("text")).findFirst().orElseThrow();var c=m.animations().get("stop_motion");
  assertEquals("#ff5555",Model.sampleText(text,c,.25,false).style().color());
  assertEquals("#5555ff",Model.sampleText(text,c,.5,false).style().color());
 }
 @Test void wrongTypesAndOldVersionExtensionsAreRejected(){
  var raw="{\"version\":6,\"name\":\"test\",\"parts\":[{\"id\":\"p\",\"position\":[0,0,0],\"rotation\":[0,0,0],\"scale\":[1,1,1]}],\"animations\":{\"a\":{\"duration\":1,\"loop\":false,\"tracks\":{\"p\":[{\"time\":0,\"position\":[0,0,0],\"rotation\":[0,0,0],\"scale\":[1,1,1],\"visible\":false,\"interpolation\":\"step\"}]}}}}";
  assertEquals(6,Model.read(new StringReader(raw)).version());
  for(var bad:List.of(raw.replace("\"visible\":false","\"visible\":\"false\""),raw.replace("\"step\"","\"cubic\""),raw.replace("\"version\":6","\"version\":5")))assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(bad)));
 }
}
