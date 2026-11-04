package com.flavors.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JSON {@code ProblemDetail} answers for the social-account endpoints (EEC-33). A separate class from
 * GlobalExceptionHandler so this story's branch doesn't collide with the other open branches that edit it.
 */
@RestControllerAdvice
class SocialAccountExceptionHandler {

    @ExceptionHandler(SocialConnectionUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(SocialConnectionUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Instagram not set up", e.getMessage());
    }

    @ExceptionHandler(CreatorProfileMissingException.class)
    ResponseEntity<ProblemDetail> profileMissing(CreatorProfileMissingException e) {
        return problem(HttpStatus.CONFLICT, "Creator profile missing", e.getMessage());
    }

    @ExceptionHandler(SocialAccountTakenException.class)
    ResponseEntity<ProblemDetail> taken(SocialAccountTakenException e) {
        return problem(HttpStatus.CONFLICT, "Account already connected", e.getMessage());
    }

    @ExceptionHandler(SocialAccountNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(SocialAccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Social account not found", e.getMessage());
    }

    /** Instagram's own message is not passed on: it may name internal details. */
    @ExceptionHandler(SocialPlatformException.class)
    ResponseEntity<ProblemDetail> platform(SocialPlatformException e) {
        return problem(HttpStatus.BAD_GATEWAY, "Instagram error", "Instagram didn't answer correctly. Please try again.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
