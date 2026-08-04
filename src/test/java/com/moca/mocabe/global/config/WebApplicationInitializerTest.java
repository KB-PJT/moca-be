package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class WebApplicationInitializerTest {

    private final WebApplicationInitializer initializer = new WebApplicationInitializer();

    @Test
    void registersSpringMvcConfiguration() {
        assertArrayEquals(
                new Class<?>[]{PersistenceConfig.class, RedisConfig.class, AuthConfig.class, LocalTestAuthConfig.class,
                        AppConfig.class},
                initializer.getRootConfigClasses()
        );
        assertArrayEquals(
                new Class<?>[]{WebMvcConfig.class},
                initializer.getServletConfigClasses()
        );
        assertArrayEquals(new String[]{"/"}, initializer.getServletMappings());
    }
}
