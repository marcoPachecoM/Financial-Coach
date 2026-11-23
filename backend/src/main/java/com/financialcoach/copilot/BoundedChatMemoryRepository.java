package com.financialcoach.copilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

/**
 * In-memory chat history that keeps at most {@code maxConversations} conversations, dropping the
 * least recently used one first. Spring AI's InMemoryChatMemoryRepository never forgets a
 * conversation, so a long-running server would grow without limit (the frontend starts a new
 * conversation on every account change or "new chat").
 *
 * <p>History is lost on restart, which is fine for a chat: the frontend shows a fresh one.
 */
class BoundedChatMemoryRepository implements ChatMemoryRepository {

    private final Map<String, List<Message>> conversations;

    BoundedChatMemoryRepository(int maxConversations) {
        // accessOrder = true: get() moves a conversation to the end, so the eldest is the least used.
        this.conversations = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                return size() > maxConversations;
            }
        };
    }

    @Override
    public synchronized List<String> findConversationIds() {
        return List.copyOf(conversations.keySet());
    }

    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        List<Message> messages = conversations.get(conversationId);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        conversations.put(conversationId, new ArrayList<>(messages));
    }

    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        conversations.remove(conversationId);
    }
}
