package dev.webfx.stack.framework.shared.services.authz;

import dev.webfx.stack.framework.shared.services.authz.spi.AuthorizationServiceProvider;
import dev.webfx.stack.async.Future;
import dev.webfx.platform.util.serviceloader.SingleServiceProvider;

import java.util.ServiceLoader;

/**
 * @author Bruno Salmon
 */
public final class AuthorizationService {

    public static Future<Boolean> isAuthorized(Object operationAuthorizationRequest, Object userPrincipal) {
        return getProvider().isAuthorized(operationAuthorizationRequest, userPrincipal);
    }

    public static AuthorizationServiceProvider getProvider() {
        return SingleServiceProvider.getProvider(AuthorizationServiceProvider.class, () -> ServiceLoader.load(AuthorizationServiceProvider.class));
    }
}
