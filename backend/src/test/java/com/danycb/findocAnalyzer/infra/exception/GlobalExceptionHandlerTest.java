package com.danycb.findocAnalyzer.infra.exception;

import com.danycb.findocAnalyzer.features.chat.application.AiAnalysisException;
import com.danycb.findocAnalyzer.features.identity.application.exception.DuplicateTeamNameException;
import com.danycb.findocAnalyzer.features.identity.application.exception.DuplicateUsernameException;
import com.danycb.findocAnalyzer.features.identity.application.exception.ForbiddenOperationException;
import com.danycb.findocAnalyzer.features.identity.application.exception.InvalidCredentialsException;
import com.danycb.findocAnalyzer.features.identity.application.exception.NotFoundException;
import com.danycb.findocAnalyzer.features.identity.application.exception.OnboardingDisabledException;
import com.danycb.findocAnalyzer.features.identity.application.exception.TeamNotEmptyException;
import com.danycb.findocAnalyzer.features.vault.application.ResourceNotFoundException;
import com.danycb.findocAnalyzer.infra.security.InvalidAccessTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_returnsBadRequestWithFirstFieldErrorMessage() throws NoSuchMethodException {
        MethodArgumentNotValidException exception = validationException("must not be blank");

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("must not be blank"));
    }

    @Test
    void handleResourceNotFound_returnsNotFound() {
        ResourceNotFoundException exception = new ResourceNotFoundException("document not found");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("document not found"));
    }

    @Test
    void handleAiAnalysisException_returnsBadGateway() {
        AiAnalysisException exception = new AiAnalysisException("ai provider failed", new RuntimeException("boom"));

        ResponseEntity<ErrorResponse> response = handler.handleAiAnalysisException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("ai provider failed"));
    }

    @Test
    void handleInvalidCredentials_returnsUnauthorized() {
        InvalidCredentialsException exception = new InvalidCredentialsException("bad credentials");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("bad credentials"));
    }

    @Test
    void handleInvalidAccessToken_returnsUnauthorized() {
        InvalidAccessTokenException exception = new InvalidAccessTokenException("token expired");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidAccessToken(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("token expired"));
    }

    @Test
    void handleAccessDenied_returnsForbiddenWithExceptionMessage() {
        AccessDeniedException exception = new AccessDeniedException("no permission");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("no permission"));
    }

    @Test
    void handleAccessDenied_withBlankMessage_defaultsToAccessDenied() {
        AccessDeniedException exception = new AccessDeniedException(null);

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Access denied"));
    }

    @Test
    void handleNotFound_returnsNotFound() {
        NotFoundException exception = new NotFoundException("team not found");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("team not found"));
    }

    @Test
    void handleForbidden_returnsForbidden() {
        ForbiddenOperationException exception = new ForbiddenOperationException("not allowed");

        ResponseEntity<ErrorResponse> response = handler.handleForbidden(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("not allowed"));
    }

    @Test
    void handleConflict_mapsOnboardingDisabledExceptionToConflict() {
        OnboardingDisabledException exception = new OnboardingDisabledException("already onboarded");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("already onboarded"));
    }

    @Test
    void handleConflict_mapsDuplicateUsernameExceptionToConflict() {
        DuplicateUsernameException exception = new DuplicateUsernameException("username taken");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("username taken"));
    }

    @Test
    void handleConflict_mapsDuplicateTeamNameExceptionToConflict() {
        DuplicateTeamNameException exception = new DuplicateTeamNameException("team name taken");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("team name taken"));
    }

    @Test
    void handleConflict_mapsTeamNotEmptyExceptionToConflict() {
        TeamNotEmptyException exception = new TeamNotEmptyException("team has members");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("team has members"));
    }

    @Test
    void handleConflict_mapsLimitExceededExceptionToConflict() {
        LimitExceededException exception = new LimitExceededException("limit reached");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("limit reached"));
    }

    @Test
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException exception = new IllegalArgumentException("bad argument");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("bad argument"));
    }

    private MethodArgumentNotValidException validationException(String fieldMessage) throws NoSuchMethodException {
        Method method = DummyTarget.class.getMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new DummyTarget(), "dummyTarget");
        bindingResult.addError(new FieldError("dummyTarget", "field", fieldMessage));
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    private static class DummyTarget {
        public void dummyMethod(String arg) {
        }
    }
}
