package io.github.nktogo.dataquality.reporting;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ValidationReportController.class)
class ValidationReportExceptionHandler {

  @ExceptionHandler(InvalidReportFormatException.class)
  ProblemDetail handleInvalidReportFormat(
      InvalidReportFormatException exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle("Invalid report format");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return problemDetail;
  }
}
