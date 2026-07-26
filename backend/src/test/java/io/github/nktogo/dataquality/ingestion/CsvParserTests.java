package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvParserTests {

  private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private final CsvParser parser = new CsvParser();

  @Test
  void parsesHeaderAndRowsInSourceOrder() {
    ParsedCsv parsed = parse("name,age\nAlice,30\nBob,41");

    assertThat(parsed.headers()).containsExactly("name", "age");
    assertThat(parsed.rows())
        .containsExactly(
            new ParsedCsvRow(2, List.of("Alice", "30")), new ParsedCsvRow(3, List.of("Bob", "41")));
  }

  @Test
  void parsesNonAsciiUtf8Content() {
    ParsedCsv parsed = parse("name,city\nŁukasz,Kraków\n李雷,北京");

    assertThat(parsed.rows())
        .containsExactly(
            new ParsedCsvRow(2, List.of("Łukasz", "Kraków")),
            new ParsedCsvRow(3, List.of("李雷", "北京")));
  }

  @Test
  void acceptsAndRemovesOneLeadingUtf8Bom() {
    byte[] csv = "name,city\nAlice,Berlin".getBytes(StandardCharsets.UTF_8);
    byte[] content = new byte[UTF_8_BOM.length + csv.length];
    System.arraycopy(UTF_8_BOM, 0, content, 0, UTF_8_BOM.length);
    System.arraycopy(csv, 0, content, UTF_8_BOM.length, csv.length);

    ParsedCsv parsed = parser.parse(content);

    assertThat(parsed.headers()).containsExactly("name", "city");
    assertThat(parsed.rows()).containsExactly(new ParsedCsvRow(2, List.of("Alice", "Berlin")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void acceptsLfCrLfAndCrRecordSeparators(String separator) {
    ParsedCsv parsed = parse("name,age" + separator + "Alice,30" + separator + "Bob,41");

    assertThat(parsed.rows())
        .containsExactly(
            new ParsedCsvRow(2, List.of("Alice", "30")), new ParsedCsvRow(3, List.of("Bob", "41")));
  }

  @Test
  void parsesQuotedCommasAndDoubledQuotes() {
    ParsedCsv parsed = parse("name,note\n\"Doe, Jane\",\"She said \"\"hello\"\"\"");

    assertThat(parsed.rows())
        .containsExactly(new ParsedCsvRow(2, List.of("Doe, Jane", "She said \"hello\"")));
  }

  @Test
  void preservesEmbeddedLfInsideQuotedField() {
    ParsedCsv parsed = parse("id,note\n1,\"first line\nsecond line\"");

    assertThat(parsed.rows())
        .containsExactly(new ParsedCsvRow(2, List.of("1", "first line\nsecond line")));
  }

  @Test
  void preservesEmbeddedCrLfInsideQuotedField() {
    ParsedCsv parsed = parse("id,note\r\n1,\"first line\r\nsecond line\"");

    assertThat(parsed.rows())
        .containsExactly(new ParsedCsvRow(2, List.of("1", "first line\r\nsecond line")));
  }

  @Test
  void preservesEmbeddedCrInsideQuotedField() {
    ParsedCsv parsed = parse("id,note\r1,\"first line\rsecond line\"");

    assertThat(parsed.rows())
        .containsExactly(new ParsedCsvRow(2, List.of("1", "first line\rsecond line")));
  }

  @Test
  void preservesHeaderAndDataWhitespaceExactly() {
    ParsedCsv parsed = parse(" name ,Name,name\n value ,  second  ,third ");

    assertThat(parsed.headers()).containsExactly(" name ", "Name", "name");
    assertThat(parsed.rows())
        .containsExactly(new ParsedCsvRow(2, List.of(" value ", "  second  ", "third ")));
  }

  @Test
  void preservesLeadingMiddleQuotedAndTrailingEmptyFields() {
    ParsedCsv parsed = parse("first,second,third,fourth\n,,\"\",");

    assertThat(parsed.rows()).containsExactly(new ParsedCsvRow(2, List.of("", "", "", "")));
  }

  @Test
  void treatsBlankRecordsAsOneEmptyField() {
    ParsedCsv parsed = parse("value\nfirst\n\nlast");

    assertThat(parsed.rows())
        .containsExactly(
            new ParsedCsvRow(2, List.of("first")),
            new ParsedCsvRow(3, List.of("")),
            new ParsedCsvRow(4, List.of("last")));
  }

  @Test
  void treatsSemicolonsTabsAndCommentMarkersAsOrdinaryContent() {
    ParsedCsv parsed = parse("value\nleft;middle\tright\n#not-a-comment");

    assertThat(parsed.rows())
        .containsExactly(
            new ParsedCsvRow(2, List.of("left;middle\tright")),
            new ParsedCsvRow(3, List.of("#not-a-comment")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"name,age", "name,age\n", "name,age\r\n", "name,age\r"})
  void acceptsHeaderOnlyInputWithoutCreatingRows(String content) {
    ParsedCsv parsed = parse(content);

    assertThat(parsed.headers()).containsExactly("name", "age");
    assertThat(parsed.rows()).isEmpty();
  }

  @Test
  void numbersLogicalRecordsIncludingHeaderBlankAndMultilineRecords() {
    ParsedCsv parsed = parse("value\n\"first\ncontinued\"\n\nlast");

    assertThat(parsed.rows())
        .containsExactly(
            new ParsedCsvRow(2, List.of("first\ncontinued")),
            new ParsedCsvRow(3, List.of("")),
            new ParsedCsvRow(4, List.of("last")));
  }

  @Test
  void treatsQuotesInsideUnquotedFieldsAsLiteralContent() {
    ParsedCsv parsed = parse("value\nbefore\"after");

    assertThat(parsed.rows()).containsExactly(new ParsedCsvRow(2, List.of("before\"after")));
  }

  @Test
  void parsedModelsDefensivelyCopyAndExposeImmutableLists() {
    List<String> sourceHeaders = new ArrayList<>(List.of("name"));
    List<String> sourceValues = new ArrayList<>(List.of("Alice"));
    ParsedCsvRow row = new ParsedCsvRow(2, sourceValues);
    List<ParsedCsvRow> sourceRows = new ArrayList<>(List.of(row));

    ParsedCsv parsed = new ParsedCsv(sourceHeaders, sourceRows);
    sourceHeaders.set(0, "changed");
    sourceValues.set(0, "changed");
    sourceRows.clear();

    assertThat(parsed.headers()).containsExactly("name");
    assertThat(parsed.rows()).containsExactly(new ParsedCsvRow(2, List.of("Alice")));
    assertThatThrownBy(() -> parsed.headers().add("age"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> parsed.rows().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> parsed.rows().getFirst().values().add("Bob"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMalformedUtf8() {
    assertParsingFailure(new byte[] {(byte) 0xC3, (byte) 0x28}, "CSV content is not valid UTF-8.");
  }

  @Test
  void rejectsEmptyInput() {
    assertParsingFailure(new byte[0], "CSV content must contain a header record.");
  }

  @Test
  void rejectsBomOnlyInput() {
    assertParsingFailure(UTF_8_BOM, "CSV content must contain a header record.");
  }

  @Test
  void rejectsEmptyHeaderName() {
    assertParsingFailure(",name\nvalue,Alice", "CSV header column 1 must have a name.");
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "   ", "\t", "\u2003"})
  void rejectsWhitespaceOnlyHeaderName(String blankHeader) {
    assertParsingFailure(
        "id," + blankHeader + ",name\n1,value,Alice", "CSV header column 2 must have a name.");
  }

  @Test
  void rejectsExactDuplicateHeaderNames() {
    assertParsingFailure(
        "id,name,id\n1,Alice,2", "CSV header columns 1 and 3 have duplicate names.");
  }

  @Test
  void rejectsRecordsWithFewerFieldsThanHeader() {
    assertParsingFailure("id,name,age\n1,Alice", "CSV record 2 has 2 fields; expected 3.");
  }

  @Test
  void rejectsRecordsWithMoreFieldsThanHeader() {
    assertParsingFailure("id,name\n1,Alice,extra", "CSV record 2 has 3 fields; expected 2.");
  }

  @Test
  void rejectsBlankRecordWhenHeaderHasMultipleColumns() {
    assertParsingFailure("id,name\n\n1,Alice", "CSV record 2 has 1 fields; expected 2.");
  }

  @Test
  void rejectsUnterminatedQuotedField() {
    assertParsingFailure("id,note\n1,\"unterminated", "CSV content is malformed.");
  }

  @Test
  void rejectsTrailingCharactersAfterClosingQuote() {
    assertParsingFailure("id,note\n1,\"quoted\"tail", "CSV content is malformed.");
  }

  @Test
  void rejectsBackslashEscapedQuotes() {
    assertParsingFailure("id,note\n1,\"before\\\"after\"", "CSV content is malformed.");
  }

  @Test
  void rejectsNullAsProgrammingError() {
    assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
  }

  private ParsedCsv parse(String content) {
    return parser.parse(content.getBytes(StandardCharsets.UTF_8));
  }

  private void assertParsingFailure(String content, String expectedMessage) {
    assertParsingFailure(content.getBytes(StandardCharsets.UTF_8), expectedMessage);
  }

  private void assertParsingFailure(byte[] content, String expectedMessage) {
    assertThatThrownBy(() -> parser.parse(content))
        .isInstanceOf(CsvParsingException.class)
        .hasMessage(expectedMessage);
  }
}
