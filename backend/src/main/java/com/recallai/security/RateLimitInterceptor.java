package com.recallai.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies {@link RateLimitService} to the routes it is registered for (the AI endpoints).
 * Runs after authentication, so the user is always known; CORS preflights and non-handler
 * requests are ignored.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    static final String AI_PATH_PREFIX = "/api/ai/";

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (!request.getRequestURI().startsWith(AI_PATH_PREFIX) && !method.hasMethodAnnotation(AiRateLimited.class)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return true;
        }
        RateLimitService.Decision decision = rateLimitService.tryConsume(user.id());
        response.setHeader(LIMIT_HEADER, String.valueOf(decision.limit()));
        response.setHeader(REMAINING_HEADER, String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            log.info("Rate limit exceeded for user {} on {}", user.id(), request.getRequestURI());
            throw new RateLimitExceededException(decision.limit(), decision.retryAfterSeconds());
        }
        return true;
    }
}
