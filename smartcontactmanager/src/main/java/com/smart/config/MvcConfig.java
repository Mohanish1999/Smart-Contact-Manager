package com.smart.config;

import java.nio.file.Paths;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /** Same folder FileStorageService writes to (app.upload-dir). */
    @Value("${app.upload-dir:user-photos}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        // Photos are only served to logged-in users (see MyConfig: anyRequest().authenticated()).
        registry.addResourceHandler("/user-photos/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate());
    }
}
