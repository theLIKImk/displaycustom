package io.github.displaycustom;

import java.io.StringReader;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NametagHeightTest {
    private Model model(String type){return Model.read(new StringReader("""
      {"version":7,"name":"height","groups":[{"id":"head","position":[0,2,0],"rotation":[0,0,0],"scale":[1,1,1]}],
       "parts":[{"id":"hair","type":"%s","material":"IRON_BLOCK","group":"head","position":[0,0.2,0],"rotation":[0,0,0],"scale":[0.2,0.6,0.2]}],
       "animations":{"duck":{"duration":1,"loop":false,"tracks":{"head":[
        {"time":0,"position":[0,2,0],"rotation":[0,0,0],"scale":[1,1,1]},
        {"time":1,"position":[0,1,0],"rotation":[0,0,0],"scale":[1,1,1],"visible":false}]}}}}
      """.formatted(type)));}
    @Test void centeredItemsAndCornerAnchoredBlocksHaveSameTop(){
        for(String type:List.of("item","block")){
            var m=model(type);
            assertEquals(2.5,m.topHeight(List.of(),0,false,0,0),1e-5);
            assertEquals(2,m.topHeight(List.of("duck"),.5,false,0,0),1e-5);
            assertEquals(0,m.topHeight(List.of("duck"),1,false,0,0),1e-5);
        }
    }
    @Test void usesActualRenderedPoseDuringBlendingAndOmitsHiddenParts(){
        var m=model("item");
        assertEquals(1.8,m.topHeight(Map.of("hair",new Matrix4f().translation(0,1.5f,0).scale(.2f,.6f,.2f))),1e-5);
        assertEquals(0,m.topHeight(Map.of()),1e-5);
        assertEquals(.1,m.topHeight(Map.of("hair",new Matrix4f().rotationZ((float)Math.PI/2).scale(.2f,.6f,.2f))),1e-5);
    }
}
