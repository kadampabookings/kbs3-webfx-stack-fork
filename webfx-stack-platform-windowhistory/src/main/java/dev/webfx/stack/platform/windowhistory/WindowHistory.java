package dev.webfx.stack.platform.windowhistory;

import dev.webfx.stack.platform.windowhistory.spi.BrowsingHistoryLocation;
import dev.webfx.stack.platform.windowhistory.spi.WindowHistoryProvider;
import dev.webfx.platform.util.serviceloader.SingleServiceProvider;

import java.util.ServiceLoader;

/**
 * @author Bruno Salmon
 */
public final class WindowHistory {

    public static WindowHistoryProvider getProvider() { // returns the browser history
        return SingleServiceProvider.getProvider(WindowHistoryProvider.class, () -> ServiceLoader.load(WindowHistoryProvider.class));
    }

    /**
     *
     * @return the current location
     */
    public static BrowsingHistoryLocation getCurrentLocation() {
        return getProvider().getCurrentLocation();
    }

}
