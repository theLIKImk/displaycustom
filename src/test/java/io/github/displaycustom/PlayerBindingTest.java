package io.github.displaycustom;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerBindingTest {
    private static final String JSON="""
      {"version":7,"name":"binding","info":{"name":"显示名称","author":"测试作者","description":"第一行\n第二行","contact":"example@example.invalid","copyright":"测试许可"},
       "groups":[{"id":"body","kind":"bone","length":1,"tags":["body"],"position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 {"id":"head","group":"body","kind":"bone","tags":["head"],"pivot":[0,0.1,0],"position":[0,1,0],"rotation":[0,0,0],"scale":[1,1,1]},
                 {"id":"hair","group":"head","tags":["head"],"position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}],
       "parts":[{"id":"torso","group":"body","material":"IRON_BLOCK","position":[0,0.5,0],"rotation":[0,0,0],"scale":[1,1,1]},
                {"id":"eye","group":"hair","material":"IRON_BLOCK","position":[0,0.1,0.2],"rotation":[0,0,0],"scale":[0.1,0.1,0.1]}],
       "animations":{"breathe":{"duration":2,"loop":true,"tags":["stand"],"tracks":{}},"stride":{"duration":1,"loop":true,"tags":["walk","run"],"tracks":{}},"hop":{"duration":1,"loop":false,"tags":["jump"],"tracks":{}},"crouch":{"duration":1,"loop":true,"tags":["sneak"],"tracks":{}}}}
      """.replace("第一行\n第二行","第一行\\n第二行");
    private Model model(){return Model.read(new StringReader(JSON));}
    @Test void metadataTagsAndLegacyConstructorsRoundTrip(){
        Model m=model();assertEquals("显示名称",m.info().name());assertEquals("第一行\n第二行",m.info().description());
        assertEquals(m.info(),Model.read(new StringReader(new Gson().toJson(m))).info());
        assertEquals(List.of("head"),m.groups().get(1).tags());
        assertDoesNotThrow(()->new Model(1,"old",List.of(new Model.Part("p","IRON_BLOCK",new float[3],new float[3],new float[]{1,1,1},null,0))).validate());
    }
    @Test void headGazeRotatesAtNeckOnceAndDoesNotTiltBody(){
        Model m=model();var eye=m.parts().get(1);var center=new Vector3f(.5f,.5f,.5f);
        var actual=m.matrix(eye,List.of(),0,false,90,0).transformPosition(new Vector3f(center));
        assertTrue(actual.distance(new Vector3f(-.2f,1.1f,0))<1e-5,actual.toString());
        var down=m.matrix(eye,List.of(),0,false,0,90).transformPosition(new Vector3f(center));
        assertTrue(down.distance(new Vector3f(0,.9f,0))<1e-5,down.toString());
        assertTrue(m.matrix(m.parts().getFirst(),List.of(),0,false,75,80).equals(m.matrix(m.parts().getFirst(),List.of(),0,false),1e-6f));
        assertEquals(2,PlayerAnimation.relativeYaw(-179,179));assertEquals(-2,PlayerAnimation.relativeYaw(179,-179));
    }
    @Test void explicitStateTagsWinOverNamesAndFallbacksAreDeterministic(){
        Model m=model();assertEquals("breathe",PlayerAnimation.clip(m,"stand"));assertEquals("stride",PlayerAnimation.clip(m,"run"));
        assertEquals("crouch",PlayerAnimation.clip(m,"sneak_walk"));assertEquals("hop",PlayerAnimation.clip(m,"fall"));assertEquals("breathe",PlayerAnimation.clip(m,"sit"));
        var clips=new LinkedHashMap<>(m.animations());clips.put("run",new Model.Animation(1,true,Map.of()));
        Model withName=new Model(7,m.name(),m.parts(),clips,m.groups(),null,m.info());assertEquals("stride",PlayerAnimation.clip(withName,"run"));
        assertNull(PlayerAnimation.clip(new Model(1,"static",List.of()),"stand"));
    }
    @Test void statePriorityDistinguishesMotionSneakRidingAndAirborne(){
        assertEquals("climb",PlayerAnimation.state(new PlayerAnimation.State(false,false,false,false,false,true,true,true,true,true)));
        assertEquals("sleep",PlayerAnimation.state(new PlayerAnimation.State(true,false,false,false,false,true,true,true,true,true)));
        assertEquals("stand",state(false,false,false,false,true,false,false,false,true));
        assertEquals("walk",state(false,false,false,false,true,false,false,true,false));
        assertEquals("run",state(false,false,false,false,true,false,false,true,true));
        assertEquals("sneak",state(false,false,false,false,true,false,true,false,true));
        assertEquals("sneak_walk",state(false,false,false,false,true,false,true,true,true));
        assertEquals("jump",state(false,false,false,false,false,true,true,true,true));
        assertEquals("fall",state(false,false,false,false,false,false,false,true,true));
        assertEquals("sit",state(false,true,false,false,false,false,true,true,true));
        assertEquals("swim",state(false,false,false,true,false,false,true,true,true));
        assertEquals("fly",state(false,false,true,false,false,false,true,true,true));
        assertEquals("sleep",state(true,true,true,true,false,true,true,true,true));
    }
    @Test void bedFootAndHeadProduceSamePlacementForAllDirections(){
        for(var face:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.WEST)){
            var foot=new org.bukkit.Location(null,10,64,20);var head=foot.clone().add(face.getModX(),0,face.getModZ());
            var a=SpawnPlacement.sleeping(foot,face,false);var b=SpawnPlacement.sleeping(head,face,true);
            assertEquals(a,b);assertEquals(64.5625,a.getY());
            var v=a.getDirection();assertEquals(-face.getModX(),v.getX(),1e-6);assertEquals(-face.getModZ(),v.getZ(),1e-6);
        }
    }
    private String state(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f,boolean g,boolean h,boolean i){return PlayerAnimation.state(new PlayerAnimation.State(a,b,c,d,e,f,g,h,i));}
    @Test void malformedInfoAndAmbiguousTagsAreRejected(){
        JsonObject original=JsonParser.parseString(JSON).getAsJsonObject();
        for(String bad:List.of("version","infoType","infoValue","infoLength","tagsType","tagsValue","roleConflict","duplicateState","unknownState")){
            var r=original.deepCopy();switch(bad){
                case "version"->r.addProperty("version",6);
                case "infoType"->r.addProperty("info",true);
                case "infoValue"->r.getAsJsonObject("info").addProperty("author",42);
                case "infoLength"->r.getAsJsonObject("info").addProperty("author","a".repeat(129));
                case "tagsType"->r.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("tags","body");
                case "tagsValue"->r.getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("tags").set(0,new JsonPrimitive(3));
                case "roleConflict"->r.getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("tags").add("head");
                case "duplicateState"->r.getAsJsonObject("animations").getAsJsonObject("stride").getAsJsonArray("tags").add("stand");
                case "unknownState"->r.getAsJsonObject("animations").getAsJsonObject("stride").getAsJsonArray("tags").add("dance");
            }assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(r.toString())),bad);
        }
    }
}
