package com.financialcoach.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

class BoundedChatMemoryRepositoryTest {

    private final BoundedChatMemoryRepository repository = new BoundedChatMemoryRepository(2);

    @Test
    void forgetsTheLeastRecentlyUsedConversationWhenFull() {
        repository.saveAll("a", List.of(new UserMessage("first")));
        repository.saveAll("b", List.of(new UserMessage("second")));
        repository.findByConversationId("a"); // "a" is now more recent than "b"

        repository.saveAll("c", List.of(new UserMessage("third")));

        assertThat(repository.findConversationIds()).containsExactlyInAnyOrder("a", "c");
        assertThat(repository.findByConversationId("b")).isEmpty();
    }

    @Test
    void returnsACopySoCallersCannotChangeTheStoredHistory() {
        repository.saveAll("a", List.of(new UserMessage("hi")));

        assertThat(repository.findByConversationId("a")).hasSize(1);
        repository.deleteByConversationId("a");
        assertThat(repository.findByConversationId("a")).isEmpty();
    }
}
