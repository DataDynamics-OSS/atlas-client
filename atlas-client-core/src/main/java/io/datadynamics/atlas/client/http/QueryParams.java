package io.datadynamics.atlas.client.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ordered, multi-valued query string builder. {@code null} values are skipped so optional
 * parameters can be added unconditionally.
 */
public final class QueryParams {

    private final List<Map.Entry<String, String>> entries = new ArrayList<>();

    public static QueryParams create() {
        return new QueryParams();
    }

    public QueryParams add(String name, Object value) {
        if (value != null) {
            entries.add(Map.entry(name, value.toString()));
        }
        return this;
    }

    public QueryParams addAll(String name, Collection<?> values) {
        if (values != null) {
            values.forEach(v -> add(name, v));
        }
        return this;
    }

    /** Adds Atlas unique-attribute parameters: {@code attr:<name>=<value>}. */
    public QueryParams uniqueAttributes(Map<String, ?> uniqueAttributes) {
        if (uniqueAttributes != null) {
            uniqueAttributes.forEach((name, value) -> add("attr:" + name, value));
        }
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** @return the encoded query string without the leading {@code ?}, or an empty string */
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

    @Override
    public String toString() {
        return toQueryString();
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encodes a single path segment (type names, guids, qualified names). */
    public static String pathSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Path segment must not be empty");
        }
        return encode(segment);
    }
}
