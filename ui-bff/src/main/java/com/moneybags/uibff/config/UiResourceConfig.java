package com.moneybags.uibff.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UiResourceConfig implements WebMvcConfigurer {
    private final BffProperties properties;

    public UiResourceConfig(BffProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        location(properties.uiStaticLocation()),
                        location("moneybags-ui/web"),
                        location("../moneybags-ui/web"),
                        "classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    private static String location(String value) {
        String location = Path.of(value).toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
