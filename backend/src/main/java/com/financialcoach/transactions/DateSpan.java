package com.financialcoach.transactions;

import java.time.LocalDateTime;

/** Datetimes of the first and the last transaction in a period; both null when there are none. */
public record DateSpan(LocalDateTime first, LocalDateTime last) {
}
