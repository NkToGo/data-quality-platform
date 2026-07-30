package io.github.nktogo.dataquality.dataset;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ValidationRuleController.class)
class ValidationRuleExceptionHandler {

  @ExceptionHandler(InvalidValidationRuleParametersException.class)
  ProblemDetail handleInvalidValidationRuleParameters(
      InvalidValidationRuleParametersException exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle("Invalid Validation Rule parameters");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return problemDetail;
  }
}
