package kg.tunduk.cvscan.screening.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponse;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(final MethodArgumentNotValidException ex, final HttpServletRequest request) {
        final List<ErrorResponseDetailsInner> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponseDetailsInner()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .pointer(FieldErrorToPointerMapper.toPointer(fe.getField())))
                .toList();
        return badRequest(request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(final ConstraintViolationException ex, final HttpServletRequest request) {
        final List<ErrorResponseDetailsInner> details = ex.getConstraintViolations().stream()
                .map(v -> {
                    final String field = v.getPropertyPath().toString();
                    return new ErrorResponseDetailsInner()
                            .field(field)
                            .message(v.getMessage())
                            .pointer(FieldErrorToPointerMapper.toPointer(field));
                })
                .toList();
        return badRequest(request, details);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(final BadRequestException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorResponse(400, "VALIDATION_ERROR", ex.getMessage(), request));
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleRequestValidation(final RequestValidationException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorResponse(400, "VALIDATION_ERROR", ex.getMessage(), request).details(ex.getDetails()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(final NotFoundException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                errorResponse(404, "RESOURCE_NOT_FOUND", ex.getMessage(), request));
    }

    @ExceptionHandler(DuplicateRuleSetException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRuleSet(final DuplicateRuleSetException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                errorResponse(409, "DUPLICATE_RULE_SET", ex.getMessage(), request));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(final VersionConflictException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                errorResponse(409, "VERSION_CONFLICT", ex.getMessage(), request));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(final ObjectOptimisticLockingFailureException ex, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                errorResponse(409, "VERSION_CONFLICT", "Конкурентное изменение решения, повторите запрос", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(final Exception ex, final HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                errorResponse(500, "INTERNAL_ERROR", "Внутренняя ошибка сервиса", request));
    }

    private ResponseEntity<ErrorResponse> badRequest(final HttpServletRequest request, final List<ErrorResponseDetailsInner> details) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorResponse(400, "VALIDATION_ERROR", "Ошибка валидации входных данных", request).details(details));
    }

    private ErrorResponse errorResponse(final int status, final String error, final String message, final HttpServletRequest request) {
        return new ErrorResponse(status, error, message, OffsetDateTime.now(ZoneOffset.UTC), request.getRequestURI());
    }
}
