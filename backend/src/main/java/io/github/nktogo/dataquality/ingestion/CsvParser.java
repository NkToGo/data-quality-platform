package io.github.nktogo.dataquality.ingestion;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
final class CsvParser {

  private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final CSVFormat CSV_FORMAT =
      CSVFormat.RFC4180
          .builder()
          .setDelimiter(',')
          .setQuote('"')
          .setEscape(null)
          .setCommentMarker(null)
          .setIgnoreEmptyLines(false)
          .setIgnoreSurroundingSpaces(false)
          .setTrim(false)
          .setLenientEof(false)
          .setTrailingData(false)
          .get();

  ParsedCsv parse(byte[] content) {
    Objects.requireNonNull(content, "content must not be null");

    String decodedContent = decode(content);

    try (var parser = CSV_FORMAT.parse(new StringReader(decodedContent))) {
      Iterator<CSVRecord> records = parser.iterator();
      if (!records.hasNext()) {
        throw new CsvParsingException("CSV content must contain a header record.");
      }

      CSVRecord headerRecord = records.next();
      List<String> headers = valuesOf(headerRecord);
      validateHeaders(headers);

      List<ParsedCsvRow> rows = new ArrayList<>();
      while (records.hasNext()) {
        CSVRecord record = records.next();
        validateRecordWidth(record, headers.size());
        rows.add(new ParsedCsvRow(record.getRecordNumber(), valuesOf(record)));
      }

      return new ParsedCsv(headers, rows);
    } catch (UncheckedIOException | IOException exception) {
      throw new CsvParsingException("CSV content is malformed.", exception);
    }
  }

  private String decode(byte[] content) {
    int offset = startsWithUtf8Bom(content) ? UTF_8_BOM.length : 0;
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    try {
      return decoder.decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
    } catch (CharacterCodingException exception) {
      throw new CsvParsingException("CSV content is not valid UTF-8.", exception);
    }
  }

  private boolean startsWithUtf8Bom(byte[] content) {
    if (content.length < UTF_8_BOM.length) {
      return false;
    }

    for (int index = 0; index < UTF_8_BOM.length; index++) {
      if (content[index] != UTF_8_BOM[index]) {
        return false;
      }
    }
    return true;
  }

  private List<String> valuesOf(CSVRecord record) {
    List<String> values = new ArrayList<>(record.size());
    record.forEach(values::add);
    return List.copyOf(values);
  }

  private void validateHeaders(List<String> headers) {
    Map<String, Integer> firstColumnByName = new HashMap<>();

    for (int index = 0; index < headers.size(); index++) {
      String header = headers.get(index);
      int columnNumber = index + 1;

      if (header.isBlank()) {
        throw new CsvParsingException("CSV header column " + columnNumber + " must have a name.");
      }

      Integer firstColumnNumber = firstColumnByName.putIfAbsent(header, columnNumber);
      if (firstColumnNumber != null) {
        throw new CsvParsingException(
            "CSV header columns "
                + firstColumnNumber
                + " and "
                + columnNumber
                + " have duplicate names.");
      }
    }
  }

  private void validateRecordWidth(CSVRecord record, int expectedFieldCount) {
    if (record.size() != expectedFieldCount) {
      throw new CsvParsingException(
          "CSV record "
              + record.getRecordNumber()
              + " has "
              + record.size()
              + " fields; expected "
              + expectedFieldCount
              + ".");
    }
  }
}
