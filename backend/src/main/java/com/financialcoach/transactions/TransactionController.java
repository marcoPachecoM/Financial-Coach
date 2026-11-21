package com.financialcoach.transactions;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.shared.error.PayloadTooLargeException;
import com.financialcoach.shared.web.QueryParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

    private static final Set<String> LIST_PARAMETERS = Set.of("account_id", "page", "size", "start_date", "end_date");

    /** 1 MiB, as in the contract. spring.servlet.multipart enforces it too, before the file is read. */
    static final long MAX_CSV_BYTES = 1024 * 1024;

    private final TransactionService transactionService;
    private final CsvImportService csvImportService;

    TransactionController(TransactionService transactionService, CsvImportService csvImportService) {
        this.transactionService = transactionService;
        this.csvImportService = csvImportService;
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
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        QueryParameters.requireOnly(request, LIST_PARAMETERS);
        return TransactionPageResponse.from(
                transactionService.list(user.id(), accountId, page, size, startDate, endDate));
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
