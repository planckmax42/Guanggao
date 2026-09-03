package com.example.adplatform.common.exception;

public class DependencyException extends AppException {

    public DependencyException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DependencyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DependencyException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DependencyException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
