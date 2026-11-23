package com.financialcoach.copilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.FixedClockConfiguration;
import com.financialcoach.support.FixedClockConfiguration.MutableClock;
import com.financialcoach.support.TestUsers.TestUser;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.RequestBuilder;

/** Small limits so the test reaches them: 2 messages per minute, 3 per day. */
@Import(FixedClockConfiguration.class)
@TestPropertySource(properties = {
        "financial-coach.copilot.requests-per-minute=2",
        "financial-coach.copilot.daily-limit=3"})
class CopilotRateLimitTest extends ApiIntegrationTest {

    @MockitoBean
    private GoogleGenAiChatModel chatModel;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private MutableClock clock;

    private TestUser alice;
    private UUID account;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        clock.setToday(FixedClockConfiguration.TODAY);
        alice = users.client();
        account = jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, 'Debit') RETURNING id",
                UUID.class, alice.id());
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
    }

    @Test
    void perMinuteLimitReturns429UntilTheNextMinute() throws Exception {
        mvc.perform(chat(alice)).andExpect(status().isOk());
        mvc.perform(chat(alice)).andExpect(status().isOk());

        // The clock is at 10:00:00, so the minute window ends in 60 seconds.
        mvc.perform(chat(alice))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("Too many messages. Try again in 60 seconds."));

        clock.advance(Duration.ofMinutes(1));
        mvc.perform(chat(alice)).andExpect(status().isOk());
    }

    @Test
    void dailyLimitLastsUntilLocalMidnight() throws Exception {
        mvc.perform(chat(alice)).andExpect(status().isOk());
        mvc.perform(chat(alice)).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        mvc.perform(chat(alice)).andExpect(status().isOk());

        // 10:01 → midnight is 13 h 59 min away.
        mvc.perform(chat(alice))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", Long.toString(Duration.ofHours(13).plusMinutes(59).toSeconds())))
                .andExpect(jsonPath("$.detail").value("You reached the daily limit of 3 copilot messages. It resets at midnight."));

        clock.setToday(FixedClockConfiguration.TODAY.plusDays(1));
        mvc.perform(chat(alice)).andExpect(status().isOk());
    }

    @Test
    void eachUserHasItsOwnCounter() throws Exception {
        mvc.perform(chat(alice)).andExpect(status().isOk());
        mvc.perform(chat(alice)).andExpect(status().isOk());
        mvc.perform(chat(alice)).andExpect(status().isTooManyRequests());

        TestUser bob = users.create("bob@example.com", alice.role(), true);
        account = jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, 'Debit') RETURNING id",
                UUID.class, bob.id());
        mvc.perform(chat(bob)).andExpect(status().isOk());
    }

    private RequestBuilder chat(TestUser user) {
        return post(CopilotController.CHAT_PATH).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Hi\", \"account_id\": \"%s\"}".formatted(account));
    }
}
