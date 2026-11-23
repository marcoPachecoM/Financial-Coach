package com.financialcoach.copilot;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Flux;

/**
 * Caps the calls to the model in one chat request. Create one per request.
 *
 * <p>Spring AI's tool loop has no limit: a model that keeps asking for tools (a bug, or text in
 * the data that steers it) would call the LLM again and again, and the rate limit only counts HTTP
 * requests. A normal question takes 2 or 3 calls.
 *
 * <p>Order -1: after the ToolCallingAdvisor, so it sees every round of the loop, and before the
 * TokenUsageAdvisor (0), so a call that is refused is not counted as spent.
 */
class LlmCallLimitAdvisor implements CallAdvisor, StreamAdvisor {

    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();

    LlmCallLimitAdvisor(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        checkNextCall();
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Flux.defer(() -> {
            checkNextCall();
            return chain.nextStream(request);
        });
    }

    private void checkNextCall() {
        if (calls.incrementAndGet() > maxCalls) {
            throw new LimitExceededException(maxCalls);
        }
    }

    /** True when {@code error}, or one of its causes, is this advisor refusing a call. */
    static boolean isLimitExceeded(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            if (e instanceof LimitExceededException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "LlmCallLimitAdvisor";
    }

    @Override
    public int getOrder() {
        return -1;
    }

    static final class LimitExceededException extends RuntimeException {

        LimitExceededException(int maxCalls) {
            super("The model needed more than " + maxCalls + " calls for one message");
        }
    }
}
