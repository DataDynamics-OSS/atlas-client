package io.datadynamics.atlas.client.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.datadynamics.atlas.client.exception.AtlasClientException;

/**
 * Jackson configuration matching the Atlas v2 wire format.
 * <ul>
 *     <li>unknown properties are tolerated, and unknown enum values read as {@code null}, so newer
 *     Atlas versions do not break the client</li>
 *     <li>{@code null} values are omitted, as Atlas treats a missing field and an explicit null differently
 *     on partial updates</li>
 *     <li>{@link java.time.Instant} values are written as epoch milliseconds, like Atlas {@code Date} fields</li>
 * </ul>
 */
public final class AtlasJson {

    private static final ObjectMapper MAPPER = newObjectMapper();

    private AtlasJson() {
    }

    public static ObjectMapper newObjectMapper() {
        return configure(new ObjectMapper());
    }

    /**
     * Applies the Atlas settings to any mapper, e.g. a {@code YAMLMapper} reading type definition files.
     */
    public static <M extends ObjectMapper> M configure(M mapper) {
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }

    /** Deep copy through JSON, preserving additional properties. */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T value) {
        if (value == null) {
            return null;
        }
        return (T) MAPPER.convertValue(MAPPER.valueToTree(value), value.getClass());
    }

    /** The shared, pre-configured mapper. Do not reconfigure it; use {@link #newObjectMapper()} instead. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to serialize " + value.getClass().getName() + " to JSON", e);
        }
    }

    public static String toPrettyJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to serialize " + value.getClass().getName() + " to JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to parse JSON as " + type.getName(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to parse JSON as " + type.getType(), e);
        }
    }

    /** Converts between compatible representations, e.g. a {@code Map} attribute value into a model class. */
    public static <T> T convert(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }
}
