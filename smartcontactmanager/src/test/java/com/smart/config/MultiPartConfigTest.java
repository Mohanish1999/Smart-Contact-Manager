package com.smart.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

class MultiPartConfigTest {

    @Test
    void testMultipartResolver() {

        MultiPartConfig config = new MultiPartConfig();

        MultipartResolver resolver = config.multipartResolver();

        assertNotNull(resolver);
        assertTrue(resolver instanceof StandardServletMultipartResolver);
    }
}