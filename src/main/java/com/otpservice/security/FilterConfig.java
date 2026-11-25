package com.otpservice.security;

import com.otpservice.app.ApiKeyRepository;
import com.otpservice.app.ClientAppRepository;
import com.otpservice.config.OtpProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Which filter guards which paths. Anything not under /v1 or /admin is only /health. */
@Configuration
class FilterConfig {

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ApiKeyRepository keys, ClientAppRepository apps) {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(new ApiKeyFilter(keys, apps));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(10);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(OtpProperties properties) {
        FilterRegistrationBean<AdminTokenFilter> registration =
                new FilterRegistrationBean<>(new AdminTokenFilter(properties.adminToken()));
        registration.addUrlPatterns("/admin/*");
        registration.setOrder(10);
        return registration;
    }
}
