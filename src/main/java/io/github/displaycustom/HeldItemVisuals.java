package io.github.displaycustom;

import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.joml.Matrix4f;

final class HeldItemVisuals {
    private static final float HALF_TURN = (float) Math.PI;
    private static final float DEG = HALF_TURN / 180;

    // Paper 26.3 client display contexts, named by physical hand rather than inventory slot.
    static ItemDisplayTransform context(boolean left) {
        return left ? ItemDisplayTransform.THIRDPERSON_LEFTHAND : ItemDisplayTransform.THIRDPERSON_RIGHTHAND;
    }

    // Vanilla uses the same special geometry for each pair. Only the native hand transform changes.
    private static final Matrix4f LEFT_BLOCK = correction(
        pose(true, 10, 6, 12, 0, 90, 0), pose(true, 11.51f, 7, 2.5f, 45, 155, 0));
    private static final Matrix4f RIGHT_BLOCK = correction(
        pose(false, 10, 6, -4, 0, 90, 0), pose(false, -3.49f, 11, -2, 45, 155, 0));
    private static final Matrix4f LEFT_THROW = correction(
        pose(true, 3, 17, 12, 0, 60, 0), pose(true, 8, -17, -7, 0, 90, 180));
    private static final Matrix4f RIGHT_THROW = correction(
        pose(false, 11, 17, -2, 0, 60, 0), pose(false, 8, -17, 9, 0, 90, 180));

    private HeldItemVisuals() {}

    static void applyUsePose(Matrix4f matrix, Material material, boolean left) {
        switch (material) {
            case SHIELD -> matrix.mul(left ? LEFT_BLOCK : RIGHT_BLOCK);
            case TRIDENT -> matrix.mul(left ? LEFT_THROW : RIGHT_THROW);
            default -> { }
        }
    }

    private static Matrix4f pose(boolean left, float x, float y, float z, float rx, float ry, float rz) {
        int side = left ? -1 : 1;
        return new Matrix4f().translation(side * x / 16, y / 16, z / 16)
            .rotateXYZ(rx * DEG, side * ry * DEG, side * rz * DEG);
    }

    private static Matrix4f correction(Matrix4f held, Matrix4f used) {
        // ItemDisplay inserts a Y half-turn before the client applies the default item transform.
        return new Matrix4f().rotationY(HALF_TURN).mul(used).mul(held.invert()).rotateY(-HALF_TURN);
    }
}
