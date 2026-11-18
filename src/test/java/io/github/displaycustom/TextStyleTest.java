package io.github.displaycustom;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextStyleTest {
    private Model demo(){return Model.read(new InputStreamReader(getClass().getResourceAsStream("/fixtures/text_style_demo.json"),StandardCharsets.UTF_8));}
    @Test void allStylesSurviveGsonAndPartialStylesHaveCompatibleDefaults(){
        Model m=demo();assertEquals(4,m.version());assertEquals(179,m.parts().get(1).appearance().opacity());
        var copy=Model.read(new StringReader(new Gson().toJson(m)));assertEquals(m.parts().get(0).textStyle(),copy.parts().get(0).textStyle());
        assertEquals(255,TextStyle.DEFAULT.opacity());assertEquals(0,TextStyle.DEFAULT.backgroundOpacity());assertTrue(TextStyle.DEFAULT.shadow());assertFalse(TextStyle.DEFAULT.bold());
    }
    @Test void styleRequiresVersionFourAndCorrectEntityTypeAndRejectsCoercions(){
        var valid=new Gson().toJsonTree(demo()).getAsJsonObject();
        var old=valid.deepCopy();old.addProperty("version",3);assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(old.toString())));
        var wrong=valid.deepCopy();wrong.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("type","block");assertThrows(IllegalArgumentException.class,()->Model.read(new StringReader(wrong.toString())));
        for(String json:List.of("{\"opacity\":12.5}","{\"opacity\":\"128\"}","{\"opacity\":256}","{\"opacity\":null}","{\"color\":1}","{\"shadow\":\"false\"}","{\"lineWidth\":0}","{\"lineWidth\":1001}","{\"alignment\":\"middle\"}","{\"backgroundColor\":\"#fff\"}","{\"bold\":1}","{\"unknown\":true}","[]")){
            var root=valid.deepCopy();root.getAsJsonArray("parts").get(0).getAsJsonObject().add("textStyle",JsonParser.parseString(json));
            assertThrows(RuntimeException.class,()->Model.read(new StringReader(root.toString())),json);
        }
    }
    @Test void opacityAndBackgroundEncodeUnsignedByteAndArgbExactly(){
        for(int opacity:List.of(0,1,127,128,254,255)){
            var style=new TextStyle("#ABCDEF",opacity,"#112233",opacity,false,false,"left",100,false,false);
            assertEquals(opacity,Byte.toUnsignedInt(style.opacityByte()));assertEquals(opacity,style.backgroundArgb()>>>24);assertEquals(0x112233,style.backgroundArgb()&0xffffff);assertEquals("#abcdef",style.color());
        }
    }
    @Test void paperDisplayReceivesTextComponentAndIndependentStyleSettings(){
        Map<String,Object> calls=new HashMap<>();
        TextDisplay proxy=(TextDisplay)Proxy.newProxyInstance(TextDisplay.class.getClassLoader(),new Class<?>[]{TextDisplay.class},(object,method,args)->{if(args!=null&&args.length==1)calls.put(method.getName(),args[0]);return null;});
        var p=demo().parts().get(0);TextDisplays.configure(proxy,p);
        Component component=(Component)calls.get("text");assertEquals(TextColor.color(0xffdd55),component.color());assertEquals(TextDecoration.State.TRUE,component.decoration(TextDecoration.BOLD));
        assertEquals((byte)-1,calls.get("setTextOpacity"));assertEquals(Color.fromARGB(192,17,34,51),calls.get("setBackgroundColor"));assertEquals(250,calls.get("setLineWidth"));assertEquals(false,calls.get("setDefaultBackground"));assertEquals(true,calls.get("setShadowed"));assertEquals(false,calls.get("setSeeThrough"));assertEquals(TextDisplay.TextAlignment.CENTER,calls.get("setAlignment"));
        calls.clear();TextDisplays.configure(proxy,demo().parts().get(1));assertEquals((byte)179,calls.get("setTextOpacity"));assertEquals(128,((Color)calls.get("setBackgroundColor")).getAlpha());assertEquals(false,calls.get("setShadowed"));assertEquals(TextDisplay.TextAlignment.LEFT,calls.get("setAlignment"));
    }
}
