package io.github.displaycustom;

import com.google.gson.*;
import java.util.*;

/** Explicit defaults preserve the appearance of pre-v4 text parts. */
public record TextStyle(String color,Integer opacity,String backgroundColor,Integer backgroundOpacity,
                        Boolean shadow,Boolean seeThrough,String alignment,Integer lineWidth,Boolean bold,Boolean italic) {
    public static final Set<String> FIELDS=Set.of("color","opacity","backgroundColor","backgroundOpacity","shadow","seeThrough","alignment","lineWidth","bold","italic");
    public static final TextStyle DEFAULT=new TextStyle(null,null,null,null,null,null,null,null,null,null);
    public TextStyle {
        color=color==null?"#ffffff":color.toLowerCase(Locale.ROOT);opacity=opacity==null?255:opacity;
        backgroundColor=backgroundColor==null?"#000000":backgroundColor.toLowerCase(Locale.ROOT);backgroundOpacity=backgroundOpacity==null?0:backgroundOpacity;
        shadow=shadow==null?true:shadow;seeThrough=seeThrough==null?false:seeThrough;
        alignment=alignment==null?"center":alignment;lineWidth=lineWidth==null?200:lineWidth;
        bold=bold==null?false:bold;italic=italic==null?false:italic;
        if(!color.matches("#[0-9a-f]{6}")||!backgroundColor.matches("#[0-9a-f]{6}"))throw Messages.failure("error.text.color");
        if(opacity<0||opacity>255||backgroundOpacity<0||backgroundOpacity>255)throw Messages.failure("error.text.opacity");
        if(!Set.of("left","center","right").contains(alignment))throw Messages.failure("error.text.alignment");
        if(lineWidth<1||lineWidth>1000)throw Messages.failure("error.text.width");
    }
    public static void validateJson(JsonElement value){
        if(value==null||value.isJsonNull())return;
        if(!value.isJsonObject())throw Messages.failure("error.text.object");
        for(var entry:value.getAsJsonObject().entrySet()){
            String key=entry.getKey();JsonElement v=entry.getValue();
            if(!FIELDS.contains(key)||!v.isJsonPrimitive())throw Messages.failure("error.text.field", key);
            var primitive=v.getAsJsonPrimitive();
            if(Set.of("color","backgroundColor","alignment").contains(key)){
                if(!primitive.isString())throw Messages.failure("error.text.string", key);
            }else if(Set.of("shadow","seeThrough","bold","italic").contains(key)){
                if(!primitive.isBoolean())throw Messages.failure("error.text.boolean", key);
            }else{
                if(!primitive.isNumber())throw Messages.failure("error.text.integer", key);
                double n=primitive.getAsDouble();
                if(!Double.isFinite(n)||n!=Math.rint(n)||n<0||n>(key.equals("lineWidth")?1000:255))throw Messages.failure("error.text.range", key);
            }
        }
    }
    public static TextStyle interpolate(TextStyle a,TextStyle b,double t){return new TextStyle(mixColor(a.color,b.color,t),mix(a.opacity,b.opacity,t),mixColor(a.backgroundColor,b.backgroundColor,t),mix(a.backgroundOpacity,b.backgroundOpacity,t),a.shadow,a.seeThrough,a.alignment,a.lineWidth,a.bold,a.italic);}
    private static int mix(int a,int b,double t){return (int)Math.round(a+(b-a)*t);}
    private static String mixColor(String a,String b,double t){int first=Integer.parseInt(a.substring(1),16),last=Integer.parseInt(b.substring(1),16);return String.format("#%02x%02x%02x",mix(first>>16&255,last>>16&255,t),mix(first>>8&255,last>>8&255,t),mix(first&255,last&255,t));}
    public byte opacityByte(){return (byte)(int)opacity;}
    public int backgroundArgb(){return backgroundOpacity<<24|Integer.parseInt(backgroundColor.substring(1),16);}
}
