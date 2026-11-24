package com.financialcoach.transactions;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.accounts.ResolvedAccounts;
import com.financialcoach.categorization.CategorizationService;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.categorization.CategoryGroupAssignment;
import com.financialcoach.categorization.CategoryGroupSource;
import com.financialcoach.categorization.Categorizations;
import com.financialcoach.shared.error.ConflictException;
import com.financialcoach.shared.error.ValidationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /transactions/upload-csv}: validate every row first, then, in one database
 * transaction, create missing accounts, skip rows already imported and store the rest.
 * Any failure rolls back everything, including the new accounts.
 *
 * <p>Rows that are occurrences of a recurring transaction are linked to it, replacing the
 * occurrence that was created automatically (see RecurringReconciler).
 */
@Service
class CsvImportService {

    /** Keeps the number of bind parameters per query far below PostgreSQL's limit. */
    private static final int LOOKUP_CHUNK_SIZE = 1_000;

    private static final String FINGERPRINT_CONSTRAINT = "ux_transactions_account_fingerprint";

    private final TransactionRepository transactions;
    private final ImportBatchRepository importBatches;
    private final AccountService accountService;
    private final CategorizationService categorizationService;
    private final RecurringReconciler recurringReconciler;
    private final TagService tagService;
    private final Clock clock;

    CsvImportService(TransactionRepository transactions, ImportBatchRepository importBatches,
                     AccountService accountService, CategorizationService categorizationService,
                     RecurringReconciler recurringReconciler, TagService tagService, Clock clock) {
        this.transactions = transactions;
        this.importBatches = importBatches;
        this.accountService = accountService;
        this.categorizationService = categorizationService;
        this.recurringReconciler = recurringReconciler;
        this.tagService = tagService;
        this.clock = clock;
    }

    @Transactional
    CsvUploadResponse importFile(UUID userId, String originalFilename, byte[] content) {
        String filename = checkFilename(originalFilename);
        List<CsvRow> rows = CsvTransactionReader.read(content);

        ResolvedAccounts accounts = accountService.resolveOrCreate(
                userId, rows.stream().map(CsvRow::account).toList());
        List<String> fingerprints = fingerprints(rows, accounts);
        Set<String> alreadyImported = findExisting(accounts.idsByKey().values(), fingerprints);
        Map<String, Integer> unfingerprinted = findUnfingerprinted(userId, accounts, rows, fingerprints,
                alreadyImported);

        ImportBatch batch = importBatches.save(new ImportBatch(userId, filename));
        List<Transaction> newTransactions = new ArrayList<>();
        List<CsvRow> newRows = new ArrayList<>();
        List<String> newFingerprints = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            CsvRow row = rows.get(i);
            String fingerprint = fingerprints.get(i);
            if (alreadyImported.contains(fingerprint) || claimUnfingerprinted(unfingerprinted, row, accounts)) {
                continue;
            }
            newRows.add(row);
            newFingerprints.add(fingerprint);
        }

        // Rows the file left without a group, or with Other (the bank could not tell either), are
        // categorized, all in one go (one LLM round at most). Any other group in the file is kept.
        Categorizations categorized = categorizationService.categorizeAll(userId, newRows.stream()
                .filter(CsvImportService::needsCategorizing).map(CsvRow::name).toList());
        int automaticallyCategorized = 0;
        for (int i = 0; i < newRows.size(); i++) {
            CsvRow row = newRows.get(i);
            String fingerprint = newFingerprints.get(i);
            CategoryGroupAssignment fromFile = new CategoryGroupAssignment(row.categoryGroup(), CategoryGroupSource.CSV);
            CategoryGroupAssignment group = fromFile;
            if (needsCategorizing(row)) {
                CategoryGroupAssignment found = categorized.of(row.name());
                boolean gotAGroup = !categorized.isFallback(row.name()) && found.group() != CategoryGroup.OTHER;
                if (gotAGroup) {
                    automaticallyCategorized++;
                }
                // When nothing better than Other is known, an Other row keeps the file's value.
                group = gotAGroup || row.categoryGroup() == null ? found : fromFile;
            }
            newTransactions.add(Transaction.fromCsv(
                    userId, accounts.idFor(row.account()), row.type(), row.occurredAt(), row.name(), row.amount(),
                    group.group(), group.source(), row.category(), row.notes(),
                    batch.getId(), fingerprint));
        }

        // Tags of the new rows only: a skipped duplicate does not create tags. One lookup for the file.
        Map<String, Tag> tags = tagService.resolve(userId, newRows.stream().flatMap(r -> r.tags().stream()).toList());
        for (int i = 0; i < newTransactions.size(); i++) {
            newTransactions.get(i).replaceTags(TagService.pick(tags, newRows.get(i).tags()));
        }

        int recurringMatched = recurringReconciler.reconcile(
                userId, accounts.idsByKey().values(), newTransactions, clock.instant());
        saveAll(newTransactions);
        batch.recordCounts(newTransactions.size(), rows.size() - newTransactions.size());
        importBatches.flush();
        return new CsvUploadResponse(newTransactions.size(), rows.size() - newTransactions.size(),
                recurringMatched, automaticallyCategorized, accounts.created());
    }

    /** Empty and Other both mean the file does not know the group. */
    private static boolean needsCategorizing(CsvRow row) {
        return row.categoryGroup() == null || row.categoryGroup() == CategoryGroup.OTHER;
    }

    /** Only the base name is kept: some browsers send the full client path. */
    private static String checkFilename(String originalFilename) {
        String filename = originalFilename == null ? "" : originalFilename.strip();
        filename = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ValidationException("file", "must be a .csv file");
        }
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }

    /** One fingerprint per row, in the same order as the rows. */
    private static List<String> fingerprints(List<CsvRow> rows, ResolvedAccounts accounts) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<String> fingerprints = new ArrayList<>(rows.size());
        for (CsvRow row : rows) {
            String key = TransactionFingerprint.key(
                    accounts.idFor(row.account()), row.occurredAt(), row.name(), row.amount());
            int occurrence = occurrences.merge(key, 1, Integer::sum);
            fingerprints.add(TransactionFingerprint.of(key, occurrence));
        }
        return fingerprints;
    }

    private Set<String> findExisting(Iterable<UUID> accountIds, List<String> fingerprints) {
        Set<UUID> ids = new HashSet<>();
        accountIds.forEach(ids::add);
        List<String> distinct = List.copyOf(new LinkedHashSet<>(fingerprints));

        Set<String> existing = new HashSet<>();
        for (int start = 0; start < distinct.size(); start += LOOKUP_CHUNK_SIZE) {
            List<String> chunk = distinct.subList(start, Math.min(distinct.size(), start + LOOKUP_CHUNK_SIZE));
            existing.addAll(transactions.findExistingFingerprints(ids, chunk));
        }
        return existing;
    }

    /**
     * Transactions without a fingerprint (manual or recurring) that match a row not already found by
     * fingerprint, counted per identity (account, date and time to the second, name, amount). Two
     * transactions with all of that equal are the same in practice: re-importing a file exported
     * by this app must not duplicate what was typed in by hand.
     */
    private Map<String, Integer> findUnfingerprinted(UUID userId, ResolvedAccounts accounts, List<CsvRow> rows,
                                                     List<String> fingerprints, Set<String> alreadyImported) {
        List<LocalDateTime> times = new ArrayList<>(new LinkedHashSet<>(IntStream.range(0, rows.size())
                .filter(i -> !alreadyImported.contains(fingerprints.get(i)))
                .mapToObj(i -> rows.get(i).occurredAt())
                .toList()));
        Set<UUID> accountIds = new HashSet<>(accounts.idsByKey().values());

        Map<String, Integer> counts = new HashMap<>();
        for (int start = 0; start < times.size(); start += LOOKUP_CHUNK_SIZE) {
            List<LocalDateTime> chunk = times.subList(start, Math.min(times.size(), start + LOOKUP_CHUNK_SIZE));
            transactions.findWithoutFingerprintAt(userId, accountIds, chunk)
                    .forEach(existing -> counts.merge(existing.key(), 1, Integer::sum));
        }
        return counts;
    }

    /**
     * True when the row matches one of those transactions, which is then used up: if a coffee was
     * typed in twice at the same second, a file with three such rows still imports the third.
     */
    private static boolean claimUnfingerprinted(Map<String, Integer> unfingerprinted, CsvRow row,
                                                ResolvedAccounts accounts) {
        String key = TransactionFingerprint.key(accounts.idFor(row.account()), row.occurredAt(), row.name(),
                row.amount());
        Integer left = unfingerprinted.get(key);
        if (left == null || left == 0) {
            return false;
        }
        unfingerprinted.put(key, left - 1);
        return true;
    }

    /**
     * The fingerprint lookup avoids duplicates in the normal case. If the same file is uploaded
     * twice at the same time, the unique index rejects the second import as a whole.
     */
    private void saveAll(List<Transaction> newTransactions) {
        try {
            transactions.saveAllAndFlush(newTransactions);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains(FINGERPRINT_CONSTRAINT)) {
                throw new ConflictException("This file is already being imported. Try again in a moment.");
            }
            throw e;
        }
    }
}
