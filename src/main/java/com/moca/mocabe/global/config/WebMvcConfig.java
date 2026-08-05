package com.moca.mocabe.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

@Configuration
@EnableWebMvc
@ComponentScan(
        basePackages = "com.moca.mocabe",
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class),
        useDefaultFilters = false
)
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String API_V1_PREFIX = "/api/v1";
    private static final String SWAGGER_ASSET_LOCATION =
            "classpath:/META-INF/resources/webjars/swagger-ui/5.32.8/";
    private static final String[] ALLOWED_ORIGINS = {
            "http://localhost:5173",
            "https://moca-fe-rho.vercel.app"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_V1_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/swagger-ui/");
        registry.addResourceHandler("/docs/**")
                .addResourceLocations("classpath:/swagger-ui/");
        registry.addResourceHandler("/swagger-assets/**")
                .addResourceLocations(SWAGGER_ASSET_LOCATION);
        registry.addResourceHandler("/api-docs/**")
                .addResourceLocations("classpath:/openapi/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(
                "/swagger-ui",
                "/swagger-ui/index.html"
        );
        registry.addRedirectViewController("/docs", "/docs/index.html");
    }
}
