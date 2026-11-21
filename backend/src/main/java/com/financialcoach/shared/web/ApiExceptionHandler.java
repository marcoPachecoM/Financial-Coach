package com.financialcoach.shared.web;

import com.financialcoach.shared.error.ApiException;
import com.financialcoach.shared.error.ApiFieldError;
import com.financialcoach.shared.error.ValidationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns every error raised while handling a request into RFC 9457 Problem Details with the
 * shape of {@code ProblemDetail} in docs/openapi.yaml: {@code code} always, {@code errors} for
 * validation problems. Security errors (401/403) are written by SecurityConfig instead,
 * because they happen before the request reaches a controller.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    /** Errors thrown on purpose by services (not found, conflict, business validation). */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setProperty("code", ex.code());
        if (!ex.errors().isEmpty()) {
            problem.setProperty("errors", ex.errors());
        }
        HttpHeaders headers = new HttpHeaders();
        if (ex.status() == HttpStatus.UNAUTHORIZED) {
            // A 401 must say how to authenticate (RFC 9110).
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return handleExceptionInternal(ex, problem, headers, ex.status(), request);
    }

    /** Anything unexpected: log it, never leak details to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Bean Validation failures on a {@code @Valid @RequestBody}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ApiFieldError.of(toSnakeCase(error.getField()), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
                .toList();
        return validationProblem(ex, errors, headers, request);
    }

    /** Constraint annotations on {@code @RequestParam} / {@code @PathVariable} (e.g. size > 100). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                           HttpHeaders headers,
                                                                           HttpStatusCode status,
                                                                           WebRequest request) {
        List<ApiFieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> ApiFieldError.of(parameterName(result.getMethodParameter()),
                                error.getDefaultMessage())))
                .sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
                .toList();
        return validationProblem(ex, errors, headers, request);
    }

    /** A required query parameter is missing. */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        return validationProblem(ex, List.of(ApiFieldError.of(ex.getParameterName(), "is required")),
                headers, request);
    }

    /** A required multipart part is missing (e.g. the CSV upload without {@code file}). */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
                                                                     HttpHeaders headers,
                                                                     HttpStatusCode status,
                                                                     WebRequest request) {
        return validationProblem(ex, List.of(ApiFieldError.of(ex.getRequestPartName(), "is required")),
                headers, request);
    }

    /** A query or path parameter cannot be converted (invalid UUID, date or number). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex.getPropertyName() == null ? "parameter" : toSnakeCase(ex.getPropertyName());
        return validationProblem(ex, List.of(ApiFieldError.of(name, "Invalid value.")), headers, request);
    }

    /** Malformed JSON, unknown fields or values of the wrong type. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        JacksonException jsonError = findCause(ex, JacksonException.class);
        if (jsonError instanceof UnrecognizedPropertyException) {
            return validationProblem(ex, List.of(ApiFieldError.of(lastPropertyName(jsonError), "Unknown field.")),
                    headers, request);
        }
        if (jsonError != null && lastPropertyName(jsonError) != null) {
            return validationProblem(ex, List.of(ApiFieldError.of(lastPropertyName(jsonError), "Invalid value.")),
                    headers, request);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request body.");
        problem.setProperty("code", "VALIDATION_ERROR");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Every response body passes through here, including the ones Spring builds for its own
     * exceptions (405, 415, type mismatches...). Adds the {@code code} and {@code instance}
     * members when they are missing.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            // Spring's own exceptions arrive without a body; build it here so code/instance are added.
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", defaultCode(statusCode));
            }
            if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
                problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationProblem(Exception ex, List<ApiFieldError> errors,
                                                     HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ValidationException.describe(errors.size()));
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static String defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            default -> status.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "HTTP_" + status.value();
        };
    }

    /** The name the client used: the {@code @RequestParam} name if set, else the Java name in snake_case. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        String javaName = parameter.getParameterName();
        return javaName == null ? "parameter" : toSnakeCase(javaName);
    }

    /** Java property names are camelCase; the API exposes snake_case. */
    private static String toSnakeCase(String field) {
        return CAMEL_CASE_BOUNDARY.matcher(field).replaceAll("$1_$2").toLowerCase();
    }

    private static String lastPropertyName(JacksonException error) {
        List<JacksonException.Reference> path = error.getPath();
        return path.isEmpty() ? null : path.getLast().getPropertyName();
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }
}
