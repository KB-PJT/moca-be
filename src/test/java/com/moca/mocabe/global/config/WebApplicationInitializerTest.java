package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WebApplicationInitializerTest {

    private final WebApplicationInitializer initializer = new WebApplicationInitializer();

    @Test
    void registersSpringMvcConfiguration() {
        assertNull(initializer.getRootConfigClasses());
        assertArrayEquals(
                new Class<?>[]{WebMvcConfig.class},
                initializer.getServletConfigClasses()
        );
        assertArrayEquals(new String[]{"/"}, initializer.getServletMappings());
    }
}
