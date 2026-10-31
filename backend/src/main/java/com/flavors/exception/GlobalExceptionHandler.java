package com.flavors.exception;
 
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
 
/**
 * Turns domain exceptions into JSON {@code ProblemDetail} responses, like the 401 and 403 ones.
 * Other stories add their own {@code @ExceptionHandler} methods here.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
 
    @ExceptionHandler(InvalidPortfolioException.class)
    ResponseEntity<ProblemDetail> invalidPortfolio(InvalidPortfolioException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid portfolio", e.getMessage());
    }
 
    @ExceptionHandler(PortfolioNotAvailableException.class)
    ResponseEntity<ProblemDetail> portfolioNotAvailable(PortfolioNotAvailableException e) {
        return problem(HttpStatus.CONFLICT, "Portfolio not available", e.getMessage());
    }
 
    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}