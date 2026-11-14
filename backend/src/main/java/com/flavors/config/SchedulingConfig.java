package com.flavors.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} methods. The scheduled jobs are the daily social refresh (EEC-37) and the campaign response expiry (EEC-43). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
