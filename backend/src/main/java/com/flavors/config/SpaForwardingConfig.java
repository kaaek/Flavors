package com.flavors.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app from {@code static/} (the Dockerfile copies it there) and makes
 * client-side routes survive a page reload: opening or refreshing {@code /brand/campaigns} has no
 * file behind it, so the server answers with {@code index.html} and React Router takes over.
 * See docs/Architecture.md §10.
 *
 * <p>Done as a fallback inside the static-file handler rather than a {@code @GetMapping} controller,
 * because controllers are matched before static files, so a catch-all one would also swallow
 * {@code /assets/app.js}. Here a real file always wins.
 */
@Configuration(proxyBeanMethods = false)
class SpaForwardingConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource file = location.createRelative(resourcePath);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        // A missing API path or a missing file (it has an extension) must stay a 404.
                        // Anything else is a screen of the React app.
                        boolean isSpaRoute = !resourcePath.startsWith("api/") && !resourcePath.startsWith("actuator/")
                                && !resourcePath.contains(".");
                        return isSpaRoute ? location.createRelative("index.html") : null;
                    }
                });
    }
}
