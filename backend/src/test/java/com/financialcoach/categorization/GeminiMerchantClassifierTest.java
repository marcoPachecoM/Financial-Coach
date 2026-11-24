package com.financialcoach.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

/** The LLM is a stub: what matters is batching, parsing and never failing. */
class GeminiMerchantClassifierTest {

    private final List<String> userMessages = new ArrayList<>();

    @Test
    void parsesTheAnswerAndKeepsOnlyMerchantsSentWithValidLabels() {
        GeminiMerchantClassifier classifier = classifier(2, 3, prompt -> """
                Sure! ```json
                {"oxxo centro": "Snacks", "netflix com": "Services", "invented": "Food", "uber": "Rockets", "x": 3}
                ```""");

        Map<String, CategoryGroup> result = classifier.classify(List.of("oxxo centro", "netflix com", "uber"));

        assertThat(result).containsOnly(
                Map.entry("oxxo centro", CategoryGroup.SNACKS), Map.entry("netflix com", CategoryGroup.SERVICES));
    }

    @Test
    void sendsManyMerchantsPerCallUpToTheCallLimit() {
        List<String> merchants = IntStream.rangeClosed(1, 7).mapToObj(i -> "shop " + (char) ('a' + i)).toList();
        GeminiMerchantClassifier classifier = classifier(3, 2, prompt -> "{}");

        classifier.classify(merchants);

        // 7 merchants, 3 per call, at most 2 calls: the 7th is not sent.
        assertThat(userMessages).hasSize(2);
        assertThat(userMessages.get(0).lines()).containsExactly("shop b", "shop c", "shop d");
        assertThat(userMessages.get(1).lines()).containsExactly("shop e", "shop f", "shop g");
    }

    @Test
    void neverFails() {
        assertThat(classifier(10, 1, prompt -> "not json at all").classify(List.of("oxxo"))).isEmpty();
        assertThat(classifier(10, 1, prompt -> {
            throw new IllegalStateException("quota exceeded");
        }).classify(List.of("oxxo"))).isEmpty();
    }

    @Test
    void parseIgnoresAnythingThatIsNotAnObject() {
        GeminiMerchantClassifier classifier = classifier(10, 1, prompt -> "{}");

        assertThat(classifier.parse(null, Set.of("a"))).isEmpty();
        assertThat(classifier.parse("[\"Food\"]", Set.of("a"))).isEmpty();
        assertThat(classifier.parse("{\"a\": \"Food\"", Set.of("a"))).isEmpty();
    }

    private GeminiMerchantClassifier classifier(int batchSize, int maxCalls, Function<Prompt, String> answer) {
        ChatModel model = prompt -> {
            userMessages.add(prompt.getUserMessage().getText());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer.apply(prompt)))));
        };
        return new GeminiMerchantClassifier(ChatClient.builder(model).build(), JsonMapper.builder().build(),
                new CategorizationProperties(true, batchSize, maxCalls));
    }
}
