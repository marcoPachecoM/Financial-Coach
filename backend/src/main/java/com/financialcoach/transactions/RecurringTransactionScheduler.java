package com.financialcoach.transactions;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates the due occurrences of recurring transactions once a day, and once at startup to
 * catch up after the application was stopped. Safe to run on several instances at once
 * (see RecurringTransactionRepository#lockIfDue). Disabled in tests, which call the service
 * with fixed dates.
 */
@Component
@ConditionalOnProperty(prefix = "financial-coach.recurring", name = "scheduler-enabled",
        havingValue = "true", matchIfMissing = true)
class RecurringTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionScheduler.class);

    private final RecurringTransactionService service;
    private final Clock clock;

    RecurringTransactionScheduler(RecurringTransactionService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${financial-coach.recurring.cron:0 5 0 * * *}")
    void generateDue() {
        int processed = service.generateDue(LocalDate.now(clock));
        if (processed > 0) {
            log.info("Generated the due occurrences of {} recurring transaction(s)", processed);
        }
    }
}
