package com.recallai.config;

import com.recallai.security.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The interceptor sees every API call but only counts the model-backed ones: everything under
     * /api/ai plus handlers marked {@link com.recallai.security.AiRateLimited}.
     */
    static final String[] RATE_LIMITED_PATHS = {"/api/**"};

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(RATE_LIMITED_PATHS);
    }
}
