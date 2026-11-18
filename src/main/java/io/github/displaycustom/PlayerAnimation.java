package io.github.displaycustom;

import java.util.*;

/** Deterministic, Bukkit-independent state selection and animation fallback. */
public final class PlayerAnimation {
    private PlayerAnimation(){}
    public record State(boolean sleeping,boolean seated,boolean flying,boolean swimming,boolean grounded,boolean rising,boolean sneaking,boolean moving,boolean sprinting,boolean climbing){
        public State(boolean sleeping,boolean seated,boolean flying,boolean swimming,boolean grounded,boolean rising,boolean sneaking,boolean moving,boolean sprinting){this(sleeping,seated,flying,swimming,grounded,rising,sneaking,moving,sprinting,false);}
    }
    public static String state(State s){
        if(s.sleeping)return "sleep";if(s.seated)return "sit";if(s.flying)return "fly";if(s.swimming)return "swim";
        if(s.climbing)return "climb";if(!s.grounded)return s.rising?"jump":"fall";
        if(s.sneaking)return s.moving?"sneak_walk":"sneak";
        return s.moving?(s.sprinting?"run":"walk"):"stand";
    }
    public static String clip(Model m,String state){
        var clips=m.animations();if(clips==null)return null;
        List<String> candidates=switch(state){case "run"->List.of("run","walk","stand");case "sneak_walk"->List.of("sneak_walk","sneak","walk","stand");case "fall"->List.of("fall","jump","stand");default->state.equals("stand")?List.of("stand"):List.of(state,"stand");};
        for(String tag:candidates){
            for(var e:clips.entrySet())if(!e.getValue().isHurt()&&e.getValue().tags()!=null&&e.getValue().tags().contains(tag))return e.getKey();
            var named=clips.get(tag);
            if(named!=null&&!named.isHurt())return tag;
            var idle=clips.get("idle");
            if(tag.equals("stand")&&idle!=null&&!idle.isHurt())return "idle";
        }
        return null;
    }
    public static float relativeYaw(float head,float body){float diff=(head-body)%360;return diff>=180?diff-360:diff< -180?diff+360:diff;}
}
