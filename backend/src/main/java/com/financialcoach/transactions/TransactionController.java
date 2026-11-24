package com.financialcoach.transactions;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.categorization.CategoryGroup;
import com.financialcoach.shared.DateRange;
import com.financialcoach.shared.Money;
import com.financialcoach.shared.error.ApiFieldError;
import com.financialcoach.shared.error.ValidationException;
import com.financialcoach.shared.error.PayloadTooLargeException;
import com.financialcoach.shared.web.QueryParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/** Transactions endpoints of openapi.yaml. */
@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

    private static final Set<String> LIST_PARAMETERS = Set.of("account_id", "page", "size", "start_date", "end_date",
            "q", "category_group", "min_amount", "max_amount");
    static final String ROW_COUNT_HEADER = "X-Row-Count";
    private static final Set<String> EXPORT_PARAMETERS = Set.of("account_id", "start_date", "end_date");

    /** 1 MiB, as in the contract. spring.servlet.multipart enforces it too, before the file is read. */
    static final long MAX_CSV_BYTES = 1024 * 1024;

    private final TransactionService transactionService;
    private final CsvImportService csvImportService;
    private final CsvExportService csvExportService;

    TransactionController(TransactionService transactionService, CsvImportService csvImportService,
                          CsvExportService csvExportService) {
        this.transactionService = transactionService;
        this.csvImportService = csvImportService;
        this.csvExportService = csvExportService;
    }

    @GetMapping
    TransactionPageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
                                 HttpServletRequest request,
                                 @RequestParam("account_id") UUID accountId,
                                 @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
                                 @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size,
                                 @RequestParam(name = "start_date", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @RequestParam(name = "end_date", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                 @RequestParam(name = "q", required = false) @Size(max = 100) String text,
                                 @RequestParam(name = "category_group", required = false) String categoryGroup,
                                 @RequestParam(name = "min_amount", required = false) String minAmount,
                                 @RequestParam(name = "max_amount", required = false) String maxAmount) {
        QueryParameters.requireOnly(request, LIST_PARAMETERS);
        DateRange range = DateRange.between(startDate, endDate);
        TransactionListFilter filter = listFilter(text, categoryGroup, minAmount, maxAmount);
        return TransactionPageResponse.from(transactionService.list(user.id(), accountId, page, size, range, filter));
    }

    /** Checks the optional filters of the list; every problem is reported at once (400). */
    private static TransactionListFilter listFilter(String text, String categoryGroup, String minAmount,
                                                    String maxAmount) {
        List<ApiFieldError> errors = new ArrayList<>();
        CategoryGroup group = null;
        if (categoryGroup != null && !categoryGroup.isBlank()) {
            try {
                group = CategoryGroup.fromLabel(categoryGroup.strip());
            } catch (IllegalArgumentException e) {
                errors.add(ApiFieldError.of("category_group", "must be one of the category groups"));
            }
        }
        BigDecimal min = amountBound("min_amount", minAmount, errors);
        BigDecimal max = amountBound("max_amount", maxAmount, errors);
        if (min != null && max != null && min.compareTo(max) > 0) {
            errors.add(ApiFieldError.of("max_amount", "must be greater than or equal to min_amount"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new TransactionListFilter(text == null || text.isBlank() ? null : text, group, min, max);
    }

    private static BigDecimal amountBound(String field, String value, List<ApiFieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.strip().matches(Money.POSITIVE_PATTERN)) {
            errors.add(ApiFieldError.of(field, Money.POSITIVE_MESSAGE));
            return null;
        }
        return Money.parse(value.strip());
    }

    @PostMapping
    ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody TransactionCreateRequest request,
                                               UriComponentsBuilder uriBuilder) {
        Transaction transaction = transactionService.create(user.id(), request);
        URI location = uriBuilder.path("/api/v1/transactions/{id}").buildAndExpand(transaction.getId()).toUri();
        return ResponseEntity.created(location).body(TransactionResponse.from(transaction));
    }

    @PostMapping(path = "/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CsvUploadResponse> uploadCsv(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new PayloadTooLargeException("The file must be at most 1 MiB.");
        }
        CsvUploadResponse result = csvImportService.importFile(user.id(), file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Downloads the transactions as CSV, in the import format. Without account_id, every account
     * of the user. The checks run before anything is written, so errors are normal Problem Details.
     */
    @GetMapping("/export")
    void export(@AuthenticationPrincipal AuthenticatedUser user,
                HttpServletRequest request,
                HttpServletResponse response,
                @RequestParam(name = "account_id", required = false) UUID accountId,
                @RequestParam(name = "start_date", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                @RequestParam(name = "end_date", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws IOException {
        QueryParameters.requireOnly(request, EXPORT_PARAMETERS);
        CsvExportService.CsvExport export = csvExportService.prepare(user.id(), accountId, startDate, endDate);

        // Known before the body: lets the client say "no transactions" instead of saving an empty file.
        response.setHeader(ROW_COUNT_HEADER, Long.toString(export.rows()));
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // The file name is already ASCII (see CsvExportService.slug), so no RFC 5987 variant is needed.
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(export.filename()).build().toString());
        csvExportService.write(export, response.getWriter());
    }

    @GetMapping("/{transactionId}")
    TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID transactionId) {
        return TransactionResponse.from(transactionService.get(user.id(), transactionId));
    }

    @PatchMapping("/{transactionId}")
    TransactionResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID transactionId,
                               @Valid @RequestBody TransactionUpdateRequest request) {
        return TransactionResponse.from(transactionService.update(user.id(), transactionId, request));
    }

    @DeleteMapping("/{transactionId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID transactionId) {
        transactionService.delete(user.id(), transactionId);
        return ResponseEntity.noContent().build();
    }
}
