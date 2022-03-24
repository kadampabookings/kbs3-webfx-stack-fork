// File managed by WebFX (DO NOT EDIT MANUALLY)

module webfx.platform.vertx.scheduler.impl {

    // Direct dependencies modules
    requires io.vertx.core;
    requires webfx.platform.shared.scheduler;
    requires webfx.platform.vertx.instance;

    // Exported packages
    exports dev.webfx.platform.vertx.services.scheduler.spi.impl;

    // Provided services
    provides dev.webfx.platform.shared.services.scheduler.spi.SchedulerProvider with dev.webfx.platform.vertx.services.scheduler.spi.impl.VertxSchedulerProvider;

}