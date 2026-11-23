package com.financialcoach.copilot;

/** {@code usage}: tokens spent on this message (also logged), e.g. to watch the LLM cost. */
public record ChatResponseRecord(String response, String provider, String model, TokenUsage usage) {}
