package com.financialcoach.analytics;

import java.util.Map;

/**
 * {@code Alert} schema of openapi.yaml.
 *
 * <p>{@code id} is stable (the same situation always has the same id), so clients can remember
 * which alerts the user dismissed. {@code data} holds structured values (money as strings, dates
 * as ISO strings) and the client writes the text. {@code action} is null when there is none.
 */
record AlertResponse(String id, AlertType type, AlertSeverity severity, Map<String, Object> data,
                     AlertAction action) {
}
