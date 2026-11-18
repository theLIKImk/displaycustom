package io.github.displaycustom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelfBodyVisibilityTest {
    @Test void overridesOnlyInvisibilityForEveryCombinationOfSharedFlags(){
        for(int value=0;value<256;value++){
            byte actual=(byte)value;
            assertEquals(value&~0x20,Byte.toUnsignedInt(SelfBodyVisibility.flags(actual,false)));
            assertEquals(value|0x20,Byte.toUnsignedInt(SelfBodyVisibility.flags(actual,true)));
        }
    }
}
