package com.financialcoach.copilot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.financialcoach.transactions.TransactionStatistics;

@Service 
public class CopilotService {
    private final ChatClient chatClient;
    private final TransactionStatistics statistics;
    private final Clock clock;

    public CopilotService(ChatClient chatClient, TransactionStatistics statistics, Clock clock) {
        this.chatClient = chatClient;
        this.statistics = statistics;
        this.clock = clock;
    }

    ChatResponseRecord chat(UUID userId, ChatRequestRecord request) {
        CopilotTools tools = new CopilotTools(statistics, userId, request.accountId());

        String answer = chatClient.prompt()
                .system("""
                        You are a personal finance coach. Today is %s.
                        Answer only with data from the tools; never invent amounts.
                        Answer in the user's language, briefly.
                        """.formatted(LocalDate.now(clock)))
                .user(request.message())
                .tools(tools)
                .call()
                .content();

        return new ChatResponseRecord(answer, "google-genai", "gemini-3.8-flash");
    }
}

