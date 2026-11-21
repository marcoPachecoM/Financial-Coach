package com.financialcoach.transactions;

import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.ApiFieldError;
import com.financialcoach.shared.error.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads and validates an uploaded CSV file without touching the database. Either every row is
 * valid and all of them are returned, or a {@link CsvInvalidRowsException} lists the errors of
 * every invalid row (the import is all or nothing). Problems with the file as a whole (encoding,
 * header, broken quoting) are a {@link ValidationException} on the {@code file} field.
 */
final class CsvTransactionReader {

    /** Exact header of the contract, in order. */
    static final List<String> HEADER = List.of(
            "Type", "Date", "Time", "Name", "Amount", "Category Group", "Category", "Account", "Notes");

    /** Caps the response size when a whole file is wrong; the detail still gives the total. */
    static final int MAX_REPORTED_ERRORS = 100;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern SIGNED_AMOUNT = Pattern.compile(Money.SIGNED_PATTERN);

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(true)
            .get();

    private static final char BYTE_ORDER_MARK = '﻿';

    private CsvTransactionReader() {
    }

    static List<CsvRow> read(byte[] content) {
        String text = decodeUtf8(content);
        if (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) {
            text = text.substring(1); // Excel adds it when saving as "CSV UTF-8".
        }
        if (text.isBlank()) {
            throw new ValidationException("file", "must not be empty");
        }

        List<CsvRow> rows = new ArrayList<>();
        List<ApiFieldError> errors = new ArrayList<>();
        int invalidRows = 0;

        try (CSVParser parser = CSVParser.parse(text, FORMAT)) {
            Iterator<CSVRecord> records = parser.iterator();
            checkHeader(records.next());
            int rowNumber = 0;
            while (records.hasNext()) {
                rowNumber++;
                List<ApiFieldError> rowErrors = new ArrayList<>();
                CsvRow row = parseRow(rowNumber, records.next(), rowErrors);
                if (rowErrors.isEmpty()) {
                    rows.add(row);
                } else {
                    invalidRows++;
                    errors.addAll(rowErrors);
                }
            }
        } catch (UncheckedIOException | IOException e) {
            // Commons CSV reports broken quoting (e.g. a quote that is never closed) this way.
            throw new ValidationException("file", "is not a valid CSV file (check the quotes).");
        }

        if (invalidRows > 0) {
            throw new CsvInvalidRowsException(describe(invalidRows, errors.size()),
                    errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
        }
        return rows;
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ValidationException("file", "must be UTF-8 encoded.");
        }
    }

    private static void checkHeader(CSVRecord header) {
        List<String> names = header.stream().map(String::strip).toList();
        boolean matches = names.size() == HEADER.size()
                && IntStream.range(0, HEADER.size())
                        .allMatch(i -> HEADER.get(i).equalsIgnoreCase(names.get(i)));
        if (!matches) {
            throw new ValidationException("file", "header must be: " + String.join(", ", HEADER));
        }
    }

    /** Collects every problem of the row in {@code errors}; the returned row is only valid if none. */
    private static CsvRow parseRow(int row, CSVRecord record, List<ApiFieldError> errors) {
        if (record.size() != HEADER.size()) {
            errors.add(new ApiFieldError(row, "row",
                    "Expected %d columns, found %d.".formatted(HEADER.size(), record.size())));
            return null;
        }

        TransactionType type = parseType(row, text(record, "Type"), errors);
        LocalDateTime occurredAt = parseDate(row, text(record, "Date"), errors);
        // "Time" is ignored: "Date" already contains the time.
        String name = required(row, "Name", text(record, "Name"), 255, errors);
        BigDecimal amount = parseAmount(row, text(record, "Amount"), errors);
        CategoryGroup categoryGroup = parseCategoryGroup(row, text(record, "Category Group"), errors);
        String category = optional(row, "Category", text(record, "Category"), 100, errors);
        String account = required(row, "Account", text(record, "Account"), 100, errors);
        String notes = optional(row, "Notes", text(record, "Notes"), 1000, errors);

        if (type != null && amount != null) {
            AmountRules.signViolation(type, amount)
                    .ifPresent(message -> errors.add(new ApiFieldError(row, "Amount", message)));
        }
        // Same order as the columns, so the sign error sits next to the other Amount errors.
        errors.sort(Comparator.comparingInt(error -> HEADER.indexOf(error.field())));
        return new CsvRow(row, type, occurredAt, name, amount, categoryGroup, category, account, notes);
    }

    /** The stripped value of a column; empty string when blank. */
    private static String text(CSVRecord record, String column) {
        return record.get(HEADER.indexOf(column)).strip();
    }

    private static TransactionType parseType(int row, String value, List<ApiFieldError> errors) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "expense" -> TransactionType.EXPENSE;
            case "income" -> TransactionType.INCOME;
            case "" -> fail(row, "Type", "Type is required.", errors);
            default -> fail(row, "Type", "must be Expense or Income.", errors);
        };
    }

    private static LocalDateTime parseDate(int row, String value, List<ApiFieldError> errors) {
        if (value.isEmpty()) {
            return fail(row, "Date", "Date is required.", errors);
        }
        try {
            return LocalDateTime.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return fail(row, "Date", "must be a valid date in yyyy-MM-dd HH:mm:ss format.", errors);
        }
    }

    private static BigDecimal parseAmount(int row, String value, List<ApiFieldError> errors) {
        if (value.isEmpty()) {
            return fail(row, "Amount", "Amount is required.", errors);
        }
        if (!SIGNED_AMOUNT.matcher(value).matches()) {
            return fail(row, "Amount", Money.SIGNED_MESSAGE, errors);
        }
        return Money.parse(value);
    }

    private static CategoryGroup parseCategoryGroup(int row, String value, List<ApiFieldError> errors) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return CategoryGroup.fromLabel(value);
        } catch (IllegalArgumentException e) {
            return fail(row, "Category Group", "Unknown category group.", errors);
        }
    }

    private static String required(int row, String column, String value, int maxLength, List<ApiFieldError> errors) {
        if (value.isEmpty()) {
            return fail(row, column, column + " is required.", errors);
        }
        return optional(row, column, value, maxLength, errors);
    }

    private static String optional(int row, String column, String value, int maxLength, List<ApiFieldError> errors) {
        if (value.length() > maxLength) {
            return fail(row, column, "must be at most %d characters.".formatted(maxLength), errors);
        }
        return value.isEmpty() ? null : value;
    }

    private static <T> T fail(int row, String column, String message, List<ApiFieldError> errors) {
        errors.add(new ApiFieldError(row, column, message));
        return null;
    }

    private static String describe(int invalidRows, int errorCount) {
        String rows = invalidRows == 1 ? "1 row has errors." : invalidRows + " rows have errors.";
        String truncated = errorCount > MAX_REPORTED_ERRORS
                ? " Only the first %d errors are listed.".formatted(MAX_REPORTED_ERRORS)
                : "";
        return rows + " Nothing was imported." + truncated;
    }
}
