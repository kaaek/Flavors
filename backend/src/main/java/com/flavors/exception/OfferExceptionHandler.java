package com.flavors.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JSON {@code ProblemDetail} answers for the follower offer (EEC-41). Its own class, not more methods in
 * GlobalExceptionHandler, so this branch doesn't collide with the other open branches that edit that file.
 */
@RestControllerAdvice
class OfferExceptionHandler {

    /** Same shape as "Invalid campaign": {@code errors} names each wrong field so the form can highlight it. */
    @ExceptionHandler(OfferValidationException.class)
    ResponseEntity<ProblemDetail> invalid(OfferValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid offer");
        problem.setProperty("errors", e.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** The page tells the brand to choose the creator on the campaign first, with a link to it. */
    @ExceptionHandler(OfferCreatorRequiredException.class)
    ResponseEntity<ProblemDetail> noCreator(OfferCreatorRequiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Creator required");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(OfferLockedException.class)
    ResponseEntity<ProblemDetail> locked(OfferLockedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Offer locked");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
