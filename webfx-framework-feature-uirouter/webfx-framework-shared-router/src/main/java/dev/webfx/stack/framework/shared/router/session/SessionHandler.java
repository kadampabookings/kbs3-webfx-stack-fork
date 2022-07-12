package dev.webfx.stack.framework.shared.router.session;

import dev.webfx.stack.framework.shared.router.RoutingContext;
import dev.webfx.stack.framework.shared.router.session.impl.SessionHandlerImpl;
import dev.webfx.stack.async.Handler;
import dev.webfx.platform.util.function.Callable;
import java.util.function.Consumer;

/**
 * @author Bruno Salmon
 */
public interface SessionHandler extends Handler<RoutingContext> {

    static SessionHandler create(Callable<SessionStore> sessionStoreGetter, Callable<String> sessionIdFetcher, Consumer<String> sessionIdRecorder) {
        return new SessionHandlerImpl(sessionStoreGetter, sessionIdFetcher, sessionIdRecorder);
    }

    static SessionHandler create(SessionStore sessionStore, Callable<String> sessionIdFetcher, Consumer<String> sessionIdRecorder) {
        return create(() -> sessionStore, sessionIdFetcher, sessionIdRecorder);
    }
}
