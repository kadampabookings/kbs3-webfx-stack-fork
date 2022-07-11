package dev.webfx.stack.vertx.common;

import io.vertx.core.Vertx;

/**
 * @author Bruno Salmon
 */
public final class VertxInstance {

    private static Vertx VERTX;

    public static void setVertx(Vertx vertx) {
        VERTX = vertx;
    }

    public static Vertx getVertx() {
        return VERTX;
    }
}
