package io.github.nktogo.dataquality.ingestion;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class SourceFileExceptionHandler {

  @ExceptionHandler(InvalidSourceFileException.class)
  ProblemDetail handleInvalidSourceFile(
      InvalidSourceFileException exception, HttpServletRequest request) {
    return problemDetail(
        HttpStatus.BAD_REQUEST, "Invalid source file", exception.getMessage(), request);
  }

  @ExceptionHandler(SourceFileTooLargeException.class)
  ProblemDetail handleSourceFileTooLarge(
      SourceFileTooLargeException exception, HttpServletRequest request) {
    return problemDetail(
        HttpStatus.CONTENT_TOO_LARGE, "Source file too large", exception.getMessage(), request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail handleMaximumUploadSize(
      MaxUploadSizeExceededException exception, HttpServletRequest request) {
    return problemDetail(
        HttpStatus.CONTENT_TOO_LARGE,
        "Source file too large",
        "The uploaded file exceeds the configured maximum size.",
        request);
  }

  private ProblemDetail problemDetail(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return problemDetail;
  }
}
