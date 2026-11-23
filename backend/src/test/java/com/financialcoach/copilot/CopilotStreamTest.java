package com.financialcoach.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import reactor.core.publisher.Flux;

/** POST /copilot/chat/stream with a fake chat model that streams its answer in chunks. */
class CopilotStreamTest extends ApiIntegrationTest {

    @MockitoBean
    private GoogleGenAiChatModel chatModel;

    @Autowired
    private RateLimiter rateLimiter;

    private TestUser alice;
    private UUID account;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        alice = users.client();
        account = jdbc.queryForObject("INSERT INTO accounts (user_id, name) VALUES (?, 'Debit') RETURNING id",
                UUID.class, alice.id());
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        // Like Gemini: every chunk repeats the usage so far; the last one has the totals.
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> Flux.just(
                chunk("En septiembre ", new DefaultUsage(120, 3, 3)),
                chunk("gastaste ", new DefaultUsage(120, 5, 5)),
                chunk("$10.00.", new DefaultUsage(120, 8, 150))));
    }

    @Test
    void sendsTheAnswerInDeltasAndThenTheUsage() throws Exception {
        String body = stream(alice, null);

        assertThat(body).containsSubsequence(
                "event:delta", "data:{\"text\":\"En septiembre \"}",
                "event:delta", "data:{\"text\":\"gastaste \"}",
                "event:delta", "data:{\"text\":\"$10.00.\"}",
                "event:done",
                "\"provider\":\"google-genai\"", "\"model\":\"fake-model\"",
                // Only the last chunk's usage counts: 120 / 8 / 150, not the sum of every chunk.
                "\"usage\":{\"input_tokens\":120,\"output_tokens\":8,\"total_tokens\":150,\"llm_calls\":1}");
        assertThat(body).doesNotContain("event:error");
    }

    @Test
    void aFailureBecomesAnErrorEvent() throws Exception {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("Failed to generate content")));

        assertThat(stream(alice, null))
                .contains("event:error")
                .contains("\"code\":\"COPILOT_UNAVAILABLE\"")
                .doesNotContain("event:done");
    }

    @Test
    void anotherUsersAccountIsStillAPlain404() throws Exception {
        TestUser bob = users.create("bob@example.com", alice.role(), true);

        mvc.perform(streamRequest(bob, null)).andExpect(status().isNotFound());
    }

    @Test
    void theStreamedAnswerIsRememberedForTheNextMessage() throws Exception {
        UUID conversation = UUID.randomUUID();
        stream(alice, conversation);

        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> Flux.just(chunk("Ok.", null)));
        stream(alice, conversation);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.times(2)).stream(prompts.capture());
        List<String> secondCall = prompts.getValue().getInstructions().stream().map(Message::getText).toList();
        assertThat(secondCall).contains("How much?", "En septiembre gastaste $10.00.");
    }

    /** Runs the request and returns the SSE body once the stream ends. */
    private String stream(TestUser user, UUID conversationId) throws Exception {
        MvcResult started = mvc.perform(streamRequest(user, conversationId))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString();
    }

    private RequestBuilder streamRequest(TestUser user, UUID conversationId) {
        String conversation = conversationId == null ? "" : ", \"conversation_id\": \"" + conversationId + "\"";
        return post(CopilotController.STREAM_PATH).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON)
                .content("{\"message\": \"How much?\", \"account_id\": \"%s\"%s}".formatted(account, conversation));
    }

    private static ChatResponse chunk(String text, DefaultUsage usage) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model("fake-model");
        if (usage != null) {
            metadata.usage(usage);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata.build());
    }
}
