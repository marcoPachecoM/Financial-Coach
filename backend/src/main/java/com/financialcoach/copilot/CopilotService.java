package com.financialcoach.copilot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import com.financialcoach.accounts.AccountService;
import com.financialcoach.shared.error.ApiException;
import com.financialcoach.shared.error.ServiceUnavailableException;
import com.financialcoach.shared.error.UpstreamUnavailableException;
import com.financialcoach.transactions.RecurringReport;
import com.financialcoach.transactions.TransactionStatistics;

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
        CopilotTools tools = new CopilotTools(statistics, recurringReport, accountService, clock, userId,
                request.accountId());

        ChatResponse chatAnswer;
        try {
            ChatClient.ChatClientRequestSpec prompt = chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(LocalDate.now(clock), properties.amountFormat()))
                    .user(request.message())
                    .tools(tools);
            if (request.conversationId() != null) {
                String conversation = conversationKey(userId, request);
                prompt = prompt.advisors(a -> a.advisors(memoryAdvisor).param(ChatMemory.CONVERSATION_ID, conversation));
            }
            chatAnswer = prompt.call().chatResponse();
        } catch (RuntimeException e) {
            if (e instanceof ApiException) {
                throw e;  // ours (e.g. 404), already mapped to an HTTP response
            }
            if (e instanceof IllegalStateException && isUnknownTool(e)) {
                log.warn("Copilot model called an unknown tool: {}", e.getMessage());
                return new ChatResponseRecord(UNKNOWN_TOOL_ANSWER, properties.provider(), null);
            }
            // Overloaded (503), over quota (429), timeout or network error: to the user they are all
            // "try again later". Providers wrap their errors differently, so the cause is only logged.
            log.warn("Copilot LLM call failed: {}", rootMessage(e), e);
            throw new ServiceUnavailableException("COPILOT_UNAVAILABLE",
                    "The copilot is busy or unavailable right now. Please try again in a moment.");
        }

        // Blocked by safety filters or cut by max-output-tokens: the provider may return no text.
        if (chatAnswer == null || chatAnswer.getResult() == null
                || chatAnswer.getResult().getOutput().getText() == null) {
            throw new UpstreamUnavailableException("COPILOT_EMPTY_RESPONSE",
                    "The copilot returned no answer. Try again or rephrase the question.");
        }
        String answer = chatAnswer.getResult().getOutput().getText();
        String model = chatAnswer.getMetadata().getModel();

        return new ChatResponseRecord(answer, properties.provider(), model);
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
