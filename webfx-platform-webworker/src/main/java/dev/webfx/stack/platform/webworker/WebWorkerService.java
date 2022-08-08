package dev.webfx.stack.platform.webworker;

import dev.webfx.stack.platform.webworker.spi.WorkerServiceProvider;
import dev.webfx.stack.platform.webworker.spi.base.JavaCodedWebWorkerBase;
import dev.webfx.platform.util.serviceloader.SingleServiceProvider;

import java.util.ServiceLoader;

/**
 * @author Bruno Salmon
 */
public class WebWorkerService {

    public static WorkerServiceProvider getProvider() {
        return SingleServiceProvider.getProvider(WorkerServiceProvider.class, () -> ServiceLoader.load(WorkerServiceProvider.class));
    }

    public static WebWorker createWorker(String scriptUrl) {
        return getProvider().createWorker(scriptUrl);
    }

    public static WebWorker createWorker(Class<? extends JavaCodedWebWorkerBase> javaCodedWorkerClass) {
        return getProvider().createWorker(javaCodedWorkerClass);
    }

}
