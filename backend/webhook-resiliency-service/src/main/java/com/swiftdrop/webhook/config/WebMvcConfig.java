package com.swiftdrop.webhook.config;

import com.swiftdrop.contracts.security.CallerContext;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    static {
        // The caller is resolved from gateway headers, not a request parameter; keep it out of the API document.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CallerContext.class);
    }

    private final CallerContextArgumentResolver callerContextArgumentResolver;

    public WebMvcConfig(CallerContextArgumentResolver callerContextArgumentResolver) {
        this.callerContextArgumentResolver = callerContextArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(callerContextArgumentResolver);
    }
}
