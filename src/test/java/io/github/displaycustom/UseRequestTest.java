package io.github.displaycustom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UseRequestTest {
 @Test void optionalPlayerRequiresSeparatePermissionEvenForSelf(){
  assertNull(UseRequest.target(new String[]{"use","demo"},true,false));
  assertThrows(IllegalArgumentException.class,()->UseRequest.target(new String[]{"use","demo","Alex"},true,false));
  assertEquals("Alex",UseRequest.target(new String[]{"use","demo","Alex"},true,true));
 }
 @Test void consoleMustSpecifyPlayerAndSelectorsAndExtraArgumentsAreRejected(){
  assertThrows(IllegalArgumentException.class,()->UseRequest.target(new String[]{"use","demo"},false,true));
  assertEquals("Alex",UseRequest.target(new String[]{"use","demo","Alex"},false,true));
  assertThrows(IllegalArgumentException.class,()->UseRequest.target(new String[]{"use","demo","@a"},false,true));
  assertThrows(IllegalArgumentException.class,()->UseRequest.target(new String[]{"use","demo","Alex","extra"},true,true));
 }
}
