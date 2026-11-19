package com.saferoute.ai.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.saferoute.ai.service.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserService currentUserService;

    public WebConfig(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /**
     * Second line of defence: private API paths are rejected before reaching a controller when nobody
     * is signed in. (Services also check, via CurrentUserService.)
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SignedInInterceptor(currentUserService))
                .addPathPatterns("/api/emergency-contacts/**", "/api/routes/**", "/api/trips/**", "/api/alerts/**");
    }

    static class SignedInInterceptor implements HandlerInterceptor {
        private final CurrentUserService currentUserService;

        SignedInInterceptor(CurrentUserService currentUserService) {
            this.currentUserService = currentUserService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            // A session, or a valid "remember me" cookie (which re-creates the session after a server restart).
            if (currentUserService.optionalUserId().isPresent()) {
                return true;
            }
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Please sign in to continue.\",\"fieldErrors\":{}}");
            return false;
        }
    }

    /** Public live-tracking page that emergency contacts open from their alert. */
    @Controller
    static class TrackingPageController {
        @GetMapping("/track/{token}")
        public String trackingPage(@PathVariable String token) {
            return "forward:/track.html";
        }
    }
}
