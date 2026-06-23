package com.danycb.findocAnalyzer.infra.exception;

public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
        super(message);
    }
}
