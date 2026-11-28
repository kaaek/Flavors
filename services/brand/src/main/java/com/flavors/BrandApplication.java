package com.flavors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The brand service (EEC-95, Phase 1b): brand profile, branches, saved creators and brand search, moved out of
 * the monolith without behaviour changes. Package names are those of the monolith ({@code com.flavors.*}).
 *
 * <p>The packages are listed instead of scanning {@code com.flavors}, because the platform library
 * ({@code com.flavors.platform}) wires itself in through auto-configuration and must not be scanned twice.
 * Entities and Spring Data repositories are found from this class's package, as usual.
 */
@SpringBootApplication(scanBasePackages = {
        "com.flavors.controller", "com.flavors.service", "com.flavors.mapper",
        "com.flavors.repository", "com.flavors.exception", "com.flavors.client"})
public class BrandApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrandApplication.class, args);
    }
}
