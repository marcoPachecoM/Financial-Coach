package com.financialcoach.shared;

import com.financialcoach.shared.error.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * A period of transaction datetimes: {@code from} inclusive, {@code to} exclusive.
 * Built from the {@code start_date} / {@code end_date} query parameters (both inclusive and
 * optional) or from a calendar month.
 */
public record DateRange(LocalDateTime from, LocalDateTime to) {

    /** Used when a date filter is not given (PostgreSQL's timestamp range is wider). */
    private static final LocalDateTime EARLIEST = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    /**
     * {@code end_date} covers the whole day. Fails with VALIDATION_ERROR on {@code end_date}
     * when it is before {@code start_date}.
     */
    public static DateRange between(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ValidationException("end_date", "must be on or after start_date");
        }
        LocalDateTime from = startDate == null ? EARLIEST : startDate.atStartOfDay();
        LocalDateTime to = endDate == null ? LATEST : endDate.plusDays(1).atStartOfDay();
        return new DateRange(from, to);
    }

    public static DateRange of(YearMonth month) {
        return new DateRange(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }
}
