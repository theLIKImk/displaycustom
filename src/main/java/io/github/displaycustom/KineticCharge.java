package io.github.displaycustom;

import io.papermc.paper.datacomponent.item.KineticWeapon;

/** Maps each native spear stage to one third of the authored charge clip. */
record KineticCharge(int preparation, int engagedEnd, int tiredEnd, int end) {
    static KineticCharge from(KineticWeapon weapon) {
        int preparation=weapon.delayTicks();
        int damage=duration(weapon.damageConditions());
        int engaged=Math.min(damage,duration(weapon.dismountConditions()));
        int tired=Math.max(engaged,Math.min(damage,duration(weapon.knockbackConditions())));
        return new KineticCharge(preparation,preparation+engaged,preparation+tired,preparation+damage);
    }

    boolean expired(int elapsed) {return elapsed>end;}

    double sample(int elapsed,float clipDuration) {
        double third=clipDuration/3.0;
        if(elapsed<=engagedEnd&&engagedEnd>0)return third*Math.max(0,elapsed)/engagedEnd;
        if(elapsed<=tiredEnd&&tiredEnd>engagedEnd)return third*(1+(double)(elapsed-engagedEnd)/(tiredEnd-engagedEnd));
        if(end>tiredEnd)return third*(2+Math.clamp((double)(elapsed-tiredEnd)/(end-tiredEnd),0,1));
        return clipDuration;
    }

    private static int duration(KineticWeapon.Condition condition) {
        return condition==null?0:condition.maxDurationTicks();
    }
}
