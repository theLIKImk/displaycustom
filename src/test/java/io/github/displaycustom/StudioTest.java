package io.github.displaycustom;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudioTest {
    private Model demo(){return Model.read(new InputStreamReader(getClass().getResourceAsStream("/fixtures/studio_demo.json"),StandardCharsets.UTF_8));}
    @Test void groupAndPartAnimationsComposeAndPreserveNativeOrigins(){
        Model m=demo();assertEquals(List.of("rise","spin"),m.defaultAnimations());
        for(var p:m.parts()){
            var point=p.entityType().equals("block")?new Vector3f(.5f,.5f,.5f):new Vector3f();
            var actual=m.matrix(p,m.defaultAnimations(),1,true).transformPosition(point);
            assertTrue(actual.distance(new Vector3f(p.position()).add(0,1,0))<1e-6);
        }
        var p=m.parts().get(1);var tip=m.matrix(p,m.defaultAnimations(),1,true).transformPosition(new Vector3f(1,0,0));assertTrue(tip.distance(new Vector3f(0,2,0))<1e-6);
        assertTrue(m.matrix(p,List.of(),0,false).transformPosition(new Vector3f()).distance(new Vector3f(1,1,0))<1e-6);
    }
    @Test void layerOverrideOnlyTouchesTargetsWithTracks(){
        Model m=demo();var arm=m.parts().get(1);
        var override=new Model.Animation(2,true,Map.of("arm",List.of(new Model.Keyframe(0,new float[]{5,0,0},new float[]{0,0,0},new float[]{1,1,1}))));
        Map<String,Model.Animation> clips=new LinkedHashMap<>(m.animations());clips.put("override",override);
        m=new Model(3,m.name(),m.parts(),clips,m.groups(),m.playback());
        assertTrue(m.matrix(arm,List.of("spin","override","rise"),1,true).transformPosition(new Vector3f()).distance(new Vector3f(5,1,0))<1e-6);
    }
    @Test void soundsFireOnceAcrossStartAndLoopBoundaries(){
        Model m=demo();var names=m.defaultAnimations();assertEquals(1,m.soundsBetween(names,-.000001,.05).size());assertEquals(0,m.soundsBetween(names,.05,.1).size());
        var events=m.soundsBetween(names,.1,2.1);assertEquals(2,events.size());assertEquals(1,events.get(0).at(),1e-6);assertEquals(2,events.get(1).at(),1e-6);
        assertTrue(m.soundsBetween(names,3,2).isEmpty());
        var fractional=new Model.Animation(2,true,Map.of(),List.of(new Model.SoundEvent(.4f,"test:cue",1,1)));
        Model precision=new Model(3,m.name(),m.parts(),Map.of("cue",fractional),m.groups(),null);
        assertEquals(1,precision.soundsBetween(List.of("cue"),.35,.4).size());
        assertEquals(0,precision.soundsBetween(List.of("cue"),.4,.45).size());
        assertEquals(1,precision.soundsBetween(List.of("cue"),2.35,2.4).size());
    }
    @Test void invalidGroupsSoundsPlaybackAndOlderSchemaAreRejected(){
        JsonObject valid=new Gson().toJsonTree(demo()).getAsJsonObject();
        for(String field:List.of("schema","group","sound","pitch","playlist","duplicate")){
            var root=valid.deepCopy();
            switch(field){
                case "schema" -> root.addProperty("version",2);
                case "group" -> root.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("group","missing");
                case "sound" -> root.getAsJsonObject("animations").getAsJsonObject("rise").getAsJsonArray("sounds").get(0).getAsJsonObject().addProperty("time",4);
                case "pitch" -> root.getAsJsonObject("animations").getAsJsonObject("rise").getAsJsonArray("sounds").get(0).getAsJsonObject().addProperty("pitch",0);
                case "playlist" -> root.getAsJsonObject("playback").getAsJsonArray("animations").add("missing");
                case "duplicate" -> root.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("id","base");
            }
            assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(root.toString())),field);
        }
    }
}
