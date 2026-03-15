package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import com.enterprise.openfinance.atmdirectory.infrastructure.security.InteractionIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final InteractionIdInterceptor interactionIdInterceptor;

    public WebConfig(InteractionIdInterceptor interactionIdInterceptor) {
        this.interactionIdInterceptor = interactionIdInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interactionIdInterceptor)
                .addPathPatterns("/open-finance/v1/**");
    }
}
