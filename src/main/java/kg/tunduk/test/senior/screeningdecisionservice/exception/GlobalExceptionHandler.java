package kg.tunduk.test.senior.screeningdecisionservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.ErrorDetail;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetail(fe.getField(), fe.getDefaultMessage(), FieldErrorToPointerMapper.toPointer(fe.getField())))
                .toList();
        return badRequest(request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> {
                    String field = v.getPropertyPath().toString();
                    return new ErrorDetail(field, v.getMessage(), FieldErrorToPointerMapper.toPointer(field));
                })
                .toList();
        return badRequest(request, details);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(400, "VALIDATION_ERROR", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleRequestValidation(RequestValidationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(400, "VALIDATION_ERROR", ex.getMessage(), request.getRequestURI(), ex.getDetails()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(404, "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateRuleSetException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRuleSet(DuplicateRuleSetException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, "DUPLICATE_RULE_SET", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, "VERSION_CONFLICT", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, "VERSION_CONFLICT", "Конкурентное изменение решения, повторите запрос", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(500, "INTERNAL_ERROR", "Внутренняя ошибка сервиса", request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> badRequest(HttpServletRequest request, List<ErrorDetail> details) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of(400, "VALIDATION_ERROR", "Ошибка валидации входных данных", request.getRequestURI(), details));
    }
}
