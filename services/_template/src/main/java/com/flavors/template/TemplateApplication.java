package com.flavors.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Rename the package and this class when you copy the template (services/README.md). The platform library
 * ({@code com.flavors.platform}) wires itself in through auto-configuration; nothing to import here.
 */
@SpringBootApplication
public class TemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateApplication.class, args);
    }
}
