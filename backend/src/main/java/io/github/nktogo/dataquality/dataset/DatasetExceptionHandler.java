package io.github.nktogo.dataquality.dataset;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DatasetController.class)
class DatasetExceptionHandler {

  @ExceptionHandler(DatasetNotFoundException.class)
  ProblemDetail handleDatasetNotFound(
      DatasetNotFoundException exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problemDetail.setTitle("Dataset not found");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return problemDetail;
  }
}
