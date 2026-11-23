package com.financialcoach.transactions;

import java.io.IOException;
import java.io.Writer;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes transactions in the same format the import reads ({@link CsvTransactionReader#HEADER}),
 * so an exported file can be imported again. Amounts follow the bank convention (expenses
 * negative, income positive): summing the column gives the net balance, and on import Type decides
 * the sign anyway.
 */
final class CsvTransactionWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader(CsvTransactionReader.HEADER.toArray(String[]::new))
            .get();
    /** Lets Excel detect UTF-8 (accents); the import skips it. */
    private static final char BYTE_ORDER_MARK = '﻿';

    private final CSVPrinter printer;

    /** Writes the byte order mark and the header right away. */
    CsvTransactionWriter(Writer out) throws IOException {
        out.write(BYTE_ORDER_MARK);
        this.printer = new CSVPrinter(out, FORMAT);
    }

    void write(CsvExportRow row, String accountName) throws IOException {
        printer.printRecord(
                row.type() == TransactionType.EXPENSE ? "Expense" : "Income",
                DATE.format(row.occurredAt()),
                TIME.format(row.occurredAt()),
                CsvFormulas.escape(row.name()),
                // Stored with the API sign (EXPENSE positive, INCOME negative): flip it.
                row.amount().negate().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                row.categoryGroup().label(),
                CsvFormulas.escape(row.category()),
                CsvFormulas.escape(accountName),
                CsvFormulas.escape(row.notes()));
    }

    void flush() throws IOException {
        printer.flush();
    }
}
