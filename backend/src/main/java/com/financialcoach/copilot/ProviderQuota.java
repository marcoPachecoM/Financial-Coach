package com.financialcoach.copilot;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes "quota exceeded" (HTTP 429) among the causes of an LLM error, and when the provider
 * says to retry. Providers wrap their errors differently, so it reads the messages instead of
 * provider-specific classes; this keeps CopilotService free of Gemini types.
 *
 * <p>Gemini's message, for example: {@code 429 . You exceeded your current quota ... Please retry
 * in 6h50m44.913668754s.} with {@code "retryDelay": "24644s"} in the details.
 *
 * @param retryIn when to try again, or null when the provider did not say
 */
record ProviderQuota(Duration retryIn) {

    private static final Pattern QUOTA = Pattern.compile(
            "\\b429\\b|RESOURCE_EXHAUSTED|exceeded your current quota|rate limit", Pattern.CASE_INSENSITIVE);
    /** "retry in 6h50m44.9s", "retry in 41.2s", "retry in 2m". */
    private static final Pattern RETRY_IN = Pattern.compile(
            "retry in ((?:\\d+(?:\\.\\d+)?[hms])+)", Pattern.CASE_INSENSITIVE);
    /** "retryDelay": "24644s" */
    private static final Pattern RETRY_DELAY = Pattern.compile("retryDelay\"?\\s*:\\s*\"(\\d+)(?:\\.\\d+)?s\"");
    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)([hms])");

    /** Present when the error, or one of its causes, is a quota error. */
    static Optional<ProviderQuota> of(Throwable error) {
        boolean quota = false;
        Duration retryIn = null;
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            String message = e.getMessage();
            if (message == null) {
                continue;
            }
            quota |= QUOTA.matcher(message).find();
            if (retryIn == null) {
                retryIn = retryInOf(message);
            }
        }
        return quota ? Optional.of(new ProviderQuota(retryIn)) : Optional.empty();
    }

    /** What the user reads: when to try again, rounded up to a whole unit. */
    String detail() {
        return retryIn == null
                ? "The AI model's usage quota is used up. Try again later."
                : "The AI model's usage quota is used up. Try again in %s.".formatted(human(retryIn));
    }

    private static Duration retryInOf(String message) {
        Matcher in = RETRY_IN.matcher(message);
        if (in.find()) {
            Duration total = Duration.ZERO;
            Matcher part = PART.matcher(in.group(1));
            while (part.find()) {
                long seconds = (long) Math.ceil(Double.parseDouble(part.group(1)) * switch (part.group(2).toLowerCase()) {
                    case "h" -> 3600;
                    case "m" -> 60;
                    default -> 1;
                });
                total = total.plusSeconds(seconds);
            }
            return total;
        }
        Matcher delay = RETRY_DELAY.matcher(message);
        return delay.find() ? Duration.ofSeconds(Long.parseLong(delay.group(1))) : null;
    }

    /** 24644 s → "6 h 51 min"; 41 s → "41 seconds"; 90 s → "2 min". */
    static String human(Duration d) {
        long seconds = Math.max(1, d.toSeconds());
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }
}
