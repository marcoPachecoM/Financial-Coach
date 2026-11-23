package com.financialcoach.copilot;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;

import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;

/**
 * Adds up the tokens of every call to the model in one chat request. Create one per request.
 *
 * <p>Its order (0) places it after the ToolCallingAdvisor and before the model, so it runs once per
 * round of the tool loop. The usage of the final response alone would only cover the last call.
 *
 * <p>When streaming, every chunk of a call repeats the usage so far, so only the last one with
 * tokens is added (adding every chunk would count the same tokens many times). A call cancelled
 * half-way (the client left) adds what its chunks reported until then.
 */
class TokenUsageAdvisor implements CallAdvisor, StreamAdvisor {

    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private int calls;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        addCall(usageOf(response));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        AtomicReference<Usage> last = new AtomicReference<>();
        return chain.nextStream(request)
                .doOnNext(response -> {
                    Usage usage = usageOf(response);
                    if (usage != null && (orZero(usage.getPromptTokens()) > 0 || orZero(usage.getCompletionTokens()) > 0)) {
                        last.set(usage);
                    }
                })
                .doOnComplete(() -> addCall(last.get()))
                // Cut short by the client: the tokens of this call up to now are billed too.
                .doOnCancel(() -> addCall(last.get()));
    }

    /** What was spent so far; also valid after a failure in a later call. */
    synchronized TokenUsage usage() {
        return new TokenUsage(inputTokens, outputTokens, totalTokens, calls);
    }

    private static Usage usageOf(ChatClientResponse response) {
        return response.chatResponse() == null ? null : response.chatResponse().getMetadata().getUsage();
    }

    /** Streams complete on Reactor threads, hence synchronized. */
    private synchronized void addCall(Usage usage) {
        calls++;
        if (usage == null) {
            return;
        }
        int input = orZero(usage.getPromptTokens());
        int output = orZero(usage.getCompletionTokens());
        inputTokens += input;
        outputTokens += output;
        totalTokens += usage.getTotalTokens() == null ? input + output : usage.getTotalTokens();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    @Override
    public String getName() {
        return "TokenUsageAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
