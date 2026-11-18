package io.github.displaycustom;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayTypesTest {
    private Model demo(){return Model.read(new InputStreamReader(getClass().getResourceAsStream("/fixtures/display_types_demo.json"),StandardCharsets.UTF_8));}
    @Test void mixedTypesLoadWithoutResourceKeysForVanillaItemsOrText(){
        Model m=demo();
        assertEquals(2,m.version());
        assertEquals("block",m.parts().get(0).entityType());
        assertEquals("DIAMOND_SWORD",m.parts().get(1).carrier());
        assertNull(m.parts().get(1).itemModel());
        assertEquals("PAPER",m.parts().get(2).carrier());
        assertEquals("text",m.parts().get(3).entityType());
        assertTrue(m.parts().get(3).text().contains("文字"));
    }
    @Test void itemAndTextUseNativeOriginAndBlockKeepsCornerCorrection(){
        Model m=demo();
        for(var p:m.parts()){
            var point=p.entityType().equals("block")?new Vector3f(.5f,.5f,.5f):new Vector3f();
            var actual=Model.matrix(p,0,false).transformPosition(point);
            assertTrue(actual.distance(new Vector3f(p.position()))<1e-6);
        }
        assertEquals(180,Model.sample(m.parts().get(1),m.animations().get("showcase"),2,false).rotation()[1]);
    }
    @Test void malformedTextAndTypeFieldsAreRejected(){
        String base="{\"version\":2,\"name\":\"test\",\"parts\":[{\"id\":\"label\",\"type\":\"text\",\"text\":%s,\"position\":[0,0,0],\"rotation\":[0,0,0],\"scale\":[1,1,1]}]}";
        assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(base.formatted("123"))));
        String longText=new com.google.gson.Gson().toJson("x".repeat(1025));
        assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(base.formatted(longText))));
        String bad=base.formatted("\"hello\"").replace("\"text\",\"text\"","\"block\",\"text\"");
        assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(bad)));
        assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(base.formatted("\"hello\"").replace("\"version\":2","\"version\":1"))));
    }
}
