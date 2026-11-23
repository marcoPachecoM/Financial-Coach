package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.DateRange;
import java.io.IOException;
import java.io.Writer;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /transactions/export}: the user's transactions as a CSV file, of one account or all
 * of them, optionally between two dates, oldest first.
 *
 * <p>Two steps, so errors (foreign account, invalid dates) become a normal Problem Details
 * response before the download starts: {@link #prepare} checks everything, then {@link #write}
 * streams the rows, a page at a time, without loading them all in memory.
 */
@Service
class CsvExportService {

    static final int PAGE_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(CsvExportService.class);

    private final TransactionRepository transactions;
    private final AccountService accountService;

    CsvExportService(TransactionRepository transactions, AccountService accountService) {
        this.transactions = transactions;
        this.accountService = accountService;
    }

    /**
     * What to export, already checked: the accounts are the user's and the dates are valid.
     * {@code accountId} is null for every account, the dates are null when not given, and
     * {@code rows} is counted before writing.
     */
    record CsvExport(UUID userId, UUID accountId, LocalDate startDate, LocalDate endDate, List<UUID> accountIds,
                     Map<UUID, String> accountNames, DateRange range, String filename, long rows) {
    }

    /** {@code accountId} null exports every account of the user. 404 for another user's account. */
    @Transactional(readOnly = true)
    CsvExport prepare(UUID userId, UUID accountId, LocalDate startDate, LocalDate endDate) {
        DateRange range = DateRange.between(startDate, endDate);
        Map<UUID, String> names = accountService.namesOf(userId);
        List<UUID> accountIds;
        if (accountId != null) {
            accountService.requireOwned(userId, accountId);
            accountIds = List.of(accountId);
        } else {
            accountIds = List.copyOf(names.keySet());
        }
        String scope = accountId == null ? "all-accounts" : slug(names.get(accountId));
        long rows = accountIds.isEmpty() ? 0
                : transactions.countForExport(userId, accountIds, range.from(), range.to());
        return new CsvExport(userId, accountId, startDate, endDate, accountIds, names, range,
                filename(scope, startDate, endDate), rows);
    }

    /** Writes the header and every row; returns the number of rows. */
    @Transactional(readOnly = true)
    int write(CsvExport export, Writer out) throws IOException {
        CsvTransactionWriter csv = new CsvTransactionWriter(out);
        int count = 0;
        if (!export.accountIds().isEmpty()) {
            for (int page = 0; ; page++) {
                Slice<CsvExportRow> rows = transactions.exportPage(export.userId(), export.accountIds(),
                        export.range().from(), export.range().to(), PageRequest.of(page, PAGE_SIZE));
                for (CsvExportRow row : rows) {
                    csv.write(row, export.accountNames().get(row.accountId()));
                    count++;
                }
                if (!rows.hasNext()) {
                    break;
                }
            }
        }
        csv.flush();
        // An export takes someone's whole financial history at once: keep a trace (ids only), so a
        // stolen session used to pull data shows up in the logs.
        log.info("CSV export user={} account={} from={} to={} rows={}", export.userId(),
                export.accountId() == null ? "all" : export.accountId(),
                export.startDate() == null ? "any" : export.startDate(),
                export.endDate() == null ? "any" : export.endDate(), count);
        return count;
    }

    /** e.g. financial-coach-debit-card-2026-11-01-to-2026-11-30.csv */
    private static String filename(String scope, LocalDate startDate, LocalDate endDate) {
        String dates;
        if (startDate == null && endDate == null) {
            dates = "all-time";
        } else {
            dates = (startDate == null ? "start" : startDate.toString()) + "-to-"
                    + (endDate == null ? "today" : endDate.toString());
        }
        return "financial-coach-" + scope + "-" + dates + ".csv";
    }

    /** "Tarjeta Débito" becomes "tarjeta-debito": safe in a file name on every system. */
    static String slug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-$", "");
        }
        return slug.isEmpty() ? "account" : slug;
    }
}
