package com.puddingkc.muses.media;

import com.puddingkc.muses.world.Stage;

public final class Projectors {
    private Projectors() {
    }

    public static Projector from(Stage stage, Projector old) {
        boolean sphere = stage.kind() == Stage.Kind.SPHERE;
        Class<?> want = sphere ? SphereProjector.class : FlatProjector.class;
        if (old != null && old.getClass() == want) {
            return old;
        }
        if (sphere) {
            return new SphereProjector(stage);
        }
        return new FlatProjector(stage);
    }
}
