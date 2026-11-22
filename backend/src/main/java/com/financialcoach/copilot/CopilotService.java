package com.financialcoach.copilot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import com.financialcoach.accounts.AccountService;
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
            """;

    /** Answer given when the model calls a tool that does not exist (Spring AI throws). */
    private static final String UNKNOWN_TOOL_ANSWER =
            "I can't answer that yet. I can tell you your totals, spending by category, "
                    + "search transactions by merchant, your budget and your recurring charges.";

    private final ChatClient chatClient;
    private final TransactionStatistics statistics;
    private final RecurringReport recurringReport;
    private final AccountService accountService;
    private final Clock clock;
    private final CopilotProperties properties;

    public CopilotService(ChatClient chatClient, TransactionStatistics statistics, RecurringReport recurringReport,
            AccountService accountService, Clock clock, CopilotProperties properties) {
        this.chatClient = chatClient;
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
            chatAnswer = chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(LocalDate.now(clock), properties.amountFormat()))
                    .user(request.message())
                    .tools(tools)
                    .call()
                    .chatResponse();
        } catch (IllegalStateException e) {
            // Spring AI has no dedicated exception for a hallucinated tool name.
            if (e.getMessage() != null && e.getMessage().startsWith("No ToolCallback found")) {
                log.warn("Copilot model called an unknown tool: {}", e.getMessage());
                return new ChatResponseRecord(UNKNOWN_TOOL_ANSWER, properties.provider(), null);
            }
            throw e;
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
}
