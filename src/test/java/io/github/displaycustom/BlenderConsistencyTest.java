package io.github.displaycustom;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlenderConsistencyTest {
    record Case(String part,String clip,double time,float[][] corners) {}
    private InputStreamReader reader(String path){
        return new InputStreamReader(getClass().getResourceAsStream("/blender/"+path),StandardCharsets.UTF_8);
    }
    @Test void pluginAcceptsRealBlenderExportAndMatchesItsEvaluatedWorldCorners(){
        Model model=Model.read(reader("model.json"));
        Case[] cases=new Gson().fromJson(reader("expected.json"),Case[].class);
        for(Case c:cases){
            var part=model.parts().stream().filter(p->p.id().equals(c.part)).findFirst().orElseThrow();
            var matrix=Model.matrix(part,model.animations().get(c.clip),c.time,false);
            int index=0;
            for(float x:new float[]{-.5f,.5f})for(float y:new float[]{-.5f,.5f})for(float z:new float[]{-.5f,.5f}){
                var actual=matrix.transformPosition(new Vector3f(x,y,z));
                var expected=new Vector3f(c.corners[index++]);
                assertTrue(actual.distance(expected)<1e-5,"Blender corner mismatch at "+c.time+"s: "+actual+" / "+expected);
            }
        }
    }
}
