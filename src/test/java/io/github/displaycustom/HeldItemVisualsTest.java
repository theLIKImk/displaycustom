package io.github.displaycustom;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HeldItemVisualsTest {
    private static final float HALF_TURN = (float) Math.PI;

    @Test void swordHandleFitsInsideBothWhaleGirlHands() throws Exception {
        try (var reader = new InputStreamReader(
                getClass().getResourceAsStream("/models/whale_girl.json"), StandardCharsets.UTF_8)) {
            Model whale = Model.read(reader).validate();
            for (String side : List.of("left", "right")) {
                var palm = whale.parts().stream().filter(p -> p.id().equals(side + "_hand")).findFirst().orElseThrow();
                var socket = whale.parts().stream().filter(p -> side.equals(p.heldItem())).findFirst().orElseThrow();
                assertEquals(palm.group(), socket.group());
                // Vanilla handheld.json. The mirrored left context resolves to the same transform for swords.
                var transform = Model.matrix(socket, 0, false).rotateY(HALF_TURN)
                    .translate(0, 4f / 16, .5f / 16).rotateY(-HALF_TURN / 2)
                    .rotateZ(55 * HALF_TURN / 180).scale(.85f);
                // Centers of the handle and tip pixels in the vanilla 16x16 diamond sword sprite.
                var grip = transform.transformPosition(new Vector3f(3.5f / 16 - .5f, .5f - 12.5f / 16, 0));
                var tip = transform.transformPosition(new Vector3f(14.5f / 16 - .5f, .5f - 1.5f / 16, 0));
                for (int axis = 0; axis < 3; axis++)
                    assertTrue(Math.abs(grip.get(axis) - palm.position()[axis]) < palm.scale()[axis] / 2, side + " " + grip);
                assertTrue(tip.z > palm.position()[2] + palm.scale()[2] / 2);
            }
        }
    }

    @Test void nativeUsePoseReplacesDefaultHandTransform() {
        assertEquals(ItemDisplayTransform.THIRDPERSON_LEFTHAND, HeldItemVisuals.context(true));
        assertEquals(ItemDisplayTransform.THIRDPERSON_RIGHTHAND, HeldItemVisuals.context(false));
        // Test the final render origin after the client applies the ordinary held model translation.
        assertUseOrigin(Material.SHIELD, false, 10, 6, -4, -3.49f, 11, -2);
        assertUseOrigin(Material.SHIELD, true, -10, 6, 12, -11.51f, 7, 2.5f);
        assertUseOrigin(Material.TRIDENT, false, 11, 17, -2, 8, -17, 9);
        assertUseOrigin(Material.TRIDENT, true, -3, 17, 12, -8, -17, -7);
    }

    private static void assertUseOrigin(Material item, boolean left, float x, float y, float z,
                                        float targetX, float targetY, float targetZ) {
        Matrix4f applied = new Matrix4f();
        HeldItemVisuals.applyUsePose(applied, item, left);
        var center = applied.rotateY(HALF_TURN).transformPosition(new Vector3f(x / 16, y / 16, z / 16));
        assertTrue(center.equals(new Vector3f(-targetX / 16, targetY / 16, -targetZ / 16), 1e-5f));
    }
}
