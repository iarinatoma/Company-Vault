package com.example.template.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API versioning is resolved by Framework 7's native support, not hand-maintained path
 * prefixes: the {@code v{n}} path segment is the version source, the supported set is closed,
 * and mappings carry the {@code version} attribute. A breaking change bumps the version on the
 * affected endpoints only.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(1) // /api/{version}/... — segment index 1 carries "v1"
                .addSupportedVersions("1")
                .setDefaultVersion("1");
    }
}
