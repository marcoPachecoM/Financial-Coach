package com.financialcoach.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** The categorization pipeline end to end; the LLM classifier is a mock. */
class CategorizationTest extends ApiIntegrationTest {

    private static final String HEADER = "Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes\n";

    @MockitoBean
    private MerchantClassifier classifier;

    @Autowired
    private CategorizationService categorization;

    @Autowired
    private RateLimiter rateLimiter;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
        jdbc.update("""
                INSERT INTO merchant_category_cache (merchant_name, category_group, source)
                VALUES ('la tiendita', 'Food', 'LLM'), ('oxxo centro', 'Snacks', 'RULE')
                """);
    }

    @Test
    void theUsersChoiceWinsThenTheCacheThenTheLlmThenOther() {
        categorization.rememberUserChoice(alice.id(), "La Tiendita", CategoryGroup.GIFT);
        when(classifier.classify(anyCollection())).thenReturn(Map.of("cinepolis", CategoryGroup.ENTERTAINMENT));

        Categorizations result = categorization.categorizeAll(alice.id(),
                List.of("LA TIENDITA", "  OXXO #8812 - Centro ", "Cinepolis", "Unknown merchant", "12345"));

        assertThat(result.of("LA TIENDITA")).isEqualTo(new CategoryGroupAssignment(CategoryGroup.GIFT, CategoryGroupSource.USER));
        assertThat(result.of("  OXXO #8812 - Centro ")).isEqualTo(new CategoryGroupAssignment(CategoryGroup.SNACKS, CategoryGroupSource.RULE));
        assertThat(result.of("Cinepolis")).isEqualTo(new CategoryGroupAssignment(CategoryGroup.ENTERTAINMENT, CategoryGroupSource.LLM));
        assertThat(result.of("Unknown merchant")).isEqualTo(new CategoryGroupAssignment(CategoryGroup.OTHER, CategoryGroupSource.RULE));
        assertThat(result.fallbackNames()).containsExactlyInAnyOrder("Unknown merchant", "12345");
        // Only what nobody knew reached the LLM (and never a name without letters).
        assertThat(sentToLlm()).containsExactlyInAnyOrder("cinepolis", "unknown merchant");
    }

    @Test
    void oneImportWithManyNewMerchantsIsOneCallAndTheAnswersAreCached() throws Exception {
        String rows = IntStream.rangeClosed(1, 50)
                .mapToObj(i -> "Expense,2026-09-%02d 10:00:00,10:00,Shop %s,10,,,Card,\n".formatted(i % 28 + 1, word(i)))
                .collect(Collectors.joining());
        when(classifier.classify(anyCollection())).thenAnswer(call -> {
            Collection<String> keys = call.getArgument(0);
            return keys.stream().collect(Collectors.toMap(key -> key, key -> CategoryGroup.SHOPPING));
        });

        upload(alice, "bank.csv", rows)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_imported").value(50))
                .andExpect(jsonPath("$.records_categorized").value(50));

        verify(classifier, times(1)).classify(anyCollection());
        assertThat(sentToLlm()).hasSize(50);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_category_cache WHERE source = 'LLM'", Integer.class))
                .isEqualTo(51); // the 50 new ones + la tiendita
        // Another user importing the same merchants: all from the cache, no new call.
        upload(bob, "bank.csv", rows).andExpect(jsonPath("$.records_categorized").value(50));
        verify(classifier, times(1)).classify(anyCollection());
    }

    @Test
    void rowsWithAGroupInTheFileNeverReachTheLlm() throws Exception {
        upload(alice, "export.csv", """
                Expense,2026-09-05 10:00:00,10:00,Cinepolis,100,Entertainment,,Card,
                Expense,2026-09-06 10:00:00,10:00,La Tiendita,50,,,Card,
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_categorized").value(1));

        verify(classifier, never()).classify(anyCollection());
        assertThat(jdbc.queryForList("SELECT category_group || '/' || category_group_source FROM transactions ORDER BY datetime",
                String.class)).containsExactly("Entertainment/CSV", "Food/LLM");
    }

    @Test
    void rowsWithOtherInTheFileAreCategorizedLikeEmptyOnes() throws Exception {
        when(classifier.classify(anyCollection())).thenReturn(Map.of("cinepolis", CategoryGroup.ENTERTAINMENT));

        upload(alice, "bank.csv", """
                Expense,2026-09-05 10:00:00,10:00,Cinepolis,100,Other,Movies,Card,
                Expense,2026-09-06 10:00:00,10:00,La Tiendita,50,Other,,Card,
                Expense,2026-09-07 10:00:00,10:00,Unknown merchant,20,Other,,Card,
                Expense,2026-09-08 10:00:00,10:00,Liverpool,30,Shopping,,Card,
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_categorized").value(2));

        assertThat(sentToLlm()).containsExactlyInAnyOrder("cinepolis", "unknown merchant");
        assertThat(jdbc.queryForList("""
                SELECT category_group || '/' || category_group_source || '/' || coalesce(category, '')
                FROM transactions ORDER BY datetime""", String.class))
                .containsExactly("Entertainment/LLM/Movies", "Food/LLM/", "Other/CSV/", "Shopping/CSV/");
    }

    @Test
    void anOtherRowStaysOtherWhenTheUserChoseOtherForThatMerchant() throws Exception {
        categorization.rememberUserChoice(alice.id(), "Cinepolis", CategoryGroup.OTHER);

        upload(alice, "bank.csv", "Expense,2026-09-05 10:00:00,10:00,CINEPOLIS,100,Other,,Card,\n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_categorized").value(0));

        verify(classifier, never()).classify(anyCollection());
        assertThat(jdbc.queryForObject("SELECT category_group FROM transactions", String.class)).isEqualTo("Other");
    }

    @Test
    void aFailingLlmLeavesOtherAndTheImportSucceeds() throws Exception {
        // The real classifier never throws; an empty answer is what a failure looks like.
        when(classifier.classify(anyCollection())).thenReturn(Map.of());

        upload(alice, "bank.csv", "Expense,2026-09-05 10:00:00,10:00,Cinepolis,100,,,Card,\n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.records_categorized").value(0));
        assertThat(jdbc.queryForObject("SELECT category_group FROM transactions", String.class)).isEqualTo("Other");
    }

    @Test
    void aCorrectionIsLearnedForThatUserOnly() throws Exception {
        UUID card = account(alice);
        String id = idOf(create(alice, card, "Oxxo Centro", null)); // Snacks from the shared cache
        mvc.perform(patch("/api/v1/transactions/" + id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_group\": \"Food\"}"))
                .andExpect(status().isOk());

        create(alice, card, "OXXO CENTRO #77", null).andExpect(jsonPath("$.category_group").value("Food"));
        create(bob, account(bob), "Oxxo Centro", null).andExpect(jsonPath("$.category_group").value("Snacks"));
        assertThat(jdbc.queryForObject("SELECT category_group FROM merchant_category_cache WHERE merchant_name = 'oxxo centro'",
                String.class)).isEqualTo("Snacks"); // the shared cache is not touched
    }

    @Test
    void theFormCallsTheLlmOnlyWithoutAChosenGroup() throws Exception {
        UUID card = account(alice);
        when(classifier.classify(anyCollection())).thenReturn(Map.of("cinepolis", CategoryGroup.ENTERTAINMENT));

        create(alice, card, "Cinepolis", null).andExpect(jsonPath("$.category_group").value("Entertainment"));
        create(alice, card, "Liverpool", "Shopping").andExpect(jsonPath("$.category_group").value("Shopping"));
        create(alice, card, "Liverpool Centro", null);

        verify(classifier, times(2)).classify(anyCollection()); // Cinepolis, then "liverpool centro"
        create(alice, card, "LIVERPOOL", null).andExpect(jsonPath("$.category_group").value("Shopping"));
        verify(classifier, times(2)).classify(anyCollection()); // the choice was remembered
    }

    @SuppressWarnings("unchecked")
    private List<String> sentToLlm() {
        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(classifier, org.mockito.Mockito.atLeastOnce()).classify(keys.capture());
        return keys.getAllValues().stream().flatMap(Collection::stream).toList();
    }

    private ResultActions upload(TestUser user, String filename, String rows) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv",
                (HEADER + rows).getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/v1/transactions/upload-csv").file(file).with(as(user)));
    }

    private ResultActions create(TestUser user, UUID accountId, String name, String group) throws Exception {
        String json = """
                {"type": "EXPENSE", "datetime": "2026-09-05T10:00", "name": "%s", "amount": "10",
                 "account_id": "%s"%s}
                """.formatted(name, accountId, group == null ? "" : ", \"category_group\": \"" + group + "\"");
        return mvc.perform(post("/api/v1/transactions").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID account(TestUser owner) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, 'Card') RETURNING id",
                UUID.class, owner.id());
    }

    private static String idOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Distinct letters-only names (digits are removed when normalizing). */
    private static String word(int i) {
        return "" + (char) ('a' + i / 26) + (char) ('a' + i % 26) + "x";
    }
}
