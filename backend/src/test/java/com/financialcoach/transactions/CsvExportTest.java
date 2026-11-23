package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CsvExportTest extends ApiIntegrationTest {

    private static final String EXPORT = "/api/v1/transactions/export";
    private static final String HEADER = "Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes";
    private static final String BOM = "﻿";

    @Autowired
    private RateLimiter rateLimiter;

    private TestUser alice;
    private UUID aliceCard;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.reset();
        alice = users.create("alice", UserRole.CLIENT, true);
        aliceCard = jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, 'Debit card') RETURNING id",
                UUID.class, alice.id());
        upload(alice, HEADER + "\n" + """
                Expense,2026-09-25 15:58:12,15:58,Engigas,-232.5,Utilities,Gas,Debit card,
                Income,2026-09-24 06:55:57,06:55,Payroll,25000,Salary,,Debit card,"September, bonus"
                Expense,2026-09-26 10:00:00,10:00,Oxxo,45,Food,Snacks,Savings,
                """).andExpect(status().isCreated());
    }

    @Test
    void exportsOneAccountInTheImportFormatOldestFirst() throws Exception {
        String body = mvc.perform(export(alice).param("account_id", aliceCard.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("X-Row-Count", "2"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"financial-coach-debit-card-all-time.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Bank signs: expenses negative, income positive. Values with a comma are quoted.
        assertThat(body).isEqualTo(BOM + HEADER + "\r\n"
                + "Income,2026-09-24 06:55:57,06:55,Payroll,25000.00,Salary,,Debit card,\"September, bonus\"\r\n"
                + "Expense,2026-09-25 15:58:12,15:58,Engigas,-232.50,Utilities,Gas,Debit card,\r\n");
    }

    @Test
    void withoutAccountItExportsEveryAccountOfTheUser() throws Exception {
        String body = mvc.perform(export(alice))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"financial-coach-all-accounts-all-time.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body.lines()).hasSize(4).last().asString().startsWith("Expense,2026-09-26 10:00:00,10:00,Oxxo,-45.00,Food,Snacks,Savings");
    }

    @Test
    void filtersByDatesIncludingBothEnds() throws Exception {
        String body = mvc.perform(export(alice).param("start_date", "2026-09-25").param("end_date", "2026-09-26"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"financial-coach-all-accounts-2026-09-25-to-2026-09-26.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("Engigas", "Oxxo").doesNotContain("Payroll");
    }

    @Test
    void reimportingAnExportOfImportedRowsSkipsThemAll() throws Exception {
        byte[] exported = mvc.perform(export(alice)).andReturn().getResponse().getContentAsByteArray();

        upload(alice, new String(exported, StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_imported").value(0))
                .andExpect(jsonPath("$.records_duplicated").value(3));
    }

    @Test
    void reimportingAnExportAlsoSkipsTransactionsTypedInByHand() throws Exception {
        insertManual(aliceCard, "2026-10-01T12:00", "Tacos", null, null);
        byte[] exported = mvc.perform(export(alice)).andReturn().getResponse().getContentAsByteArray();

        upload(alice, new String(exported, StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_imported").value(0))
                .andExpect(jsonPath("$.records_duplicated").value(4));
    }

    @Test
    void textThatLooksLikeAFormulaIsNeutralizedAndRestoredOnImport() throws Exception {
        insertManual(aliceCard, "2026-10-01T12:00", "=HYPERLINK(\"http://evil.test\",\"x\")", "-1+2", "@SUM(A1)");

        String body = mvc.perform(export(alice).param("start_date", "2026-10-01"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // A leading ' makes spreadsheets show the text instead of running it.
        assertThat(body).contains("\"'=HYPERLINK(\"\"http://evil.test\"\",\"\"x\"\")\"", ",'-1+2,", ",'@SUM(A1)");

        // Imported by someone else, the original text comes back.
        TestUser bob = users.create("bob", UserRole.CLIENT, true);
        upload(bob, body).andExpect(status().isCreated()).andExpect(jsonPath("$.records_imported").value(1));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT name, category, notes FROM transactions WHERE user_id = ?", bob.id());
        assertThat(row).containsEntry("name", "=HYPERLINK(\"http://evil.test\",\"x\")")
                .containsEntry("category", "-1+2")
                .containsEntry("notes", "@SUM(A1)");
    }

    @Test
    void anotherUsersAccountIsNotFoundAndNothingIsDownloaded() throws Exception {
        TestUser bob = users.create("bob", UserRole.CLIENT, true);

        mvc.perform(export(bob).param("account_id", aliceCard.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aUserWithoutAccountsGetsOnlyTheHeader() throws Exception {
        TestUser carol = users.create("carol", UserRole.CLIENT, true);

        String body = mvc.perform(export(carol)).andExpect(status().isOk())
                .andExpect(header().string("X-Row-Count", "0"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).isEqualTo(BOM + HEADER + "\r\n");
    }

    @Test
    void aPeriodWithoutTransactionsSaysSoInTheRowCount() throws Exception {
        mvc.perform(export(alice).param("start_date", "2025-01-01").param("end_date", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Row-Count", "0"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void everyExportIsLoggedWithIdsOnly(CapturedOutput output) throws Exception {
        mvc.perform(export(alice).param("account_id", aliceCard.toString()).param("start_date", "2026-09-01"))
                .andExpect(status().isOk());

        assertThat(output.getOut())
                .contains("CSV export user=%s account=%s from=2026-09-01 to=any rows=2".formatted(alice.id(), aliceCard))
                .doesNotContain("Payroll", "Debit card");
    }

    @Test
    void validatesTheParameters() throws Exception {
        mvc.perform(export(alice).param("start_date", "2026-10-02").param("end_date", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(export(alice).param("page", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isRateLimitedPerUser() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(export(alice)).andExpect(status().isOk());
        }
        mvc.perform(export(alice))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(EXPORT)).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder export(TestUser user) {
        return get(EXPORT).with(as(user));
    }

    private ResultActions upload(TestUser user, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/v1/transactions/upload-csv").file(file).with(as(user)));
    }

    /** A transaction typed in by hand (no CSV fingerprint). */
    private void insertManual(UUID accountId, String datetime, String name, String category, String notes) {
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, type, datetime, name, amount, category_group, category,
                                          notes, source, category_group_source)
                VALUES (?, ?, 'EXPENSE', ?, ?, ?, 'Others', ?, ?, 'MANUAL', 'USER')
                """, alice.id(), accountId, LocalDateTime.parse(datetime), name, new BigDecimal("10.00"), category, notes);
    }
}
