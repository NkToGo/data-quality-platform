package io.github.nktogo.dataquality.ingestion;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ValidationRunController.class)
class ValidationRunExceptionHandler {

  @ExceptionHandler(ValidationRunNotFoundException.class)
  ProblemDetail handleValidationRunNotFound(
      ValidationRunNotFoundException exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problemDetail.setTitle("Validation Run not found");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return problemDetail;
  }

  @ExceptionHandler(ValidationRunParentMismatchException.class)
  ProblemDetail handleParentMismatch(
      ValidationRunParentMismatchException exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problemDetail.setTitle("Validation Run parent mismatch");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return problemDetail;
  }
}
