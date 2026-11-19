package com.saferoute.ai.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.saferoute.ai.dto.ApiErrorResponse;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.exception.FieldValidationException;
import com.saferoute.ai.exception.GoneException;
import com.saferoute.ai.exception.LocationNotFoundException;
import com.saferoute.ai.exception.NotFoundException;
import com.saferoute.ai.exception.TooManyRequestsException;
import com.saferoute.ai.exception.UnauthorizedException;

/**
 * Turns exceptions into friendly JSON errors. Internal details are logged, never sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of("Please check the highlighted fields.", fieldErrors));
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleFieldValidation(FieldValidationException ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of(ex.getMessage(), Map.of(ex.getField(), ex.getMessage())));
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of("Invalid value for '" + ex.getName() + "'."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of("The request body is missing or is not valid JSON."));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ApiErrorResponse.of(ex.getMessage()));
    }

    /** Missing static files (e.g. /favicon.ico) are a 404, not a server error. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ApiErrorResponse.of("Not found."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorResponse.of("This action is not supported on this address."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooMany(TooManyRequestsException ex) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ApiErrorResponse> handleGone(GoneException ex) {
        return respond(HttpStatus.GONE, ApiErrorResponse.of(ex.getMessage()));
    }

    /** A client (e.g. a tracking page) went away mid-stream: nothing to send back. */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleClientGone() {
        // ignore
    }

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleLocationNotFound(LocationNotFoundException ex) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalService(ExternalServiceException ex) {
        log.warn("External service failure: {}", ex.getMessage(), ex.getCause());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected server error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorResponse.of("Something went wrong on the server. Please try again."));
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, ApiErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
