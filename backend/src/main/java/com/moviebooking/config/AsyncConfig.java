package com.moviebooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async (used by EmailService). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
