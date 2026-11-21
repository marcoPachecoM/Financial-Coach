package com.financialcoach.shared.web;

import com.financialcoach.shared.error.ApiFieldError;
import com.financialcoach.shared.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * The contract rejects unknown query parameters, like unknown body fields. Spring ignores them
 * by default, so endpoints with query parameters call {@link #requireOnly}.
 */
public final class QueryParameters {

    private QueryParameters() {
    }

    public static void requireOnly(HttpServletRequest request, Set<String> allowed) {
        List<ApiFieldError> unknown = request.getParameterMap().keySet().stream()
                .filter(name -> !allowed.contains(name))
                .sorted()
                .map(name -> ApiFieldError.of(name, "Unknown query parameter."))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException(unknown);
        }
    }
}
