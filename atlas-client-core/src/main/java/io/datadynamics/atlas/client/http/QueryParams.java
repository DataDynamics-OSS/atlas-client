package io.datadynamics.atlas.client.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 순서를 유지하고 같은 이름을 여러 번 쓸 수 있는 쿼리 문자열 빌더입니다.
 * <p>
 * {@code null} 값은 건너뛰므로, 선택적인 파라미터도 조건문 없이 그냥 추가하면 됩니다. 이름과 값은 UTF-8로
 * URL 인코딩되며 공백은 {@code +}가 아닌 {@code %20}으로 바뀝니다.
 * <p>
 * 스레드 안전하지 않습니다. 요청 하나를 만드는 동안 한 스레드에서만 씁니다.
 */
public final class QueryParams {

    private final List<Map.Entry<String, String>> entries = new ArrayList<>();

    /** 빈 빌더를 만듭니다. */
    public static QueryParams create() {
        return new QueryParams();
    }

    /**
     * 파라미터 하나를 추가합니다. 값은 {@link Object#toString()}으로 문자열이 됩니다.
     *
     * @param name  파라미터 이름
     * @param value 파라미터 값, {@code null}이면 아무것도 추가하지 않습니다
     * @return 메서드 체이닝을 위한 이 객체
     */
    public QueryParams add(String name, Object value) {
        if (value != null) {
            entries.add(Map.entry(name, value.toString()));
        }
        return this;
    }

    /**
     * 같은 이름으로 여러 값을 추가합니다(예: {@code guid=a&guid=b}). 컬렉션 안의 {@code null} 값은 건너뜁니다.
     *
     * @param name   파라미터 이름
     * @param values 값 목록, {@code null}이면 아무것도 추가하지 않습니다
     * @return 메서드 체이닝을 위한 이 객체
     */
    public QueryParams addAll(String name, Collection<?> values) {
        if (values != null) {
            values.forEach(v -> add(name, v));
        }
        return this;
    }

    /**
     * Atlas의 유일 속성 파라미터를 {@code attr:<이름>=<값>} 형태로 추가합니다.
     *
     * @param uniqueAttributes 유일 속성 이름과 값, {@code null}이면 아무것도 추가하지 않습니다
     * @return 메서드 체이닝을 위한 이 객체
     */
    public QueryParams uniqueAttributes(Map<String, ?> uniqueAttributes) {
        if (uniqueAttributes != null) {
            uniqueAttributes.forEach((name, value) -> add("attr:" + name, value));
        }
        return this;
    }

    /** 추가된 파라미터가 하나도 없으면 {@code true}를 돌려줍니다. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 인코딩된 쿼리 문자열을 만듭니다.
     *
     * @return 앞에 {@code ?}를 붙이지 않은 인코딩된 쿼리 문자열, 파라미터가 없으면 빈 문자열
     */
    public String toQueryString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : entries) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    /** {@link #toQueryString()}과 같은 값을 돌려줍니다. */
    @Override
    public String toString() {
        return toQueryString();
    }

    // URLEncoder는 폼 인코딩 규칙에 따라 공백을 "+"로 바꾸지만, 경로와 쿼리에서 확실히 공백으로 해석되도록
    // "%20"으로 바꿉니다. 원래 "+" 문자는 "%2B"로 인코딩되므로 이 치환과 섞이지 않습니다.
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 경로 조각 하나(타입 이름, guid, qualifiedName 등)를 URL 인코딩합니다. {@code /}도 인코딩되므로
     * 값 안에 슬래시가 있어도 경로가 나뉘지 않습니다.
     *
     * @param segment 인코딩할 경로 조각
     * @return 인코딩된 문자열
     * @throws IllegalArgumentException {@code segment}가 {@code null}이거나 비어 있을 때
     */
    public static String pathSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Path segment must not be empty");
        }
        return encode(segment);
    }
}
