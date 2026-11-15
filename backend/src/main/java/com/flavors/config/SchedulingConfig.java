package com.flavors.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} methods. The scheduled jobs are listed in docs/Architecture.md (sections 13, 16 and 17).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
