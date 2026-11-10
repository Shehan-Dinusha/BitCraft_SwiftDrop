package com.swiftdrop.webhook.config;

import com.swiftdrop.contracts.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Supplies a {@link CallerContext} controller parameter from the identity headers the API gateway injects after
 * verifying the JWT ({@code X-User-Id}, {@code X-User-Role}, {@code X-Merchant-Id}, {@code X-District-Id}).
 *
 * <p>Trust boundary: the gateway strips client-supplied copies of these headers on every authenticated route.
 * They are only trustworthy if the service is reachable solely through the gateway, which is a deployment
 * property (see the API specification), not something this class can verify. Missing or malformed headers
 * raise {@code UnauthenticatedCallerException} (401).</p>
 */
@Component
public class CallerContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CallerContext.class.equals(parameter.getParameterType());
    }

    @Override
    public CallerContext resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                         NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return CallerContext.from(name -> null);
        }
        return CallerContext.from(request::getHeader);
    }
}
