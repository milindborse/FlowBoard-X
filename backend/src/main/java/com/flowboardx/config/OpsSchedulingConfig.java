package com.flowboardx.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled methods for the new Queue Ops metrics ticker (OpsMetricsScheduler).
 * If your existing SchedulerConfig.java already has @EnableScheduling, this is redundant
 * but harmless - Spring tolerates it being declared on more than one config class.
 */
@Configuration
@EnableScheduling
public class OpsSchedulingConfig {
}