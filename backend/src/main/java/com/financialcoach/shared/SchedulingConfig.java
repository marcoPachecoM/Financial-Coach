package com.financialcoach.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} methods (e.g. the generator of recurring transactions). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
