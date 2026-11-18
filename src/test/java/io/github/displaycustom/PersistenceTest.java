package io.github.displaycustom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.joml.*;

class PersistenceTest {
 @TempDir Path temp;
 @Test void savesAndRestoresPositionsPlayersAndPlayback() throws Exception {
  Path file=temp.resolve("instances.json");var store=new PersistentModels(file);UUID world=UUID.randomUUID(),player=UUID.randomUUID();
  var p=new PersistentModels.Placed("a1b2c3d4","demo",world,12.25,-5.5,100.75,123,0,15,-20,"walk",1.5,true);
  store.placed.put(p.id(),p);store.players.put(player,"deepseek_maid_player");store.save();
  var restored=new PersistentModels(file);restored.load();assertEquals(p,restored.placed.get(p.id()));assertEquals("deepseek_maid_player",restored.players.get(player));
  restored.placed.clear();restored.players.clear();restored.save();var empty=new PersistentModels(file);empty.load();assertTrue(empty.placed.isEmpty());assertTrue(empty.players.isEmpty());
 }
 @Test void corruptedFileIsRejectedWithoutOverwritingIt() throws Exception {
  Path file=temp.resolve("instances.json");Files.writeString(file,"{not json");assertThrows(java.io.IOException.class,()->new PersistentModels(file).load());assertEquals("{not json",Files.readString(file));
 }
 @Test void blendInterpolatesTranslationRotationAndScaleWithoutChangingEndpoints(){
  var a=new Matrix4f().translation(0,0,0).rotateY(0).scale(2);var b=new Matrix4f().translation(4,0,0).rotateY((float)java.lang.Math.PI).scale(4);
  var middle=PoseBlend.blend(a,b,.5f);assertEquals(2,middle.getTranslation(new Vector3f()).x,1e-5);assertEquals(3,middle.getScale(new Vector3f()).x,1e-5);
  assertEquals(3,middle.transformDirection(new Vector3f(1,0,0)).length(),1e-5);assertTrue(PoseBlend.blend(a,b,0).equals(a,1e-5f));assertTrue(PoseBlend.blend(a,b,1).equals(b,1e-5f));
 }
}
