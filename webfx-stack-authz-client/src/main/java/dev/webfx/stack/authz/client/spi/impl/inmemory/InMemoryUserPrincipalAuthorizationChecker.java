package dev.webfx.stack.authz.client.spi.impl.inmemory;

import dev.webfx.stack.authz.client.spi.impl.UserPrincipalAuthorizationChecker;
import dev.webfx.platform.async.AsyncResult;
import dev.webfx.platform.async.Future;
import dev.webfx.platform.async.FutureBroadcaster;
import dev.webfx.platform.async.Handler;

/**
 * @author Bruno Salmon
 */
public class InMemoryUserPrincipalAuthorizationChecker implements UserPrincipalAuthorizationChecker {

    private final Object userPrincipal;
    protected final InMemoryAuthorizationRuleRegistry ruleRegistry;
    private FutureBroadcaster<?> rulesLoadingBroadcaster;

    public InMemoryUserPrincipalAuthorizationChecker(Object userPrincipal) {
        this(userPrincipal, new InMemoryAuthorizationRuleRegistry());
    }

    public InMemoryUserPrincipalAuthorizationChecker(Object userPrincipal, InMemoryAuthorizationRuleRegistry ruleRegistry) {
        this.userPrincipal = userPrincipal;
        this.ruleRegistry = ruleRegistry;
    }

    @Override
    public Object getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public Future<Boolean> isAuthorized(Object operationAuthorizationRequest) {
        FutureBroadcaster<?> loader = rulesLoadingBroadcaster;
        return loader == null ? // means the rules are already loaded, so we can evaluate them and return the result immediately
                Future.succeededFuture(ruleRegistry.doesRulesAuthorize(operationAuthorizationRequest))
                // Otherwise, we first need to wait the rules to be loaded and only then we can evaluate them and return the result
                : loader.newClient().compose(result -> Future.succeededFuture(ruleRegistry.doesRulesAuthorize(operationAuthorizationRequest)));
    }

    protected <T> void setUpInMemoryAsyncRulesLoading(Future<T> loadingFuture, Handler<AsyncResult<T>> loadedHandler) {
        FutureBroadcaster<T> broadcaster = new FutureBroadcaster<>(loadingFuture);
        rulesLoadingBroadcaster = broadcaster;
        broadcaster.newClient().onComplete(loadedHandler);
        broadcaster.newClient().onComplete(ar -> rulesLoadingBroadcaster = null);
    }
}
