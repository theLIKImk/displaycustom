package io.github.displaycustom;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Location;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AdvancedTest {
    @Test void humanoidBonesAndFullTurnAreAcceptedByServer(){
        var m=Model.read(new InputStreamReader(getClass().getResourceAsStream("/fixtures/humanoid_demo.json"),StandardCharsets.UTF_8));
        assertEquals(13,m.groups().stream().filter(g->"bone".equals(g.kind())).count());
        var part=m.parts().getFirst();var start=m.matrix(part,List.of("turn"),0,false);
        assertTrue(start.equals(m.matrix(part,List.of("turn"),4,false),1e-5f));
        assertFalse(start.equals(m.matrix(part,List.of("turn"),2,false),1e-5f));
        assertEquals(360,m.animations().get("turn").tracks().get("rig").getLast().rotation()[1]);
    }
    private Model demo(){return Model.read(new InputStreamReader(getClass().getResourceAsStream("/fixtures/advanced_demo.json"),StandardCharsets.UTF_8));}
    @Test void sampledTextMatchesBrowserGradientAndStepSemantics(){
        var m=demo();var part=m.parts().get(3);var middle=Model.sampleText(part,m.animations().get("demo"),1,false);
        assertEquals("开始 / START",middle.text());assertEquals("#aa77aa",middle.style().color());assertEquals(143,middle.style().opacity());
        assertEquals("结束 / END",Model.sampleText(part,m.animations().get("demo"),2,false).text());assertEquals("开始 / START",Model.sampleText(part,m.animations().get("demo"),2,true).text());
    }
    @Test void nestedPivotMatrixMatchesIndependentWorldPoint(){
        var groups=List.of(new Model.Group("root",new float[]{2,0,0},new float[]{0,0,90},new float[]{1,1,1}),new Model.Group("bone",new float[]{0,1,0},new float[]{0,0,90},new float[]{2,1,1},"root","bone",new float[]{.5f,0,0},1f));
        var part=new Model.Part("cube","IRON_BLOCK",new float[]{1,0,0},new float[]{0,0,0},new float[]{1,1,1},null,0,null,"block",null,null,"bone");
        var m=new Model(5,"nested",List.of(part),null,groups,null).validate();assertTrue(m.matrix(part,List.of(),0,false).transformPosition(new Vector3f(.5f,.5f,.5f)).distance(new Vector3f(0,.5f,0))<1e-5);
        var cyclic=List.of(new Model.Group("root",new float[3],new float[3],new float[]{1,1,1},"bone",null,null,null),groups.get(1));assertThrows(IllegalArgumentException.class,()->new Model(5,"nested",List.of(part),null,cyclic,null).validate());
    }
    @Test void singleAnimationSpeedIsRequiredAndInvalidListsCannotStart(){
        var request=PlaybackRequest.parse(new String[]{"play","id","demo","2"});assertEquals(2,request.seconds(20));
        assertEquals(.5,PlaybackRequest.parse(new String[]{"play","id","demo","0.5"}).seconds(20));
        for(String[] args:List.of(new String[]{"play","id"},new String[]{"play","id","demo"},new String[]{"play","id","demo,other","1"},new String[]{"play","id","demo","NaN"},new String[]{"play","id","demo","0"},new String[]{"play","id","demo","21"}))assertThrows(IllegalArgumentException.class,()->PlaybackRequest.parse(args));
    }
    @Test void spawnUsesExactPositionAndBodyYawWithoutPitchOrMutatingPlayerLocation(){
        var player=new Location(null,12.25,64.5,-8.75,137.5f,-23.25f);var origin=SpawnPlacement.at(player,90);
        assertEquals(player.toVector(),origin.toVector());assertEquals(90,origin.getYaw());assertEquals(0,origin.getPitch());assertNotSame(player,origin);origin.add(1,2,3);assertEquals(12.25,player.getX());assertEquals(137.5f,player.getYaw());assertEquals(-23.25f,player.getPitch());
    }
}
