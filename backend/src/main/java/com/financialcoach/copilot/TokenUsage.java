package com.financialcoach.copilot;

/**
 * Tokens spent answering one chat message, summed over every LLM call it needed (a question that
 * uses tools makes several: question → tool call → answer).
 *
 * @param inputTokens  tokens sent to the model (prompt, history, tool results)
 * @param outputTokens tokens the model wrote
 * @param totalTokens  as the provider bills them; may exceed input + output (e.g. Gemini's thinking tokens)
 * @param llmCalls     calls made to the model
 */
public record TokenUsage(int inputTokens, int outputTokens, int totalTokens, int llmCalls) {
}
