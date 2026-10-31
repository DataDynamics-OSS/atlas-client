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
 * Atlas v2 JSON 형식에 맞춘 Jackson 설정과, 자주 쓰는 직렬화·역직렬화 도우미를 제공합니다.
 * <ul>
 *     <li>모르는 속성은 오류 없이 넘어가고, 모르는 enum 값은 {@code null}로 읽습니다. 그래서 더 새로운
 *     Atlas 버전이 필드나 enum 값을 추가해도 클라이언트가 깨지지 않습니다.</li>
 *     <li>{@code null} 값은 JSON에 쓰지 않습니다. Atlas는 부분 수정(partial update)에서 필드가 아예 없는 것과
 *     명시적으로 {@code null}인 것을 다르게 처리하기 때문입니다.</li>
 *     <li>{@link java.time.Instant} 값은 Atlas의 {@code Date} 필드처럼 epoch 밀리초 숫자로 씁니다.</li>
 * </ul>
 * <p>
 * 공유 매퍼({@link #mapper()})는 설정을 마친 뒤 바꾸지 않으므로 여러 스레드에서 함께 써도 됩니다.
 */
public final class AtlasJson {

    private static final ObjectMapper MAPPER = newObjectMapper();

    // 정적 메서드만 있으므로 인스턴스를 만들지 못하게 막습니다.
    private AtlasJson() {
    }

    /**
     * Atlas 설정을 적용한 새 {@link ObjectMapper}를 만듭니다. 설정을 더 바꿔야 할 때는 공유 매퍼 대신 이것을 씁니다.
     *
     * @return 새로 만든, 설정이 적용된 매퍼
     */
    public static ObjectMapper newObjectMapper() {
        return configure(new ObjectMapper());
    }

    /**
     * 어떤 매퍼에든 Atlas 설정을 적용합니다. 예를 들어 타입 정의 파일을 읽는 {@code YAMLMapper}에 씁니다.
     *
     * @param mapper 설정할 매퍼(그 자리에서 바뀝니다)
     * @param <M>    매퍼 타입
     * @return 같은 매퍼 객체(체이닝용)
     */
    public static <M extends ObjectMapper> M configure(M mapper) {
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        // Atlas는 시간을 epoch 밀리초로 주고받으므로, 숫자 시간 값을 나노초가 아닌 밀리초로 해석하고 씁니다.
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        // 필드가 하나도 없는 객체도 오류 없이 빈 JSON 객체로 직렬화합니다.
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }

    /**
     * JSON을 거쳐 객체를 깊은 복사합니다. {@link io.datadynamics.atlas.client.model.AtlasModel}의 추가 속성도
     * 함께 복사됩니다.
     *
     * @param value 복사할 객체, {@code null}이면 {@code null}을 돌려줍니다
     * @param <T>   객체 타입
     * @return 같은 클래스의 새 객체
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T value) {
        if (value == null) {
            return null;
        }
        return (T) MAPPER.convertValue(MAPPER.valueToTree(value), value.getClass());
    }

    /**
     * 미리 설정해 둔 공유 매퍼를 돌려줍니다. 이 매퍼의 설정을 바꾸지 마세요. 다른 설정이 필요하면
     * {@link #newObjectMapper()}로 새 매퍼를 만들어 씁니다.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 객체를 한 줄짜리 JSON 문자열로 직렬화합니다.
     *
     * @param value 직렬화할 객체
     * @return JSON 문자열
     * @throws AtlasClientException 직렬화에 실패했을 때
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to serialize " + value.getClass().getName() + " to JSON", e);
        }
    }

    /**
     * 객체를 사람이 읽기 좋게 들여쓴 JSON 문자열로 직렬화합니다. 로그나 디버깅 출력에 씁니다.
     *
     * @param value 직렬화할 객체
     * @return 들여쓰기된 JSON 문자열
     * @throws AtlasClientException 직렬화에 실패했을 때
     */
    public static String toPrettyJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to serialize " + value.getClass().getName() + " to JSON", e);
        }
    }

    /**
     * JSON 문자열을 주어진 클래스의 객체로 읽습니다.
     *
     * @param json JSON 문자열
     * @param type 결과 클래스
     * @param <T>  결과 타입
     * @return 읽은 객체
     * @throws AtlasClientException JSON이 올바르지 않거나 변환할 수 없을 때
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to parse JSON as " + type.getName(), e);
        }
    }

    /**
     * JSON 문자열을 제네릭 타입(예: {@code List<AtlasEntityHeader>})의 객체로 읽습니다.
     *
     * @param json JSON 문자열
     * @param type 결과 타입을 나타내는 {@link TypeReference}
     * @param <T>  결과 타입
     * @return 읽은 객체
     * @throws AtlasClientException JSON이 올바르지 않거나 변환할 수 없을 때
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Failed to parse JSON as " + type.getType(), e);
        }
    }

    /**
     * 호환되는 표현 사이를 변환합니다. 예를 들어 {@code Map} 형태의 속성 값을 모델 클래스로 바꿀 때 씁니다.
     * 변환에 실패하면 Jackson의 {@link IllegalArgumentException}이 그대로 전달됩니다.
     *
     * @param value 변환할 값
     * @param type  결과 클래스
     * @param <T>   결과 타입
     * @return 변환된 객체
     */
    public static <T> T convert(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }
}
