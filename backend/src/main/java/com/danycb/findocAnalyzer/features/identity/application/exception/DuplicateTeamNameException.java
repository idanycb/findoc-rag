package com.danycb.findocAnalyzer.features.identity.application.exception;

public class DuplicateTeamNameException extends RuntimeException {
    public DuplicateTeamNameException(String message) {
        super(message);
    }
}
