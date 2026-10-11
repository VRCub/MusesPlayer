package com.puddingkc.muses.world;

import org.joml.Vector3f;

public final class Raycast {
    private Raycast() {
    }

    public static Hit test(Vector3f start, Vector3f end, Stage stage) {
        Hit hit = new Hit();
        Vector3f a = stage.c1;
        Vector3f b = stage.c2;
        Vector3f c = stage.c3;
        Vector3f d = stage.c4;
        Vector3f face = unitNormal(a, b, c);
        Vector3f ray = new Vector3f();
        end.sub(start, ray);
        float reach = ray.length();
        ray.normalize();
        float facing = face.dot(ray);
        if (facing >= -1.0e-6f) return hit;
        float along = (face.dot(a) - face.dot(start)) / facing;
        if (along < 0.0f || along > reach) return hit;
        hit.point.set(ray).mul(along).add(start);
        if (!inTriangle(hit.point, a, b, d) && !inTriangle(hit.point, b, c, d)) return hit;
        hit.intersects = true;
        hit.distance = along;
        hit.stage = stage;
        return hit;
    }

    private static Vector3f unitNormal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f down = new Vector3f();
        Vector3f across = new Vector3f();
        b.sub(a, down);
        c.sub(a, across);
        Vector3f face = new Vector3f();
        down.cross(across, face);
        face.normalize();
        return face;
    }

    private static boolean inTriangle(Vector3f p, Vector3f a, Vector3f b, Vector3f c) {
        Vector3f ab = new Vector3f(b).sub(a);
        Vector3f ac = new Vector3f(c).sub(a);
        Vector3f n = new Vector3f(ab).cross(ac);
        if (n.lengthSquared() == 0.0f) return false;
        return sameSide(a, b, p, n) && sameSide(b, c, p, n) && sameSide(c, a, p, n);
    }

    private static boolean sameSide(Vector3f from, Vector3f to, Vector3f p, Vector3f n) {
        Vector3f edge = new Vector3f(to).sub(from);
        Vector3f rel = new Vector3f(p).sub(from);
        return new Vector3f(edge).cross(rel).dot(n) >= 0.0f;
    }

    public static final class Hit {
        public boolean intersects = false;
        public final Vector3f point = new Vector3f();
        public float distance = 0f;
        public Stage stage = null;
    }
}
