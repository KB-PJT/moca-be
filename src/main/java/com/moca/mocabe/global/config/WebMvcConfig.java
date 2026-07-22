package com.moca.mocabe.global.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.moca.mocabe")
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String SWAGGER_ASSET_LOCATION =
            "classpath:/META-INF/resources/webjars/swagger-ui/5.32.8/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
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
    }
}
