package com.smart.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class MvcConfigTest {

    @Test
    void testMvcConfig() {

        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(MvcConfig.class);

        MvcConfig config = context.getBean(MvcConfig.class);

        assertNotNull(config);
        assertTrue(config instanceof WebMvcConfigurer);

        context.close();
    }
}