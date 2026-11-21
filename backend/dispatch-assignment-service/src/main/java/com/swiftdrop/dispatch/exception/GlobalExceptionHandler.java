package com.swiftdrop.dispatch.exception;

import com.swiftdrop.contracts.logging.LogSanitizer;
import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.security.CallerContextException;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.dispatch.service.CourierConflictRetry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** PostgreSQL SQLSTATE for unique_violation. */
    static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    static final String GENERIC_ERROR_MESSAGE = "An unexpected error occurred";

    /** Missing/invalid gateway identity headers -> 401; caller not allowed -> 403. */
    @ExceptionHandler(CallerContextException.class)
    public ResponseEntity<ApiErrorResponse> handleCallerContext(
            CallerContextException ex,
            HttpServletRequest request) {
        log.warn("Access denied on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        int status = ex.statusCode();
        String errorName = status == 403 ? "Forbidden" : "Unauthorized";
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status, errorName, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        boolean courierConflict = CourierConflictRetry.isCourierConflict(ex);
        log.warn("Concurrent {} update rejected for {}",
                courierConflict ? "courier shift" : "dispatch assignment", LogSanitizer.clean(request.getRequestURI()));
        String message = courierConflict
                ? "The courier's record was changed by another operation, such as a status update or "
                        + "another assignment. Refresh and retry."
                : "The dispatch record was changed by another operation. Refresh and retry.";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Conflict", message, request.getRequestURI()));
    }

    @ExceptionHandler(ShiftEndNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleShiftEndNotAllowed(
            ShiftEndNotAllowedException ex,
            HttpServletRequest request) {
        log.warn("Shift end rejected on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Shift Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Only a unique-key violation is a genuine conflict with an existing record
     * (for example two first-time shift starts for the same courier). Any other
     * integrity violation is a server-side defect, so it gets the generic 500
     * instead of a misleading 409. Database details are logged, never returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        if (!UNIQUE_VIOLATION_SQL_STATE.equals(sqlState(ex))) {
            return handleGeneric(ex, request);
        }
        log.warn("Unique constraint violation on {}", LogSanitizer.clean(request.getRequestURI()), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Data Conflict",
                        "The request conflicts with an existing record. Refresh and retry.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(AssignmentConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleAssignmentConflict(
            AssignmentConflictException ex,
            HttpServletRequest request) {
        log.warn("Assignment conflict on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Assignment Conflict", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ReassignmentNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleReassignmentNotAllowed(
            ReassignmentNotAllowedException ex,
            HttpServletRequest request) {
        log.warn("Reassignment rejected on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Reassignment Not Allowed", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AssignmentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AssignmentNotFoundException ex, HttpServletRequest request) {
        log.warn("Assignment not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CourierProfileException.class)
    public ResponseEntity<ApiErrorResponse> handleCourierProfile(CourierProfileException ex, HttpServletRequest request) {
        log.warn("Courier profile request rejected on {}: {}", LogSanitizer.clean(request.getRequestURI()),
                LogSanitizer.clean(ex.getMessage()));
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getStatus().value(), ex.getStatus().getReasonPhrase(), ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(CourierNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleCourierNotAvailable(CourierNotAvailableException ex, HttpServletRequest request) {
        log.warn("Courier not available: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Courier Not Available", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidAssignmentTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAssignmentTransition(
            InvalidAssignmentTransitionException ex,
            HttpServletRequest request) {
        log.warn("Invalid assignment transition on {}", LogSanitizer.clean(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        409,
                        "Invalid Assignment Transition",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation error on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(errors));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Validation Failed", errors, request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Exception messages can contain SQL, constraint names, driver details or row
     * data such as courier phone numbers, so the full exception is logged and the
     * client receives only a generic message. A UUID correlation ID is echoed so
     * the response can be matched to the server log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            return frameworkClientError(framework, request.getRequestURI());
        }
        String correlationId = correlationId(request);
        log.error("Internal error on {} (correlationId={})", LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(correlationId), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(500, "Internal Server Error", GENERIC_ERROR_MESSAGE,
                        request.getRequestURI(), correlationId));
    }

    /**
     * Spring MVC's own client errors (unknown path 404, wrong method 405, missing parameter 400,
     * unsupported media type 415, ...) implement {@link ErrorResponse} and already carry the right
     * status. Return it instead of letting the catch-all turn them into a 500.
     */
    private static ResponseEntity<ApiErrorResponse> frameworkClientError(ErrorResponse error, String path) {
        HttpStatusCode status = error.getStatusCode();
        HttpStatus known = HttpStatus.resolve(status.value());
        String reason = known != null ? known.getReasonPhrase() : "Client Error";
        return ResponseEntity.status(status).headers(error.getHeaders())
                .body(ApiErrorResponse.of(status.value(), reason, reason, path));
    }

    private static String sqlState(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

    /** Only a well-formed UUID is echoed; arbitrary client header text is not reflected. */
    private static String correlationId(HttpServletRequest request) {
        String header = request.getHeader(JwtHeaderConstants.X_CORRELATION_ID);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim()).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
