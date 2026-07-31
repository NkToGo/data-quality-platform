package io.github.nktogo.dataquality.validation;

public record ValidationSummary(long totalRows, long validRows, long invalidRows, long issueCount) {

  public ValidationSummary {
    if (totalRows < 0 || validRows < 0 || invalidRows < 0 || issueCount < 0) {
      throw new IllegalArgumentException("validation summary values must not be negative");
    }
    if (invalidRows > totalRows || validRows != totalRows - invalidRows) {
      throw new IllegalArgumentException("validRows plus invalidRows must equal totalRows");
    }
  }
}
