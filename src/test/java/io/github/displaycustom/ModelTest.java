package io.github.displaycustom;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    private Model demo() {
        return new Gson().fromJson(new InputStreamReader(getClass().getResourceAsStream("/fixtures/demo.json"), StandardCharsets.UTF_8), Model.class).validate();
    }
    @Test void modelFixtureLoads() { assertEquals(10, demo().parts().size()); }
    @Test void expandedPartLimitAcceptsLargeModelsAndRejectsOverflow() {
        assertEquals(1024,ModelLimits.DEFAULT_MAX_PARTS);
        assertEquals(ModelLimits.DEFAULT_MAX_PARTS,ModelLimits.maxParts);
        var template=demo().parts().getFirst();
        var parts=new java.util.ArrayList<Model.Part>();
        for(int i=0;i<=ModelLimits.maxParts;i++) {
            parts.add(new Model.Part("piece_"+i,template.material(),template.position(),template.rotation(),template.scale(),new float[]{0,0,0},0));
            if(i==128 || i==ModelLimits.maxParts-1)assertEquals(i+1,new Model(7,"large_model",List.copyOf(parts)).validate().parts().size());
        }
        assertThrows(IllegalArgumentException.class,()->new Model(7,"large_model",parts).validate());
        try {
            ModelLimits.maxParts=ModelLimits.UNLIMITED;
            assertEquals(parts.size(),new Model(7,"large_model",parts).validate().parts().size());
        } finally {
            ModelLimits.maxParts=ModelLimits.DEFAULT_MAX_PARTS;
        }
    }
    @Test void configurableAnimationLimitsRejectOrAcceptAccordingToConfiguredValue() {
        var part=new Model.Part("body","IRON_BLOCK",new float[]{0,0.5f,0},new float[]{0,0,0},new float[]{1,1,1},new float[]{0,0,0},0);
        java.util.function.BiFunction<Integer,Integer,Model.Animation> clip=(frames,sounds) -> {
            var track=new java.util.ArrayList<Model.Keyframe>();
            for(int i=0;i<frames;i++)track.add(new Model.Keyframe(i*0.1f,new float[]{0,0.5f,0},new float[]{0,0,0},new float[]{1,1,1}));
            var list=new java.util.ArrayList<Model.SoundEvent>();
            for(int i=0;i<sounds;i++)list.add(new Model.SoundEvent(i*0.1f,"test:cue",1,1));
            return new Model.Animation(frames*0.1f+0.1f,true,Map.of("body",track),list);
        };
        try {
            ModelLimits.maxAnimations=2;ModelLimits.maxSounds=3;ModelLimits.maxFrames=8;
            assertThrows(IllegalArgumentException.class,()->new Model(3,"test",List.of(part),Map.of("a",clip.apply(8,0),"b",clip.apply(8,0),"c",clip.apply(8,0))).validate());
            assertThrows(IllegalArgumentException.class,()->new Model(3,"test",List.of(part),Map.of("a",clip.apply(8,4))).validate());
            assertThrows(IllegalArgumentException.class,()->new Model(3,"test",List.of(part),Map.of("a",clip.apply(9,0))).validate());
            ModelLimits.maxAnimations=ModelLimits.UNLIMITED;ModelLimits.maxSounds=ModelLimits.UNLIMITED;ModelLimits.maxFrames=ModelLimits.UNLIMITED;
            assertEquals(3,new Model(3,"test",List.of(part),Map.of("a",clip.apply(8,0),"b",clip.apply(8,0),"c",clip.apply(8,0))).validate().animations().size());
            assertEquals(4,new Model(3,"test",List.of(part),Map.of("a",clip.apply(8,4))).validate().animations().get("a").sounds().size());
            assertEquals(9,new Model(3,"test",List.of(part),Map.of("a",clip.apply(9,0))).validate().animations().get("a").tracks().get("body").size());
        } finally {
            ModelLimits.reset();
        }
    }
    @Test void rejectsDuplicateIdsAndBrokenScales() {
        var p=demo().parts().getFirst();
        assertThrows(IllegalArgumentException.class,()->new Model(1,"demo",List.of(p,p)).validate());
        var bad=new Model.Part("bad","IRON_BLOCK",new float[]{0,0,0},new float[]{0,0,0},new float[]{0,1,1},new float[]{0,0,0},0);
        assertThrows(IllegalArgumentException.class,()->new Model(1,"demo",List.of(bad)).validate());
        assertThrows(IllegalArgumentException.class,()->new Model(1,"../escape",List.of(p)).validate());
    }
    @Test void combinedRotationKeepsCenterAndMatchesEditorReference() {
        var p=new Model.Part("box","IRON_BLOCK",new float[]{2,3,4},new float[]{30,45,60},new float[]{1,2,3},new float[]{0,0,0},0);
        var matrix=Model.matrix(p,0,false);
        var center=matrix.transformPosition(new Vector3f(.5f,.5f,.5f));
        assertEquals(2,center.x,1e-5);assertEquals(3,center.y,1e-5);assertEquals(4,center.z,1e-5);
        var corner=matrix.transformPosition(new Vector3f(0,0,0));
        assertEquals(1.3749355686193363,corner.x,1e-5);
        assertEquals(2.94011525419727,corner.y,1e-5);
        assertEquals(2.2376980185442363,corner.z,1e-5);
    }
    @Test void stoppingRestoresStaticPose() {
        var p=demo().parts().get(5);
        assertTrue(Model.matrix(p,0,false).equals(Model.matrix(p,100,false),1e-6f));
        assertFalse(Model.matrix(p,0,false).equals(Model.matrix(p,.3125,true),1e-6f));
    }
    @Test void itemGeometryUsesItsNativeCenterWithoutTheBlockCornerOffset() {
        var block=demo().parts().getFirst();
        var item=new Model.Part(block.id(),block.material(),block.position(),block.rotation(),block.scale(),block.swing(),block.phase(),"display_custom:test/body");
        new Model(1,"test",List.of(item)).validate();
        var blockCenter=Model.matrix(block,0,false).transformPosition(new Vector3f(.5f,.5f,.5f));
        var itemCenter=Model.matrix(item,0,false).transformPosition(new Vector3f(0,0,0));
        assertTrue(blockCenter.equals(itemCenter,1e-5f));
        var bad=new Model.Part(block.id(),block.material(),block.position(),block.rotation(),block.scale(),block.swing(),0,"display_custom:../escape");
        assertThrows(IllegalArgumentException.class,()->new Model(1,"test",List.of(bad)).validate());
    }
}
