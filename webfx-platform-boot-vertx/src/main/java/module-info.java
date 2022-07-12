// File managed by WebFX (DO NOT EDIT MANUALLY)

module webfx.platform.boot.vertx {

    // Direct dependencies modules
    requires io.vertx.core;
    requires io.vertx.eventbusbridge.common;
    requires io.vertx.web;
    requires java.base;
    requires webfx.platform.boot;
    requires webfx.platform.shutdown;
    requires webfx.stack.vertx.common;

    // Exported packages
    exports dev.webfx.platform.boot.spi.impl.vertx;

    // Provided services
    provides dev.webfx.platform.boot.spi.ApplicationBooterProvider with dev.webfx.platform.boot.spi.impl.vertx.VertxApplicationBooterVerticle;

}