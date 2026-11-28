package com.flavors.platform.web;

import com.flavors.platform.idempotency.IdempotencyConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The ProblemDetail error base every service gets (same shapes as the monolith's
 * {@code GlobalExceptionHandler}). A service adds its own {@code @RestControllerAdvice} for domain
 * exceptions, or throws {@link ProblemException} for the simple cases.
 */
@RestControllerAdvice
public class PlatformExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Correct the highlighted fields and try again.");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ProblemException.class)
    ProblemDetail problem(ProblemException exception) {
        return ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
    }
}
