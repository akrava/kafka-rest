/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafkarest.converters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Provides conversion of JSON to/from Protobuf.
 */
public class ProtobufConverter implements SchemaConverter {

  private static final Logger log = LoggerFactory.getLogger(ProtobufConverter.class);

  private static final ObjectMapper jsonMapper = new ObjectMapper();

  @Override
  public Object toObject(JsonNode value, ParsedSchema parsedSchema) {
    try {
      ProtobufSchema schema = (ProtobufSchema) parsedSchema;
      StringWriter out = new StringWriter();
      jsonMapper.writeValue(out, value);
      String jsonString = out.toString();
      DynamicMessage.Builder message = schema.newMessageBuilder();
      JsonFormat.parser().merge(jsonString, message);
      return message.build();
    } catch (IOException | RuntimeException e) {
      throw new ConversionException("Failed to convert JSON to Protobuf: " + e.getMessage());
    }
  }

  /**
   * Converts Protobuf data to their equivalent JsonNode representation.
   *
   * @param value the value to convert
   * @return an object containing the root JsonNode representing the converted object and the size
   *     in bytes of the data when serialized
   */
  @Override
  public JsonNodeAndSize toJson(Object value) {
    try {
      if (value == null) {
        return new JsonNodeAndSize(null, 0);
      }
      Message object = (Message) value;
      String jsonString = JsonFormat.printer().print(object);
      byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
      return new JsonNodeAndSize(jsonMapper.readTree(bytes), bytes.length);
    } catch (IOException e) {
      log.error("Jackson failed to deserialize JSON generated by Protobuf's JSON encoder: ", e);
      throw new ConversionException("Failed to convert Protobuf to JSON: " + e.getMessage());
    } catch (RuntimeException e) {
      log.error("Unexpected exception convertion Protobuf to JSON: ", e);
      throw new ConversionException("Failed to convert Protobuf to JSON: " + e.getMessage());
    }
  }
}
