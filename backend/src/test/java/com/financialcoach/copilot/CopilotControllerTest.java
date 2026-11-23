package com.financialcoach.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialcoach.shared.web.RateLimiter;
import com.financialcoach.support.ApiIntegrationTest;
import com.financialcoach.support.TestUsers.TestUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The whole endpoint with a fake chat model: Gemini is never called. */
class CopilotControllerTest extends ApiIntegrationTest {

    private static final String CHAT = "/api/v1/copilot/chat";

    @MockitoBean
    private GoogleGenAiChatModel chatModel;

    @Autowired
    private RateLimiter rateLimiter;

    private TestUser alice;
    private UUID aliceAccount;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        alice = users.client();
        aliceAccount = account(alice, "Debit");
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> answer("You spent $10.00."));
    }

    @Test
    void answersWithTheModelText() throws Exception {
        mvc.perform(chat(alice, aliceAccount, "How much did I spend?", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("You spent $10.00."))
                .andExpect(jsonPath("$.provider").value("google-genai"))
                .andExpect(jsonPath("$.model").value("fake-model"))
                .andExpect(jsonPath("$.usage.input_tokens").value(100))
                .andExpect(jsonPath("$.usage.output_tokens").value(20))
                .andExpect(jsonPath("$.usage.total_tokens").value(120))
                .andExpect(jsonPath("$.usage.llm_calls").value(1));
    }

    @Test
    void usageAddsUpEveryCallOfTheToolLoop() throws Exception {
        // 1st call: the model asks for the summary tool. 2nd call: it answers with the tool result.
        // Totals include thinking tokens, so they are larger than input + output.
        AssistantMessage toolCall = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "summary",
                        "{\"startDate\": \"2026-09-01\", \"endDate\": \"2026-09-30\"}")))
                .build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolCall)),
                        ChatResponseMetadata.builder().model("fake-model").usage(new DefaultUsage(100, 10, 130)).build()))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("You spent $0.00."))),
                        ChatResponseMetadata.builder().model("fake-model").usage(new DefaultUsage(200, 30, 250)).build()));

        mvc.perform(chat(alice, aliceAccount, "How much in September?", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("You spent $0.00."))
                .andExpect(jsonPath("$.usage.input_tokens").value(300))
                .andExpect(jsonPath("$.usage.output_tokens").value(40))
                .andExpect(jsonPath("$.usage.total_tokens").value(380))
                .andExpect(jsonPath("$.usage.llm_calls").value(2));
    }

    @Test
    void providerErrorsBecome503WithoutHanging() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Failed to generate content", new RuntimeException("503 UNAVAILABLE")));

        mvc.perform(chat(alice, aliceAccount, "Hi", null))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COPILOT_UNAVAILABLE"));
    }

    @Test
    void aQuotaErrorSaysWhenToTryAgain() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Failed to generate content",
                new RuntimeException("429 . You exceeded your current quota. Please retry in 6h50m44.913668754s.")));

        mvc.perform(chat(alice, aliceAccount, "Hi", null))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COPILOT_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("The AI model's usage quota is used up. Try again in 6 h 51 min."));
    }

    @Test
    void anotherUsersAccountIs404AndTheModelIsNotCalled() throws Exception {
        TestUser bob = users.create("bob@example.com", alice.role(), true);

        mvc.perform(chat(bob, aliceAccount, "Hi", null))
                .andExpect(status().isNotFound());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void sameConversationSendsThePreviousMessagesBack() throws Exception {
        UUID conversation = UUID.randomUUID();
        mvc.perform(chat(alice, aliceAccount, "How much in September?", conversation)).andExpect(status().isOk());
        mvc.perform(chat(alice, aliceAccount, "And in August?", conversation)).andExpect(status().isOk());

        List<String> secondCall = texts(lastPrompt(2));
        assertThat(secondCall).contains("How much in September?", "You spent $10.00.", "And in August?");
    }

    @Test
    void withoutConversationIdEachMessageStandsAlone() throws Exception {
        mvc.perform(chat(alice, aliceAccount, "How much in September?", null)).andExpect(status().isOk());
        mvc.perform(chat(alice, aliceAccount, "And in August?", null)).andExpect(status().isOk());

        assertThat(texts(lastPrompt(2))).doesNotContain("How much in September?");
    }

    @Test
    void historyIsNotSharedAcrossAccountsOrUsers() throws Exception {
        UUID conversation = UUID.randomUUID();
        mvc.perform(chat(alice, aliceAccount, "Secret question", conversation)).andExpect(status().isOk());

        UUID cash = account(alice, "Cash");
        mvc.perform(chat(alice, cash, "Other account", conversation)).andExpect(status().isOk());
        assertThat(texts(lastPrompt(2))).doesNotContain("Secret question");

        TestUser bob = users.create("bob@example.com", alice.role(), true);
        UUID bobAccount = account(bob, "Debit");
        mvc.perform(chat(bob, bobAccount, "Bob here", conversation)).andExpect(status().isOk());
        assertThat(texts(lastPrompt(3))).doesNotContain("Secret question");
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(TestUser user, UUID accountId, String message,
                                                                     UUID conversationId) {
        String conversation = conversationId == null ? "" : ", \"conversation_id\": \"" + conversationId + "\"";
        return post(CHAT).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"%s\", \"account_id\": \"%s\"%s}".formatted(message, accountId, conversation));
    }

    /** The prompt of the last of {@code calls} calls to the model. */
    private Prompt lastPrompt(int calls) {
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(calls)).call(prompts.capture());
        return prompts.getValue();
    }

    private static List<String> texts(Prompt prompt) {
        return prompt.getInstructions().stream().map(Message::getText).toList();
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().model("fake-model").usage(new DefaultUsage(100, 20)).build());
    }

    private UUID account(TestUser owner, String name) {
        return jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, owner.id(), name);
    }
}
