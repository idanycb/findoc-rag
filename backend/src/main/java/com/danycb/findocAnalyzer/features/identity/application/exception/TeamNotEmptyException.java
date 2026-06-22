package com.danycb.findocAnalyzer.features.identity.application.exception;

/**
 * Thrown when deleting a team that still has members.
 */
public class TeamNotEmptyException extends RuntimeException {
    public TeamNotEmptyException(String message) {
        super(message);
    }
}
