package com.financialcoach.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class CsvImportTest extends ApiIntegrationTest {

    private static final String UPLOAD = "/api/v1/transactions/upload-csv";
    private static final String HEADER = "Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes\n";

    private TestUser alice;
    private TestUser bob;
    private UUID aliceCard;

    @BeforeEach
    void createUsersAndAccounts() {
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
        aliceCard = insertAccount(alice, "Debit card");
        insertAccount(bob, "Savings");
    }

    @Nested
    class Import {

        @Test
        void importsRowsAndCreatesMissingAccounts() throws Exception {
            upload(alice, HEADER + """
                    Expense,2026-01-02 13:35:00,13:35,La tiendita,100.00,Food,Food,debit card,
                    Income,2026-01-15 09:00:00,09:00,Employer Inc,-20000.00,Salary,Work,Debit Card,January
                    Expense,2026-01-03 18:00:00,18:00,Cinema,250,Entertainment,,Savings,
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(3))
                    .andExpect(jsonPath("$.records_duplicated").value(0))
                    .andExpect(jsonPath("$.accounts_created", hasSize(1)))
                    .andExpect(jsonPath("$.accounts_created[0].name").value("Savings"))
                    .andExpect(jsonPath("$.accounts_created[0].monthly_budget").value(nullValue()))
                    .andExpect(jsonPath("$.accounts_created[0].id").exists());

            // "debit card" and "Debit Card" both go to the existing "Debit card".
            assertThat(countTransactions(aliceCard)).isEqualTo(2);
            // Bob's "Savings" is not used: Alice got her own.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM accounts WHERE user_id = ? AND name = 'Savings'", Integer.class, alice.id()))
                    .isEqualTo(1);
        }

        @Test
        void storesEveryColumn() throws Exception {
            upload(alice, HEADER
                    + "Income,2026-01-15 09:00:05,ignored,  Employer Inc  ,-20000.5,Salary,  Payroll  ,Debit card,"
                    + "\"Bonus, \"\"January\"\"\"\n")
                    .andExpect(status().isCreated());

            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM transactions WHERE account_id = ?", aliceCard);
            assertThat(row)
                    .containsEntry("user_id", alice.id())
                    .containsEntry("type", "INCOME")
                    .containsEntry("datetime", java.sql.Timestamp.valueOf("2026-01-15 09:00:05"))
                    .containsEntry("name", "Employer Inc")
                    .containsEntry("amount", new java.math.BigDecimal("-20000.50"))
                    .containsEntry("category_group", "Salary")
                    .containsEntry("category", "Payroll")
                    .containsEntry("notes", "Bonus, \"January\"")
                    .containsEntry("source", "CSV")
                    .containsEntry("category_group_source", "CSV");
            assertThat((String) row.get("fingerprint")).hasSize(64);
            assertThat(row.get("import_batch_id")).isNotNull();
        }

        @Test
        void assignsTheGroupWhenTheRowHasNone() throws Exception {
            upload(alice, HEADER + "Expense,2026-01-02 13:35:00,,Unknown shop,10,,,Debit card,\n")
                    .andExpect(status().isCreated());

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT category_group, category_group_source, category, notes FROM transactions WHERE account_id = ?",
                    aliceCard);
            assertThat(row)
                    .containsEntry("category_group", "Other")
                    .containsEntry("category_group_source", "RULE")
                    .containsEntry("category", null)
                    .containsEntry("notes", null);
        }

        /** Same account, date and time to the second, name and amount as a manual transaction: the same one. */
        @Test
        void aRowIdenticalToATransactionTypedInByHandIsSkipped() throws Exception {
            insertManual(aliceCard, "2026-01-02T13:35:00", "La tiendita", "100.00");

            upload(alice, HEADER + """
                    Expense,2026-01-02 13:35:00,13:35,La tiendita,100.00,Food,,Debit card,
                    Expense,2026-01-02 13:35:01,13:35,La tiendita,100.00,Food,,Debit card,
                    Expense,2026-01-02 13:35:00,13:35,La tiendita,100.50,Food,,Debit card,
                    Expense,2026-01-02 13:35:00,13:35,La Tiendita,100.00,Food,,Debit card,
                    """)
                    .andExpect(status().isCreated())
                    // Only the exact match is skipped: another second, amount or spelling is new.
                    .andExpect(jsonPath("$.records_imported").value(3))
                    .andExpect(jsonPath("$.records_duplicated").value(1));
        }

        @Test
        void eachTransactionTypedInByHandMatchesOneRowOnly() throws Exception {
            insertManual(aliceCard, "2026-01-02T08:00:00", "Coffee", "45.00");
            insertManual(aliceCard, "2026-01-02T08:00:00", "Coffee", "45.00");

            upload(alice, HEADER + """
                    Expense,2026-01-02 08:00:00,08:00,Coffee,45,Food,,Debit card,
                    Expense,2026-01-02 08:00:00,08:00,Coffee,45,Food,,Debit card,
                    Expense,2026-01-02 08:00:00,08:00,Coffee,45,Food,,Debit card,
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(1))
                    .andExpect(jsonPath("$.records_duplicated").value(2));
        }

        @Test
        void anotherAccountsTransactionDoesNotCount() throws Exception {
            insertManual(insertAccount(alice, "Cash"), "2026-01-02T08:00:00", "Coffee", "45.00");

            upload(alice, HEADER + "Expense,2026-01-02 08:00:00,08:00,Coffee,45,Food,,Debit card,\n")
                    .andExpect(jsonPath("$.records_imported").value(1));
        }

        /** Bank exports use cash-flow signs; Type decides, so both conventions give the same result. */
        @Test
        void typeDecidesTheSignNotTheAmount() throws Exception {
            upload(alice, HEADER + """
                    Expense,2026-09-25 15:58:12,15:58,Engigas,-232.5,Utilities,Gas,Debit card,
                    Income,2026-09-24 06:55:57,6:55,Banorte iva,301.92,Others,Awards,Debit card,
                    Expense,2026-09-26 10:00:00,,Oxxo,45,Food,Snacks,Debit card,
                    Income,2026-09-27 10:00:00,,Refund,-10,Others,,Debit card,
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(4));

            assertThat(jdbc.queryForList("SELECT amount::text FROM transactions WHERE account_id = ? ORDER BY datetime",
                    String.class, aliceCard))
                    .containsExactly("-301.92", "232.50", "45.00", "-10.00");
        }

        @Test
        void theSameRowWithTheOtherSignIsADuplicate() throws Exception {
            upload(alice, HEADER + "Expense,2026-09-25 15:58:12,15:58,Engigas,-232.5,Utilities,Gas,Debit card,\n")
                    .andExpect(jsonPath("$.records_imported").value(1));

            upload(alice, HEADER + "Expense,2026-09-25 15:58:12,15:58,Engigas,232.50,Utilities,Gas,Debit card,\n")
                    .andExpect(jsonPath("$.records_imported").value(0))
                    .andExpect(jsonPath("$.records_duplicated").value(1));
        }

        /** A real export (bank-style signs, free-text categories); see src/test/resources/csv. */
        @Test
        void importsARealExport() throws Exception {
            ClassPathResource resource = new ClassPathResource("csv/mock_transactions.csv");
            assumeTrue(resource.exists(), "csv/mock_transactions.csv not present; it is a private export kept out of git");
            byte[] file = resource.getContentAsByteArray();

            upload(alice, "mock_transactions.csv", file)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(24))
                    .andExpect(jsonPath("$.accounts_created[*].name", containsInAnyOrder("Malu", "Credit Card")));

            Map<String, Object> totals = jdbc.queryForMap("""
                    SELECT sum(amount) FILTER (WHERE type = 'EXPENSE') AS expenses,
                           sum(amount) FILTER (WHERE type = 'INCOME')  AS income,
                           count(*) FILTER (WHERE category_group = 'Utilities' AND category = 'Gas') AS gas
                    FROM transactions WHERE user_id = ?
                    """, alice.id());
            assertThat(totals)
                    .containsEntry("expenses", new java.math.BigDecimal("12936.24"))
                    .containsEntry("income", new java.math.BigDecimal("-1382.92"))
                    .containsEntry("gas", 2L);
        }

        @Test
        void recordsTheImportBatch() throws Exception {
            upload(alice, "january.csv", HEADER + """
                    Expense,2026-01-02 13:35:00,,La tiendita,100,Food,,Debit card,
                    Expense,2026-01-03 13:35:00,,La tiendita,100,Food,,Debit card,
                    """)
                    .andExpect(status().isCreated());

            Map<String, Object> batch = jdbc.queryForMap("SELECT * FROM import_batches WHERE user_id = ?", alice.id());
            assertThat(batch)
                    .containsEntry("filename", "january.csv")
                    .containsEntry("records_imported", 2)
                    .containsEntry("records_duplicated", 0);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM transactions WHERE import_batch_id = ?", Integer.class, batch.get("id")))
                    .isEqualTo(2);
        }

        @Test
        void acceptsExcelByteOrderMarkAndCrlfLineEndings() throws Exception {
            String csv = "\uFEFF" + HEADER.replace("\n", "\r\n")
                    + "Expense,2026-01-02 13:35:00,,La tiendita,100,Food,,Debit card,\r\n";

            upload(alice, csv).andExpect(status().isCreated()).andExpect(jsonPath("$.records_imported").value(1));
        }

        @Test
        void headerOnlyImportsNothing() throws Exception {
            upload(alice, HEADER)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(0))
                    .andExpect(jsonPath("$.accounts_created", hasSize(0)));
        }

        @Test
        void keepsOnlyTheBaseNameOfTheFile() throws Exception {
            upload(alice, "C:\\Users\\me\\Downloads\\bank.CSV", HEADER).andExpect(status().isCreated());

            assertThat(jdbc.queryForObject("SELECT filename FROM import_batches WHERE user_id = ?",
                    String.class, alice.id())).isEqualTo("bank.CSV");
        }
    }

    @Nested
    class Duplicates {

        private static final String FILE = HEADER + """
                Expense,2026-01-02 13:35:00,,Coffee,45,Drink,,Debit card,
                Expense,2026-01-02 13:35:00,,Coffee,45,Drink,,Debit card,
                Expense,2026-01-02 18:00:00,,Taxi,80,Car,,Debit card,
                """;

        @Test
        void identicalRowsInOneFileAreDifferentTransactions() throws Exception {
            upload(alice, FILE).andExpect(jsonPath("$.records_imported").value(3));

            assertThat(countTransactions(aliceCard)).isEqualTo(3);
        }

        @Test
        void uploadingTheSameFileTwiceImportsNothingNew() throws Exception {
            upload(alice, FILE).andExpect(status().isCreated());

            upload(alice, FILE)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.records_imported").value(0))
                    .andExpect(jsonPath("$.records_duplicated").value(3))
                    .andExpect(jsonPath("$.accounts_created", hasSize(0)));
            assertThat(countTransactions(aliceCard)).isEqualTo(3);
        }

        @Test
        void aLongerFileOnlyAddsTheNewRows() throws Exception {
            upload(alice, FILE).andExpect(status().isCreated());

            upload(alice, FILE + "Expense,2026-01-02 13:35:00,,Coffee,45,,Drink,Debit card,\n")
                    .andExpect(jsonPath("$.records_imported").value(1))
                    .andExpect(jsonPath("$.records_duplicated").value(3));
        }

        @Test
        void deletedTransactionsAreNotImportedAgain() throws Exception {
            upload(alice, FILE).andExpect(status().isCreated());
            jdbc.update("UPDATE transactions SET deleted_at = now() WHERE name = 'Taxi'");

            upload(alice, FILE).andExpect(jsonPath("$.records_imported").value(0));
            assertThat(countTransactions(aliceCard)).isEqualTo(2);
        }

        @Test
        void sameRowsInAnotherAccountAreNotDuplicates() throws Exception {
            upload(alice, FILE).andExpect(status().isCreated());

            upload(alice, FILE.replace("Debit card", "Cash"))
                    .andExpect(jsonPath("$.records_imported").value(3))
                    .andExpect(jsonPath("$.accounts_created[0].name").value("Cash"));
        }
    }

    @Nested
    class InvalidRows {

        @Test
        void listsEveryErrorAndImportsNothing() throws Exception {
            upload(alice, HEADER + """
                    Expense,2026-01-02 13:35:00,,Valid row,100,Food,,New account,
                    Transfer,2026-01-02 13:35:00,,Bad type,100,Food,,Debit card,
                    Expense,2026-02-30 10:00:00,,Bad date,abc,Food,,Debit card,
                    Expense,2026-01-02 13:35:00,,No account,100,Food,,,
                    Expense,2026-01-02 13:35:00,,,0,Groceries,,Debit card,
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("CSV_INVALID_ROWS"))
                    .andExpect(jsonPath("$.detail").value("4 rows have errors. Nothing was imported."))
                    .andExpect(jsonPath("$.errors", hasSize(7)))
                    .andExpect(jsonPath("$.errors[0].row").value(2))
                    .andExpect(jsonPath("$.errors[0].field").value("Type"))
                    .andExpect(jsonPath("$.errors[1].row").value(3))
                    .andExpect(jsonPath("$.errors[1].field").value("Date"))
                    .andExpect(jsonPath("$.errors[2].row").value(3))
                    .andExpect(jsonPath("$.errors[2].field").value("Amount"))
                    .andExpect(jsonPath("$.errors[3].row").value(4))
                    .andExpect(jsonPath("$.errors[3].message").value("Account is required."))
                    .andExpect(jsonPath("$.errors[4].row").value(5))
                    .andExpect(jsonPath("$.errors[4].field").value("Name"))
                    .andExpect(jsonPath("$.errors[5].field").value("Amount"))
                    .andExpect(jsonPath("$.errors[5].message").value("must not be zero"))
                    .andExpect(jsonPath("$.errors[6].field").value("Category Group"))
                    .andExpect(jsonPath("$.errors[6].message").value("Unknown category group."));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id = ?",
                    Integer.class, alice.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id = ?",
                    Integer.class, alice.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM import_batches WHERE user_id = ?",
                    Integer.class, alice.id())).isZero();
        }

        @Test
        void zeroAmountIsRejected() throws Exception {
            upload(alice, HEADER + "Expense,2026-01-02 13:35:00,,Zero,0.00,,,Debit card,\n")
                    .andExpect(jsonPath("$.errors[0].field").value("Amount"))
                    .andExpect(jsonPath("$.errors[0].message").value("must not be zero"));
        }

        @Test
        void wrongNumberOfColumns() throws Exception {
            upload(alice, HEADER + "Expense,2026-01-02 13:35:00,,Short row,100\n")
                    .andExpect(jsonPath("$.code").value("CSV_INVALID_ROWS"))
                    .andExpect(jsonPath("$.errors[0].row").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("row"))
                    .andExpect(jsonPath("$.errors[0].message").value("Expected 9 columns, found 5."));
        }

        @Test
        void valuesLongerThanTheColumnAreRejected() throws Exception {
            upload(alice, HEADER + "Expense,2026-01-02 13:35:00,,%s,100,,,Debit card,\n".formatted("x".repeat(256)))
                    .andExpect(jsonPath("$.errors[0].field").value("Name"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be at most 255 characters."));
        }

        @Test
        void reportsAtMostOneHundredErrors() throws Exception {
            String rows = IntStream.range(0, 150)
                    .mapToObj(i -> "Transfer,2026-01-02 13:35:00,,Row,100,,,Debit card,")
                    .collect(Collectors.joining("\n"));

            upload(alice, HEADER + rows)
                    .andExpect(jsonPath("$.errors", hasSize(100)))
                    .andExpect(jsonPath("$.detail").value(
                            "150 rows have errors. Nothing was imported. Only the first 100 errors are listed."));
        }
    }

    @Nested
    class InvalidFile {

        @Test
        void missingFile() throws Exception {
            mvc.perform(multipart(UPLOAD).with(as(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("file"))
                    .andExpect(jsonPath("$.errors[0].message").value("is required"));
        }

        @Test
        void wrongExtension() throws Exception {
            expectFileError(upload(alice, "data.xlsx", HEADER), "must be a .csv file");
        }

        @Test
        void emptyFile() throws Exception {
            expectFileError(upload(alice, ""), "must not be empty");
        }

        @Test
        void wrongHeader() throws Exception {
            expectFileError(upload(alice, "Date,Name,Amount\n2026-01-02,Shop,10\n"),
                    "header must be: Type, Date, Time, Name, Amount, Category Group, Category, Account, Notes"
                            + " (optionally followed by Tags)");
        }

        @Test
        void notUtf8() throws Exception {
            byte[] latin1 = (HEADER + "Expense,2026-01-02 13:35:00,,Café,10,,,Debit card,\n")
                    .getBytes(StandardCharsets.ISO_8859_1);

            expectFileError(upload(alice, "bank.csv", latin1), "must be UTF-8 encoded.");
        }

        @Test
        void unclosedQuote() throws Exception {
            expectFileError(upload(alice, HEADER + "Expense,2026-01-02 13:35:00,,\"Shop,10,,,Debit card,\n"),
                    "is not a valid CSV file (check the quotes).");
        }

        @Test
        void fileLargerThanOneMebibyte() throws Exception {
            byte[] big = new byte[(int) TransactionController.MAX_CSV_BYTES + 1];
            Arrays.fill(big, (byte) 'a');

            upload(alice, "big.csv", big)
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        }

        @Test
        void requiresMultipart() throws Exception {
            mvc.perform(post(UPLOAD).with(as(alice)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(multipart(UPLOAD).file(csvFile("bank.csv", HEADER.getBytes(StandardCharsets.UTF_8))))
                    .andExpect(status().isUnauthorized());
        }

        private void expectFileError(ResultActions result, String message) throws Exception {
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("file"))
                    .andExpect(jsonPath("$.errors[0].message").value(message));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM import_batches", Integer.class)).isZero();
        }
    }

    private ResultActions upload(TestUser user, String csv) throws Exception {
        return upload(user, "bank.csv", csv);
    }

    private ResultActions upload(TestUser user, String filename, String csv) throws Exception {
        return upload(user, filename, csv.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions upload(TestUser user, String filename, byte[] content) throws Exception {
        return mvc.perform(multipart(UPLOAD).file(csvFile(filename, content)).with(as(user)));
    }

    private static MockMultipartFile csvFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "text/csv", content);
    }

    private int countTransactions(UUID accountId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE account_id = ? AND deleted_at IS NULL",
                Integer.class, accountId);
    }

    private void insertManual(UUID accountId, String datetime, String name, String amount) {
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, type, datetime, name, amount, category_group,
                                          source, category_group_source)
                VALUES (?, ?, 'EXPENSE', ?, ?, ?, 'Food', 'MANUAL', 'USER')
                """, alice.id(), accountId, java.time.LocalDateTime.parse(datetime), name, new java.math.BigDecimal(amount));
    }

    private UUID insertAccount(TestUser owner, String name) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, owner.id(), name);
    }
}
