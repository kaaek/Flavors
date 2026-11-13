package com.flavors.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns domain exceptions into JSON {@code ProblemDetail} responses, like the 401 and 403 ones.
 * Other stories add their own {@code @ExceptionHandler} methods here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(DuplicateEmailException.class)
    ProblemDetail duplicateEmail(DuplicateEmailException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(SuspendedAccountException.class)
    ProblemDetail suspended(SuspendedAccountException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    ProblemDetail invalidResetToken(InvalidPasswordResetTokenException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail validationFailed(ValidationFailedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("errors", Map.of(exception.getField(), exception.getMessage()));
        return problem;
    }

    @ExceptionHandler(CreatorNotFoundException.class)
    ProblemDetail creatorNotFound(CreatorNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ReviewNotAllowedException.class)
    ProblemDetail reviewNotAllowed(ReviewNotAllowedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(SavedCreatorNotFoundException.class)
    ProblemDetail savedCreatorNotFound(SavedCreatorNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CreatorNotApprovedException.class)
    ProblemDetail creatorNotApproved(CreatorNotApprovedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /** The campaign form has wrong or missing fields: {@code errors} names each one so the form can highlight it. */
    @ExceptionHandler(CampaignValidationException.class)
    ResponseEntity<ProblemDetail> campaignInvalid(CampaignValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid campaign");
        problem.setProperty("errors", e.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(NoBranchException.class)
    ResponseEntity<ProblemDetail> noBranch(NoBranchException e) {
        return problem(HttpStatus.CONFLICT, "No branch", e.getMessage());
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    ResponseEntity<ProblemDetail> campaignNotFound(CampaignNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Campaign not found", e.getMessage());
    }

    @ExceptionHandler(CampaignNotEditableException.class)
    ResponseEntity<ProblemDetail> campaignNotEditable(CampaignNotEditableException e) {
        return problem(HttpStatus.CONFLICT, "Campaign already sent", e.getMessage());
    }

    @ExceptionHandler(InvalidPortfolioException.class)
    ResponseEntity<ProblemDetail> invalidPortfolio(InvalidPortfolioException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid portfolio", e.getMessage());
    }

    @ExceptionHandler(PortfolioNotAvailableException.class)
    ResponseEntity<ProblemDetail> portfolioNotAvailable(PortfolioNotAvailableException e) {
        return problem(HttpStatus.CONFLICT, "Portfolio not available", e.getMessage());
    }

    @ExceptionHandler(AlreadyFeaturedException.class)
    ResponseEntity<ProblemDetail> alreadyFeatured(AlreadyFeaturedException e) {
        return problem(HttpStatus.CONFLICT, "Already featured", e.getMessage());
    }

    @ExceptionHandler(FeatureNotFoundException.class)
    ResponseEntity<ProblemDetail> featureNotFound(FeatureNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not featured", e.getMessage());
    }

    /**
     * A database constraint refused the change, for example two requests inserting the same row at the
     * same moment. The database message is not sent, because it names tables and columns.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> dataIntegrityViolation(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Someone changed this at the same time. Please try again.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
