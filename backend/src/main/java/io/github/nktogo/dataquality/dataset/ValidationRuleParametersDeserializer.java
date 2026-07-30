package io.github.nktogo.dataquality.dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

final class ValidationRuleParametersDeserializer extends StdDeserializer<Map<String, Object>> {

  ValidationRuleParametersDeserializer() {
    super(Map.class);
  }

  @Override
  public Map<String, Object> deserialize(
      JsonParser parser, DeserializationContext deserializationContext) throws JacksonException {
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      return deserializationContext.reportInputMismatch(
          Map.class, "Validation Rule parameters must be a JSON object.");
    }

    return readObject(parser, deserializationContext);
  }

  private Map<String, Object> readObject(
      JsonParser parser, DeserializationContext deserializationContext) throws JacksonException {
    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      String name = parser.currentName();
      parser.nextToken();
      values.put(name, readValue(parser, deserializationContext));
    }
    return values;
  }

  private List<Object> readArray(JsonParser parser, DeserializationContext deserializationContext)
      throws JacksonException {
    List<Object> values = new ArrayList<>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      values.add(readValue(parser, deserializationContext));
    }
    return values;
  }

  private Object readValue(JsonParser parser, DeserializationContext deserializationContext)
      throws JacksonException {
    return switch (parser.currentToken()) {
      case START_OBJECT -> readObject(parser, deserializationContext);
      case START_ARRAY -> readArray(parser, deserializationContext);
      case VALUE_STRING -> parser.getString();
      case VALUE_NUMBER_INT -> parser.getNumberValueExact();
      case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
      case VALUE_TRUE, VALUE_FALSE -> parser.getBooleanValue();
      case VALUE_NULL -> null;
      default -> deserializationContext.handleUnexpectedToken(Object.class, parser);
    };
  }
}
