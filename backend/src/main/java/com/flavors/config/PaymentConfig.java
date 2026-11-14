package com.flavors.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the payment settings (WhishProperties). Which provider adapter runs is decided by flavors.whish.mode. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WhishProperties.class)
public class PaymentConfig {
}
