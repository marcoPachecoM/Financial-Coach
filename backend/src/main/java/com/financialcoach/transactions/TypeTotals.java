package com.financialcoach.transactions;

import java.math.BigDecimal;

/** Raw aggregate of one transaction type (query result). {@code sum} keeps the stored sign. */
record TypeTotals(TransactionType type, Long count, BigDecimal sum, BigDecimal largest) {
}
