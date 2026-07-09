package com.example.adplatform.common.exception;

import com.example.adplatform.common.response.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(Result.failure(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<ValidationErrorItem>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<ValidationErrorItem> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationErrorItem)
                .toList();
        return ResponseEntity
                .status(ErrorCode.PARAM_VALIDATION_FAILED.getHttpStatus())
                .body(Result.failure(
                        ErrorCode.PARAM_VALIDATION_FAILED.getCode(),
                        ErrorCode.PARAM_VALIDATION_FAILED.getMessage(),
                        errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<List<ValidationErrorItem>>> handleBindException(BindException ex) {
        List<ValidationErrorItem> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationErrorItem)
                .toList();
        return ResponseEntity
                .status(ErrorCode.PARAM_VALIDATION_FAILED.getHttpStatus())
                .body(Result.failure(
                        ErrorCode.PARAM_VALIDATION_FAILED.getCode(),
                        ErrorCode.PARAM_VALIDATION_FAILED.getMessage(),
                        errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<List<ValidationErrorItem>>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        List<ValidationErrorItem> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> new ValidationErrorItem(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return ResponseEntity
                .status(ErrorCode.PARAM_VALIDATION_FAILED.getHttpStatus())
                .body(Result.failure(
                        ErrorCode.PARAM_VALIDATION_FAILED.getCode(),
                        ErrorCode.PARAM_VALIDATION_FAILED.getMessage(),
                        errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数类型不匹配：" + ex.getName();
        return ResponseEntity
                .status(ErrorCode.PARAM_TYPE_MISMATCH.getHttpStatus())
                .body(Result.failure(ErrorCode.PARAM_TYPE_MISMATCH, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(ErrorCode.REQUEST_BODY_INVALID.getHttpStatus())
                .body(Result.failure(ErrorCode.REQUEST_BODY_INVALID));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKeyException(DuplicateKeyException ex) {
        return ResponseEntity
                .status(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus())
                .body(Result.failure(ErrorCode.DUPLICATE_RESOURCE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("未处理的系统异常", ex);
        return ResponseEntity
                .status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.failure(ErrorCode.SYSTEM_ERROR));
    }

    private ValidationErrorItem toValidationErrorItem(FieldError fieldError) {
        return new ValidationErrorItem(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
