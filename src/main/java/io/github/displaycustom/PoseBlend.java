package io.github.displaycustom;

import org.joml.*;

/** Smoothly blend display transformations, preserving rotation and scale. */
final class PoseBlend {
    static Matrix4f blend(Matrix4f from,Matrix4f to,float weight){
        float t=java.lang.Math.max(0,java.lang.Math.min(1,weight));
        Vector3f p=from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()),t);
        Vector3f a=from.getScale(new Vector3f()),b=to.getScale(new Vector3f());
        if(a.x<1e-6f||a.y<1e-6f||a.z<1e-6f||b.x<1e-6f||b.y<1e-6f||b.z<1e-6f)return new Matrix4f(to);
        Quaternionf q=from.getUnnormalizedRotation(new Quaternionf()).normalize().slerp(to.getUnnormalizedRotation(new Quaternionf()).normalize(),t);
        return new Matrix4f().translationRotateScale(p,q,a.lerp(b,t));
    }
}
