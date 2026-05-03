package com.artfetch.auth.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.artfetch.auth.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final CurrentUserService currentUserService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/api/**")
                        .notMatch("/api/auth/login")
                        .check(r -> {
                            StpUtil.checkLogin();
                            currentUserService.requireEnabledCurrentUser();
                        })))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.svg",
                        "/favicon.ico",
                        "/error",
                        "/actuator/health"
                );
    }
}
