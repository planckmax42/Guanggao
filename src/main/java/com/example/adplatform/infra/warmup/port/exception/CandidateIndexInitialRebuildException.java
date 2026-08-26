package com.example.adplatform.infra.warmup.port.exception;

public class CandidateIndexInitialRebuildException extends RuntimeException {
    public CandidateIndexInitialRebuildException(String message,Throwable cause) {
        super(message,cause);
    }
}
