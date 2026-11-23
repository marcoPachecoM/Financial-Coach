package com.financialcoach.transactions;

/**
 * Protection against CSV (formula) injection. Spreadsheets run a cell that starts with {@code =},
 * {@code +}, {@code -} or {@code @} as a formula, so a transaction named
 * {@code =HYPERLINK("http://evil", "Click")} would become a live link when the export is opened.
 * Such text is exported with a leading {@code '}, which spreadsheets show as plain text, and the
 * import removes that {@code '} again, so exporting and importing keeps the original text.
 *
 * <p>Only for text written by people (names, categories, notes, accounts). Amounts are numbers the
 * app formats itself, so a negative amount is never escaped.
 */
final class CsvFormulas {

    /** OWASP's list: formula starts, plus tab and carriage return, which some programs strip first. */
    private static final String DANGEROUS_STARTS = "=+-@\t\r";

    private CsvFormulas() {
    }

    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        return DANGEROUS_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    /** Undoes {@link #escape}: only a {@code '} followed by a dangerous character is removed. */
    static String unescape(String value) {
        if (value.length() >= 2 && value.charAt(0) == '\'' && DANGEROUS_STARTS.indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }
}
