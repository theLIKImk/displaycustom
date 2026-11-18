package io.github.displaycustom;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemActionTest {
    @Test void distinguishesWeaponFamiliesAndToolFallback(){
        assertEquals("axe",ItemAction.family("IRON_AXE"));
        assertEquals("pickaxe",ItemAction.family("IRON_PICKAXE"));
        assertEquals("spear",ItemAction.family("COPPER_SPEAR"));
        assertEquals("trident",ItemAction.family("TRIDENT"));
        assertEquals("crossbow",ItemAction.family("CROSSBOW"));
        assertEquals("other",ItemAction.family("SHEARS"));
    }

    @Test void mirrorsArmTrackWithoutResettingTheWalkingClock(){
        Model model=Model.read(new StringReader("""
          {"version":7,"name":"action_demo",
           "groups":[{"id":"root","position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                     {"id":"left_arm","group":"root","position":[1,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                     {"id":"right_arm","group":"root","position":[-1,0,0],"rotation":[0,0,0],"scale":[1,1,1]}],
           "parts":[{"id":"arm","type":"item","group":"left_arm","position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
                    {"id":"leg","type":"item","group":"root","position":[0,-1,0],"rotation":[0,0,0],"scale":[1,1,1]}],
           "animations":{"walk":{"duration":2,"loop":true,"tracks":{"root":[
             {"time":0,"position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
             {"time":2,"position":[0,2,0],"rotation":[0,0,0],"scale":[1,1,1]}]}},
             "use_sword":{"duration":1,"loop":false,"tracks":{"right_arm":[
               {"time":0,"position":[-1,0,0],"rotation":[0,0,0],"scale":[1,1,1]},
               {"time":1,"position":[-2,0,0],"rotation":[0,0,0],"scale":[1,1,1]}]}}}}
          """));
        var source=model.animations().get("use_sword");
        var action=ItemAction.mirrored(new Model.Animation(source.duration(),false,source.tracks(),null,null,null,null,true));
        assertNull(ItemAction.select(model,"use","IRON_SWORD",null),"A legacy animation name alone cannot trigger an action");
        var arm=model.matrix(model.parts().getFirst(),List.of("walk"),1.5,false,0,0,action,.25);
        assertEquals(1.25,arm.m30(),1e-5);
        assertEquals(1.5,arm.m31(),1e-5);
        var leg=model.matrix(model.parts().getLast(),List.of("walk"),1.5,false,0,0,action,.25);
        assertEquals(.5,leg.m31(),1e-5);
        assertTrue(model.affectedBy(model.parts().getFirst(),action));
        assertFalse(model.affectedBy(model.parts().getLast(),action));
        var exclusive=ItemAction.mirrored(new Model.Animation(source.duration(),false,source.tracks(),null,null,null,null,false));
        var exclusiveLeg=model.matrix(model.parts().getLast(),List.of("walk"),1.5,false,0,0,exclusive,.25);
        assertEquals(-1,exclusiveLeg.m31(),1e-5);
    }

    @Test void mapsSpearPhasesAndExpiresEvenIfTheButtonRemainsHeld(){
        var charge=new KineticCharge(8,24,36,50);
        assertEquals(0,charge.sample(0,3),1e-6);
        assertEquals(1,charge.sample(24,3),1e-6);
        assertEquals(1.5,charge.sample(30,3),1e-6);
        assertEquals(2,charge.sample(36,3),1e-6);
        assertEquals(3,charge.sample(50,3),1e-6);
        assertFalse(charge.expired(50));
        assertTrue(charge.expired(51));
    }

    @Test void customIdTakesPrecedenceWithinTheRequestedState(){
        var model=Model.read(new StringReader("""
          {"version":8,"name":"item_rules","parts":[
            {"id":"hand","type":"item","heldItem":"left","position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}],
           "animations":{
             "custom":{"duration":1,"loop":false,"tracks":{},"itemUse":{"state":"charge","items":["test:bow"]}},
             "vanilla":{"duration":1,"loop":false,"tracks":{},"itemUse":{"state":"charge","items":["minecraft:bow"]}},
             "fallback":{"duration":1,"loop":false,"tracks":{},"itemUse":{"state":"charge","items":["#bow"]}},
             "release":{"duration":1,"loop":false,"tracks":{},"itemUse":{"state":"use","items":["test:bow"]}}
           }}
          """));
        assertEquals("left",model.parts().getFirst().heldItem());
        assertEquals("custom",ItemAction.select(model,"charge","BOW","test:bow"));
        assertEquals("vanilla",ItemAction.select(model,"charge","BOW","test:other_bow"));
        assertEquals("release",ItemAction.select(model,"use","BOW","test:bow"));
        assertNull(ItemAction.select(model,"hold","BOW","test:bow"));
    }
}
