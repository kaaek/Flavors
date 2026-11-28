package com.flavors.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The brand-related handlers of the monolith's {@code GlobalExceptionHandler}, unchanged. Validation errors
 * (400) come from the platform's {@code PlatformExceptionHandler}.
 */
@RestControllerAdvice
public class BrandExceptionHandler {

    @ExceptionHandler(SavedCreatorNotFoundException.class)
    ProblemDetail savedCreatorNotFound(SavedCreatorNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CreatorNotApprovedException.class)
    ProblemDetail creatorNotApproved(CreatorNotApprovedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * A database constraint refused the change, for example two requests inserting the same row at the
     * same moment. The database message is not sent, because it names tables and columns.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> dataIntegrityViolation(DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Someone changed this at the same time. Please try again.");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
