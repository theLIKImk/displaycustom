package io.github.displaycustom;

import com.google.gson.Gson;
import java.io.StringReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {
    private Model.Part part(){return new Model.Part("body","IRON_BLOCK",new float[]{0,1,0},new float[]{0,0,0},new float[]{1,1,1},null,0,"display_custom:colors/ff0000");}
    private Model.Animation clip(boolean loop){return new Model.Animation(2,loop,Map.of("body",List.of(
        new Model.Keyframe(0,new float[]{0,1,0},new float[]{0,0,0},new float[]{1,1,1}),
        new Model.Keyframe(2,new float[]{4,3,2},new float[]{180,90,360},new float[]{3,5,1}))));}
    @Test void interpolationMatchesEditorNumbersForAllChannels(){
        var value=Model.sample(part(),clip(false),.5,true);
        assertArrayEquals(new float[]{1,1.5f,.5f},value.position(),1e-6f);
        assertArrayEquals(new float[]{45,22.5f,90},value.rotation(),1e-6f);
        assertArrayEquals(new float[]{1.5f,2,1},value.scale(),1e-6f);
        assertEquals(180,Model.sample(part(),clip(false),1,true).rotation()[2],1e-6);
    }
    @Test void loopAndSingleShotHonorBoundaries(){
        assertArrayEquals(part().position(),Model.sample(part(),clip(true),2,true).position());
        assertArrayEquals(new float[]{4,3,2},Model.sample(part(),clip(true),2,false).position());
        assertArrayEquals(new float[]{4,3,2},Model.sample(part(),clip(false),99,true).position());
    }
    @Test void invalidKeyframeTimesAndReferencesFailBeforePlayback(){
        var c=clip(true);var frames=c.tracks().get("body");
        var duplicate=new Model.Animation(2,true,Map.of("body",List.of(frames.getFirst(),frames.getFirst())));
        assertThrows(IllegalArgumentException.class,()->new Model(1,"test",List.of(part()),Map.of("wave",duplicate)).validate());
        var missing=new Model.Animation(2,true,Map.of("missing",frames));
        assertThrows(IllegalArgumentException.class,()->new Model(1,"test",List.of(part()),Map.of("wave",missing)).validate());
    }
    @Test void oldColorIsMigratedThenAbsentFromExport(){
        String json=new Gson().toJson(new Model(1,"test",List.of(part())));
        var root=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        root.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("color","#123abc");
        Model loaded=Model.read(new StringReader(root.toString()));
        assertEquals("display_custom:colors/1133bb",loaded.parts().getFirst().itemModel());
        assertFalse(new Gson().toJson(loaded).contains("\"color\""));
        assertEquals("display_custom:colors/ffffff",Palette.key("#ffffff"));
        assertFalse(Palette.validReference("display_custom:colors/123abc"));
        assertTrue(Palette.validReference("display_custom:colors/1133bb"));
        assertTrue(Palette.validReference("display_custom:colors/red"));
    }
    @Test void bundledAnimationSurvivesJsonRoundTrip(){
        var reader=new InputStreamReader(getClass().getResourceAsStream("/fixtures/animation_demo.json"),StandardCharsets.UTF_8);
        Model loaded=Model.read(reader);
        Model roundtrip=Model.read(new StringReader(new Gson().toJson(loaded)));
        assertEquals(2,roundtrip.animations().get("wave").duration());
        var left=roundtrip.parts().stream().filter(p->p.id().equals("arm_left")).findFirst().orElseThrow();
        assertEquals(-65,Model.sample(left,roundtrip.animations().get("wave"),1,false).rotation()[2]);
    }
}
