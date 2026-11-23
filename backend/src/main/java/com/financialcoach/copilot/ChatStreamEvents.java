package com.financialcoach.copilot;

import org.springframework.http.codec.ServerSentEvent;

/**
 * Server-Sent Events of POST /copilot/chat/stream, in this order:
 * <pre>
 * event: delta   data: {"text":"En septiembre "}        (many: append them)
 * event: delta   data: {"text":"gastaste $251.96..."}
 * event: done    data: {"provider":"google-genai","model":"...","usage":{...}}   (last, on success)
 * event: error   data: {"code":"COPILOT_UNAVAILABLE","detail":"..."}            (last, on failure)
 * </pre>
 * Error codes: COPILOT_UNAVAILABLE (busy, timeout), COPILOT_QUOTA_EXCEEDED (the provider's quota is
 * used up; the detail says when to retry) and COPILOT_EMPTY_RESPONSE.
 * <pre>
 * </pre>
 * An error can follow some deltas: the answer stopped half-way.
 */
final class ChatStreamEvents {

    static final String DELTA = "delta";
    static final String DONE = "done";
    static final String ERROR = "error";

    private ChatStreamEvents() {
    }

    public record Delta(String text) {
    }

    public record Done(String provider, String model, TokenUsage usage) {
    }

    public record Error(String code, String detail) {
    }

    static ServerSentEvent<Object> delta(String text) {
        return ServerSentEvent.<Object>builder(new Delta(text)).event(DELTA).build();
    }

    static ServerSentEvent<Object> done(String provider, String model, TokenUsage usage) {
        return ServerSentEvent.<Object>builder(new Done(provider, model, usage)).event(DONE).build();
    }

    static ServerSentEvent<Object> error(String code, String detail) {
        return ServerSentEvent.<Object>builder(new Error(code, detail)).event(ERROR).build();
    }
}
