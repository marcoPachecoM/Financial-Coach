package com.financialcoach.copilot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import com.financialcoach.shared.error.UpstreamUnavailableException;
import com.financialcoach.transactions.TransactionStatistics;

@Service
public class CopilotService {
    private final ChatClient chatClient;
    private final TransactionStatistics statistics;
    private final Clock clock;
    private final CopilotProperties properties;

    public CopilotService(ChatClient chatClient, TransactionStatistics statistics, Clock clock,
            CopilotProperties properties) {
        this.chatClient = chatClient;
        this.statistics = statistics;
        this.clock = clock;
        this.properties = properties;
    }

    ChatResponseRecord chat(UUID userId, ChatRequestRecord request) {
        CopilotTools tools = new CopilotTools(statistics, userId, request.accountId());

        ChatResponse chatAnswer = chatClient.prompt()
                .system("""
                        You are a personal finance coach. Today is %s.
                        Answer only with data from the tools; never invent amounts.
                        Answer in the user's language, briefly.
                        """.formatted(LocalDate.now(clock)))
                .user(request.message())
                .tools(tools)
                .call()
                .chatResponse();

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
