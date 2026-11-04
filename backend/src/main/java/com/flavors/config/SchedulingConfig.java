package com.flavors.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} methods. The only one so far is the daily social refresh (EEC-37). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
