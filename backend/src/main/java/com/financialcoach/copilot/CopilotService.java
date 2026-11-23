package com.financialcoach.copilot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.error.ApiException;
import com.financialcoach.shared.error.ServiceUnavailableException;
import com.financialcoach.shared.error.UpstreamUnavailableException;
import com.financialcoach.transactions.RecurringReport;
import com.financialcoach.transactions.TransactionStatistics;

import reactor.core.publisher.Flux;

@Service
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    private static final String SYSTEM_PROMPT = """
            You are a personal finance coach inside a budgeting app. Today is %1$s.
            Rules:
            - Use only the provided tools to get data. Never invent amounts, dates or merchants.
            - If no tool can answer the question, say so briefly and suggest what you can answer.
            - Resolve relative dates ("this month", "last September") from today's date. A month
              without a year means the most recent one that is not in the future.
            - Always write amounts like %2$s, whatever the user's language. Do not name a currency
              (no USD, MXN or EUR), do not convert amounts and do not use another symbol.
            - Plain text only, no Markdown (no asterisks, no headings); use "- " for lists.
            - Answer in the user's language, briefly (a few sentences or a short list).
            - Do not give investment, tax or legal advice.
            - Earlier messages of the conversation may be included: use them to understand follow-up
              questions ("and in August?"), but get the numbers again from the tools.
            """;

    /** Answer given when the model calls a tool that does not exist (Spring AI throws). */
    private static final String UNKNOWN_TOOL_ANSWER =
            "I can't answer that yet. I can tell you your totals, spending by category, "
                    + "search transactions by merchant, your budget and your recurring charges.";

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final TransactionStatistics statistics;
    private final RecurringReport recurringReport;
    private final AccountService accountService;
    private final Clock clock;
    private final CopilotProperties properties;

    public CopilotService(ChatClient chatClient, TransactionStatistics statistics, RecurringReport recurringReport,
            AccountService accountService, Clock clock, CopilotProperties properties, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.statistics = statistics;
        this.recurringReport = recurringReport;
        this.accountService = accountService;
        this.clock = clock;
        this.properties = properties;
    }

    ChatResponseRecord chat(UUID userId, ChatRequestRecord request) {
        // 404 before spending tokens: inside a tool the error would only reach the model.
        accountService.requireOwned(userId, request.accountId());

        TokenUsageAdvisor usageAdvisor = new TokenUsageAdvisor();
        ChatResponse chatAnswer;
        try {
            chatAnswer = prompt(userId, request, usageAdvisor).call().chatResponse();
        } catch (RuntimeException e) {
            if (e instanceof ApiException) {
                throw e;  // ours (e.g. 404), already mapped to an HTTP response
            }
            if (e instanceof IllegalStateException && isUnknownTool(e)) {
                log.warn("Copilot model called an unknown tool: {}", e.getMessage());
                logUsage(userId, request, null, usageAdvisor.usage(), "unknown_tool");
                return new ChatResponseRecord(UNKNOWN_TOOL_ANSWER, properties.provider(), null, usageAdvisor.usage());
            }
            // Overloaded (503), over quota (429), timeout or network error: to the user they are all
            // "try again later". Providers wrap their errors differently, so the cause is only logged.
            log.warn("Copilot LLM call failed: {}", rootMessage(e), e);
            logUsage(userId, request, null, usageAdvisor.usage(), "failed");
            throw new ServiceUnavailableException("COPILOT_UNAVAILABLE",
                    "The copilot is busy or unavailable right now. Please try again in a moment.");
        }

        TokenUsage usage = usageAdvisor.usage();
        String model = chatAnswer == null ? null : chatAnswer.getMetadata().getModel();

        // Blocked by safety filters or cut by max-output-tokens: the provider may return no text.
        if (chatAnswer == null || chatAnswer.getResult() == null
                || chatAnswer.getResult().getOutput().getText() == null) {
            logUsage(userId, request, model, usage, "empty");
            throw new UpstreamUnavailableException("COPILOT_EMPTY_RESPONSE",
                    "The copilot returned no answer. Try again or rephrase the question.");
        }
        String answer = chatAnswer.getResult().getOutput().getText();
        logUsage(userId, request, model, usage, "ok");

        return new ChatResponseRecord(answer, properties.provider(), model, usage);
    }

    /**
     * Same answer as {@link #chat}, sent as it is written (Server-Sent Events, see
     * {@link ChatStreamEvents}). The account is checked before the stream starts, so a foreign
     * account is still a plain 404. Failures after that arrive as an {@code error} event, because
     * the HTTP status (200) is already sent.
     */
    Flux<ServerSentEvent<Object>> stream(UUID userId, ChatRequestRecord request) {
        accountService.requireOwned(userId, request.accountId());

        TokenUsageAdvisor usageAdvisor = new TokenUsageAdvisor();
        AtomicReference<String> model = new AtomicReference<>();
        AtomicBoolean answered = new AtomicBoolean();

        Flux<ServerSentEvent<Object>> deltas = Flux.defer(() -> prompt(userId, request, usageAdvisor).stream().chatResponse())
                .doOnNext(chunk -> {
                    if (chunk.getMetadata() != null && chunk.getMetadata().getModel() != null) {
                        model.set(chunk.getMetadata().getModel());
                    }
                })
                .map(CopilotService::textOf)
                .filter(text -> !text.isEmpty())
                .doOnNext(text -> answered.set(true))
                .map(ChatStreamEvents::delta);

        Flux<ServerSentEvent<Object>> end = Flux.defer(() -> {
            TokenUsage usage = usageAdvisor.usage();
            if (!answered.get()) {
                logUsage(userId, request, model.get(), usage, "empty");
                return Flux.just(ChatStreamEvents.error("COPILOT_EMPTY_RESPONSE",
                        "The copilot returned no answer. Try again or rephrase the question."));
            }
            logUsage(userId, request, model.get(), usage, "ok");
            return Flux.just(ChatStreamEvents.done(properties.provider(), model.get(), usage));
        });

        return deltas.concatWith(end).onErrorResume(e -> {
            TokenUsage usage = usageAdvisor.usage();
            if (e instanceof IllegalStateException ise && isUnknownTool(ise)) {
                log.warn("Copilot model called an unknown tool: {}", e.getMessage());
                logUsage(userId, request, model.get(), usage, "unknown_tool");
                return Flux.just(ChatStreamEvents.delta(UNKNOWN_TOOL_ANSWER),
                        ChatStreamEvents.done(properties.provider(), model.get(), usage));
            }
            log.warn("Copilot LLM stream failed: {}", rootMessage(e), e);
            logUsage(userId, request, model.get(), usage, "failed");
            return Flux.just(ChatStreamEvents.error("COPILOT_UNAVAILABLE",
                    "The copilot is busy or unavailable right now. Please try again in a moment."));
        });
    }

    /** The request to the model, shared by {@link #chat} and {@link #stream}. */
    private ChatClient.ChatClientRequestSpec prompt(UUID userId, ChatRequestRecord request,
                                                    TokenUsageAdvisor usageAdvisor) {
        CopilotTools tools = new CopilotTools(statistics, recurringReport, accountService, clock, userId,
                request.accountId());
        ChatClient.ChatClientRequestSpec prompt = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(LocalDate.now(clock), properties.amountFormat()))
                .user(request.message())
                .tools(tools)
                .advisors(usageAdvisor);
        if (request.conversationId() != null) {
            String conversation = conversationKey(userId, request);
            prompt = prompt.advisors(a -> a.advisors(memoryAdvisor).param(ChatMemory.CONVERSATION_ID, conversation));
        }
        return prompt;
    }

    /** Text of a streamed chunk; chunks with only metadata or a tool call have none. */
    private static String textOf(ChatResponse chunk) {
        if (chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return "";
        }
        String text = chunk.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    /**
     * One line per message, also when it fails: tokens are billed even then. Ids only, never the
     * message or the answer (they may contain financial data).
     */
    private void logUsage(UUID userId, ChatRequestRecord request, String model, TokenUsage usage, String outcome) {
        log.info("Copilot usage: outcome={} user={} account={} provider={} model={} calls={} input={} output={} total={}",
                outcome, userId, request.accountId(), properties.provider(), model, usage.llmCalls(),
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }

    /**
     * The user and the account are part of the key, so a conversation id sent by another user,
     * or reused with another account, never sees this history.
     */
    private static String conversationKey(UUID userId, ChatRequestRecord request) {
        return userId + ":" + request.accountId() + ":" + request.conversationId();
    }

    /** Spring AI has no dedicated exception for a hallucinated tool name. */
    private static boolean isUnknownTool(RuntimeException e) {
        return e.getMessage() != null && e.getMessage().startsWith("No ToolCallback found");
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
