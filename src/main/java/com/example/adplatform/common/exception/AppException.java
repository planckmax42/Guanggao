package com.example.adplatform.common.exception;

import lombok.Getter;

@Getter
public abstract class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    protected AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    protected AppException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
