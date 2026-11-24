package com.financialcoach.goals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import com.financialcoach.users.UserRole;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** {@code /goals} and their contributions. "Today" is 2026-11-21. */
@Import(FixedClockConfiguration.class)
class GoalsTest extends ApiIntegrationTest {

    private static final String GOALS = "/api/v1/goals";

    @Autowired
    private MutableClock clock;

    @Autowired
    private EntityManager entityManager;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() {
        clock.setToday(FixedClockConfiguration.TODAY);
        alice = users.create("alice", UserRole.CLIENT, true);
        bob = users.create("bob", UserRole.CLIENT, true);
    }

    @Test
    void createsAGoalWithWhatItNeedsPerMonth() throws Exception {
        create(alice, "{\"name\": \" Trip to Japan \", \"target_amount\": \"40000\", \"target_date\": \"2027-06-30\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(GOALS + "/")))
                .andExpect(jsonPath("$.name").value("Trip to Japan"))
                .andExpect(jsonPath("$.target_amount").value("40000.00"))
                .andExpect(jsonPath("$.target_date").value("2027-06-30"))
                .andExpect(jsonPath("$.saved").value("0.00"))
                .andExpect(jsonPath("$.remaining").value("40000.00"))
                .andExpect(jsonPath("$.percentage").value("0.00"))
                .andExpect(jsonPath("$.monthly_pace").value("0.00"))
                .andExpect(jsonPath("$.required_monthly").value("5714.29")) // 40000 over 7 months
                .andExpect(jsonPath("$.projected_date").value(nullValue()))
                .andExpect(jsonPath("$.status").value("BEHIND"));
    }

    @Test
    void contributionsUpdateTheProgressAndTheHistory() throws Exception {
        String id = idOf(create(alice, "{\"name\": \"Emergency fund\", \"target_amount\": \"10000\"}"));

        contribute(alice, id, "{\"amount\": \"2000\", \"date\": \"2026-10-01\", \"note\": \"Bonus\"}")
                .andExpect(status().isCreated());
        contribute(alice, id, "{\"amount\": \"2000\"}") // default date: today
                .andExpect(jsonPath("$.saved").value("4000.00"))
                .andExpect(jsonPath("$.percentage").value("40.00"))
                .andExpect(jsonPath("$.status").value("NO_TARGET_DATE"))
                .andExpect(jsonPath("$.projected_date").isNotEmpty());
        contribute(alice, id, "{\"amount\": \"-500\", \"note\": \"Car repair\"}")
                .andExpect(jsonPath("$.saved").value("3500.00"));

        mvc.perform(get(GOALS + "/" + id + "/contributions").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].amount", contains("-500.00", "2000.00", "2000.00")))
                .andExpect(jsonPath("$[*].date", contains("2026-11-21", "2026-11-21", "2026-10-01")))
                .andExpect(jsonPath("$[2].note").value("Bonus"));
    }

    @Test
    void deletingAContributionRecomputesTheProgress() throws Exception {
        String id = idOf(create(alice, "{\"name\": \"Bike\", \"target_amount\": \"5000\"}"));
        contribute(alice, id, "{\"amount\": \"1000\", \"date\": \"2026-11-01\"}");
        contribute(alice, id, "{\"amount\": \"-800\"}");
        String deposit = contributionIds(id)[1];
        String withdrawal = contributionIds(id)[0];

        // The withdrawal needs the deposit: deleting the deposit first would leave the goal below zero.
        mvc.perform(delete(GOALS + "/" + id + "/contributions/" + deposit).with(as(alice)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(GOALS + "/" + id + "/contributions/" + withdrawal).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value("1000.00"));
    }

    @Test
    void rejectsInvalidValues() throws Exception {
        create(alice, "{\"name\": \"Trip\", \"target_amount\": \"0\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("target_amount"));
        create(alice, "{\"name\": \"Trip\", \"target_amount\": \"100\", \"target_date\": \"2026-11-20\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("target_date"));
        create(alice, "{\"name\": \" \", \"target_amount\": \"100\"}").andExpect(status().isBadRequest());

        String id = idOf(create(alice, "{\"name\": \"Trip\", \"target_amount\": \"1000\"}"));
        contribute(alice, id, "{\"amount\": \"100\", \"date\": \"2026-11-22\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        contribute(alice, id, "{\"amount\": \"0\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        contribute(alice, id, "{\"amount\": \"-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("cannot withdraw more than what was saved"));
        patchJson(alice, id, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void namesAreUniquePerUserIgnoringCase() throws Exception {
        create(alice, "{\"name\": \"Trip\", \"target_amount\": \"1000\"}");
        String car = idOf(create(alice, "{\"name\": \"Car\", \"target_amount\": \"1000\"}"));

        create(alice, "{\"name\": \"TRIP\", \"target_amount\": \"5\"}").andExpect(status().isConflict());
        patchJson(alice, car, "{\"name\": \"trip\"}").andExpect(status().isConflict());
        create(bob, "{\"name\": \"Trip\", \"target_amount\": \"1000\"}").andExpect(status().isCreated());
    }

    @Test
    void patchChangesTheFieldsSentAndANullDateRemovesIt() throws Exception {
        String id = idOf(create(alice, "{\"name\": \"Trip\", \"target_amount\": \"1000\", \"target_date\": \"2027-01-31\"}"));

        patchJson(alice, id, "{\"name\": \"Trip to Japan\", \"target_amount\": \"2000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trip to Japan"))
                .andExpect(jsonPath("$.target_amount").value("2000.00"))
                .andExpect(jsonPath("$.target_date").value("2027-01-31"));
        patchJson(alice, id, "{\"target_date\": null}")
                .andExpect(jsonPath("$.target_date").value(nullValue()))
                .andExpect(jsonPath("$.status").value("NO_TARGET_DATE"));
    }

    @Test
    void listsTheGoalsNeedingAttentionFirst() throws Exception {
        String done = idOf(create(alice, "{\"name\": \"Done\", \"target_amount\": \"100\"}"));
        contribute(alice, done, "{\"amount\": \"100\"}");
        create(alice, "{\"name\": \"Someday\", \"target_amount\": \"100\"}");
        create(alice, "{\"name\": \"Behind\", \"target_amount\": \"100\", \"target_date\": \"2027-01-31\"}");
        String late = idOf(create(alice, "{\"name\": \"Late\", \"target_amount\": \"100\", \"target_date\": \"2026-12-31\"}"));
        // The API rejects a past date: set it directly, then drop the cached entity so the list reads it.
        jdbc.update("UPDATE savings_goals SET target_date = '2026-11-01' WHERE id = ?::uuid", late);
        entityManager.clear();

        mvc.perform(get(GOALS).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Late", "Behind", "Someday", "Done")))
                .andExpect(jsonPath("$[*].status", contains("OVERDUE", "BEHIND", "NO_TARGET_DATE", "COMPLETED")));
        mvc.perform(get(GOALS).with(as(bob))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void deletingAGoalDeletesItsContributions() throws Exception {
        String id = idOf(create(alice, "{\"name\": \"Trip\", \"target_amount\": \"1000\"}"));
        contribute(alice, id, "{\"amount\": \"100\"}");

        mvc.perform(delete(GOALS + "/" + id).with(as(alice))).andExpect(status().isNoContent());

        mvc.perform(get(GOALS + "/" + id).with(as(alice))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM goal_contributions", Integer.class)).isZero();
    }

    @Test
    void anotherUsersGoalIsNotFound() throws Exception {
        String id = idOf(create(alice, "{\"name\": \"Trip\", \"target_amount\": \"1000\"}"));
        contribute(alice, id, "{\"amount\": \"100\"}");
        String contribution = contributionIds(id)[0];
        String bobsGoal = idOf(create(bob, "{\"name\": \"Car\", \"target_amount\": \"1000\"}"));

        mvc.perform(get(GOALS + "/" + id).with(as(bob))).andExpect(status().isNotFound());
        patchJson(bob, id, "{\"name\": \"Mine\"}").andExpect(status().isNotFound());
        contribute(bob, id, "{\"amount\": \"100\"}").andExpect(status().isNotFound());
        mvc.perform(get(GOALS + "/" + id + "/contributions").with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(delete(GOALS + "/" + id).with(as(bob))).andExpect(status().isNotFound());
        // A contribution of another goal, even under one's own goal id.
        mvc.perform(delete(GOALS + "/" + bobsGoal + "/contributions/" + contribution).with(as(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theListRejectsUnknownParameters() throws Exception {
        mvc.perform(get(GOALS).param("status", "BEHIND").with(as(alice))).andExpect(status().isBadRequest());
    }

    private ResultActions create(TestUser user, String json) throws Exception {
        return mvc.perform(post(GOALS).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchJson(TestUser user, String id, String json) throws Exception {
        return mvc.perform(patch(GOALS + "/" + id).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions contribute(TestUser user, String id, String json) throws Exception {
        return mvc.perform(post(GOALS + "/" + id + "/contributions").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Ids of the goal's contributions, newest first. */
    private String[] contributionIds(String goalId) throws Exception {
        String body = mvc.perform(get(GOALS + "/" + goalId + "/contributions").with(as(alice)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<java.util.List<String>>read(body, "$[*].id").toArray(String[]::new);
    }

    private static String idOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
