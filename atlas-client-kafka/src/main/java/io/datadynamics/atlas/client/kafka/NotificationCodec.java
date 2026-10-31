package io.datadynamics.atlas.client.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.json.AtlasJson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Atlas가 모든 Kafka 알림을 감싸는 봉투(envelope)를 인코딩하고 디코딩합니다.
 * <pre>{@code
 * {"version":{"version":"1.0.0","versionParts":[1]},"msgCompressionKind":"NONE",
 *  "msgSplitIdx":1,"msgSplitCount":1,"msgSourceIP":"10.0.0.1","msgCreatedBy":"etl",
 *  "msgCreationTime":1700000000000,"spooled":false,"message":{...}}
 * }</pre>
 * Atlas와 마찬가지로, {@code maxMessageBytes}보다 큰 봉투는 GZIP으로 압축한 뒤 Base64로 인코딩하고,
 * 그래도 너무 크면 여러 Kafka 메시지로 나눕니다. 디코딩은 이 과정을 거꾸로 수행하며 나뉜 메시지를 다시
 * 합칩니다. 그래서 디코더는 상태를 가지며, 한 메시지의 모든 조각을 같은 인스턴스가 받아야 합니다.
 * <p>
 * {@link #decode(String)}와 {@link #pendingSplitMessages()}는 {@code synchronized}이므로 여러 스레드에서
 * 하나의 인스턴스를 함께 써도 됩니다. {@link #encode(Object, String)}는 조립 중인 조각을 건드리지 않으므로
 * 잠그지 않습니다.
 */
public final class NotificationCodec {

    /** Atlas의 {@code atlas.notification.message.max.length.bytes} 기본값입니다. */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 1_000_000;

    /** 나뉜 메시지의 조각을 나머지 조각이 올 때까지 보관하는 최대 시간입니다. 지나면 버립니다. */
    static final Duration SPLIT_TIMEOUT = Duration.ofMinutes(5);

    private static final String GZIP = "GZIP";
    private static final String NONE = "NONE";

    private final ObjectMapper mapper = AtlasJson.mapper();
    private final int maxMessageBytes;
    private final boolean compress;
    private final Clock clock;
    // msgId별로 아직 모든 조각이 도착하지 않은 분할 메시지입니다. 이 인스턴스의 모니터로 보호합니다.
    private final Map<String, Split> pending = new LinkedHashMap<>();
    // 처음 인코딩할 때 한 번 구해 두는 로컬 호스트 주소입니다. 여러 스레드가 동시에 구해도 결과가 같으므로 잠그지 않습니다.
    private volatile String sourceIp;

    /**
     * Atlas 기본 최대 크기({@value #DEFAULT_MAX_MESSAGE_BYTES}바이트)와 GZIP 압축을 쓰는 코덱을 만듭니다.
     */
    public NotificationCodec() {
        this(DEFAULT_MAX_MESSAGE_BYTES, true);
    }

    /**
     * 최대 메시지 크기와 압축 여부를 지정해 코덱을 만듭니다.
     *
     * @param maxMessageBytes 이 크기(바이트)를 넘는 메시지는 압축하고, 그래도 크면 나눕니다.
     * @param compress        큰 메시지를 나누기 전에 GZIP으로 압축할지 여부
     * @throws IllegalArgumentException {@code maxMessageBytes}가 1보다 작은 경우
     */
    public NotificationCodec(int maxMessageBytes, boolean compress) {
        this(maxMessageBytes, compress, Clock.systemUTC());
    }

    /** 테스트에서 메시지 생성 시각과 조각 만료 시각을 조절할 수 있도록 시계를 받는 생성자입니다. */
    NotificationCodec(int maxMessageBytes, boolean compress, Clock clock) {
        if (maxMessageBytes < 1) {
            throw new IllegalArgumentException("maxMessageBytes must be positive");
        }
        this.maxMessageBytes = maxMessageBytes;
        this.compress = compress;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 인코딩

    /**
     * {@code message}를 봉투로 감싸고, 보낼 Kafka 메시지 값들을 순서대로 돌려줍니다.
     * 값이 둘 이상이면 메시지가 나뉜 것이므로 전부 보내야 합니다.
     * <p>
     * 봉투에는 버전, 압축 종류, 분할 정보, 이 호스트의 IP({@code msgSourceIP}), 보낸 사용자({@code msgCreatedBy}),
     * 생성 시각(밀리초)을 담습니다. 크기에 따라 다음 중 하나가 됩니다.
     * <ul>
     *   <li>직렬화한 봉투가 {@code maxMessageBytes} 이하이면 그대로 메시지 하나로 보냅니다.</li>
     *   <li>압축을 켰고 GZIP + Base64 결과가 한도 이하이면, 그 문자열을 {@code message}에 담은 메시지 하나로 보냅니다.</li>
     *   <li>그 밖에는 Base64 결과(압축을 껐으면 압축하지 않은 원문의 Base64)를 {@code maxMessageBytes}씩 잘라
     *       같은 {@code msgId}를 가진 여러 메시지로 보냅니다. 조각의 {@code msgSplitIdx}는 0부터 시작합니다.</li>
     * </ul>
     * 나뉜 조각 하나하나에는 봉투 헤더가 더해지므로 Kafka 메시지 하나의 실제 크기는 {@code maxMessageBytes}보다
     * 조금 큽니다.
     *
     * @param message JSON으로 직렬화할 메시지 본문
     * @param user    {@code msgCreatedBy}에 기록할 사용자. {@code null}이면 빈 문자열을 기록합니다.
     * @return 보낼 Kafka 메시지 값 목록(순서 유지)
     * @throws AtlasClientException 메시지를 JSON으로 직렬화할 수 없는 경우
     */
    public List<String> encode(Object message, String user) {
        ObjectNode envelope = header(NONE, 1, 1, null);
        envelope.put("msgSourceIP", sourceIp());
        envelope.put("msgCreatedBy", user == null ? "" : user);
        envelope.put("msgCreationTime", clock.millis());
        envelope.put("spooled", false);
        envelope.set("message", mapper.valueToTree(message));

        String json = write(envelope);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxMessageBytes) {
            return List.of(json);
        }

        String msgId = UUID.randomUUID().toString();
        String kind = compress ? GZIP : NONE;
        byte[] encoded = Base64.getEncoder().encode(compress ? gzip(bytes) : bytes);
        if (compress && encoded.length <= maxMessageBytes) {
            return List.of(stringMessage(msgId, kind, 1, 1, new String(encoded, StandardCharsets.US_ASCII)));
        }
        int count = (encoded.length + maxMessageBytes - 1) / maxMessageBytes;
        List<String> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * maxMessageBytes;
            byte[] chunk = Arrays.copyOfRange(encoded, from, Math.min(from + maxMessageBytes, encoded.length));
            parts.add(stringMessage(msgId, kind, i, count, new String(chunk, StandardCharsets.US_ASCII)));
        }
        return parts;
    }

    /** 봉투의 공통 헤더(버전, 압축 종류, 분할 정보, 있으면 msgId)를 만듭니다. */
    private ObjectNode header(String compressionKind, int splitIdx, int splitCount, String msgId) {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode version = node.putObject("version");
        version.put("version", "1.0.0");
        version.putArray("versionParts").add(1);
        if (msgId != null) {
            node.put("msgId", msgId);
        }
        node.put("msgCompressionKind", compressionKind);
        node.put("msgSplitIdx", splitIdx);
        node.put("msgSplitCount", splitCount);
        return node;
    }

    /** 압축되었거나 나뉜 내용을 문자열 {@code message}로 담은 봉투를 직렬화합니다. */
    private String stringMessage(String msgId, String kind, int idx, int count, String content) {
        ObjectNode node = header(kind, idx, count, msgId);
        node.put("message", content);
        return write(node);
    }

    // ---------------------------------------------------------------- 디코딩

    /**
     * 알림의 {@code message}를 돌려줍니다. {@code value}가 나뉜 메시지의 한 조각이고 다른 조각을 아직 받지
     * 못했다면 빈 값을 돌려줍니다. 마지막 조각을 받으면 모든 조각을 합쳐 원래 메시지를 돌려줍니다.
     *
     * @param value Kafka 메시지 값(JSON 문자열)
     * @return 알림 본문. 아직 조각이 모자라면 빈 값
     * @throws AtlasClientException 값이 올바른 알림이 아닌 경우(JSON이 아님, 잘못된 Base64·GZIP, 잘못된 조각 번호 등)
     */
    public synchronized Optional<JsonNode> decode(String value) {
        JsonNode node = read(value);
        if (!node.isObject() || !node.has("version") || !node.has("message")) {
            // 아주 오래된 hook이 보낸 메시지에는 봉투가 없으므로 값 전체를 본문으로 봅니다.
            return Optional.of(node);
        }
        String kind = node.path("msgCompressionKind").asText(NONE);
        int count = node.path("msgSplitCount").asInt(1);
        if (count > 1) {
            return Optional.ofNullable(addPart(node, count)).map(bytes -> decodeInner(kind, bytes));
        }
        if (GZIP.equals(kind)) {
            return Optional.of(decodeInner(kind, base64(node.path("message").asText())));
        }
        return Optional.of(node.get("message"));
    }

    /**
     * Base64를 푼 바이트를 (GZIP이면 압축을 풀어) 다시 봉투로 해석합니다. 압축·분할된 내용 안에는 원래 봉투
     * 전체가 들어 있으므로 {@link #decode(String)}를 재귀 호출해 본문을 꺼냅니다.
     */
    private JsonNode decodeInner(String kind, byte[] encoded) {
        byte[] bytes = GZIP.equals(kind) ? gunzip(encoded) : encoded;
        return decode(new String(bytes, StandardCharsets.UTF_8))
                .orElseThrow(() -> new AtlasClientException("Reassembled notification is itself a partial message"));
    }

    /**
     * 조각 하나를 저장하고, 모든 조각이 모이면 이어 붙인 Base64를 디코딩해 돌려줍니다. 아직 모자라면 {@code null}을
     * 돌려줍니다. 저장하기 전에 {@link #SPLIT_TIMEOUT}이 지난 미완성 메시지를 먼저 버려 메모리가 쌓이지 않게 합니다.
     * 같은 조각을 다시 받으면 덮어씁니다.
     */
    private byte[] addPart(JsonNode node, int count) {
        String msgId = node.path("msgId").asText(null);
        int idx = node.path("msgSplitIdx").asInt(-1);
        if (msgId == null || idx < 0 || idx >= count) {
            throw new AtlasClientException("Invalid split notification part " + idx + "/" + count + " of " + msgId);
        }
        long now = clock.millis();
        expireParts(now);
        Split split = pending.computeIfAbsent(msgId, id -> new Split(count, now));
        split.parts[idx] = node.path("message").asText();
        if (!split.complete()) {
            return null;
        }
        pending.remove(msgId);
        return base64(String.join("", split.parts));
    }

    /** 첫 조각을 받은 지 {@link #SPLIT_TIMEOUT}이 지난 미완성 메시지를 버립니다. */
    private void expireParts(long now) {
        for (Iterator<Split> it = pending.values().iterator(); it.hasNext(); ) {
            if (now - it.next().firstSeen > SPLIT_TIMEOUT.toMillis()) {
                it.remove();
            }
        }
    }

    /**
     * 나머지 조각을 기다리고 있는 분할 메시지 수를 돌려줍니다.
     *
     * @return 미완성 분할 메시지 수
     */
    public synchronized int pendingSplitMessages() {
        return pending.size();
    }

    /** 조립 중인 분할 메시지입니다. 조각 내용을 번호 위치에 두고, 첫 조각을 받은 시각을 기억합니다. */
    private static final class Split {
        final String[] parts;
        final long firstSeen;

        Split(int count, long firstSeen) {
            this.parts = new String[count];
            this.firstSeen = firstSeen;
        }

        boolean complete() {
            return Arrays.stream(parts).allMatch(Objects::nonNull);
        }
    }

    // ---------------------------------------------------------------- 보조 메서드

    private String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Cannot serialize notification", e);
        }
    }

    private JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new AtlasClientException("Not a JSON notification: " + e.getOriginalMessage(), e);
        }
    }

    /** Base64를 디코딩합니다. MIME 디코더를 써서 줄바꿈 등 Base64 문자가 아닌 것은 무시합니다. */
    private static byte[] base64(String value) {
        try {
            return Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new AtlasClientException("Invalid Base64 in notification", e);
        }
    }

    private static byte[] gzip(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 4);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] bytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new AtlasClientException("Invalid GZIP content in notification", e);
        }
    }

    /** {@code msgSourceIP}에 넣을 이 호스트의 주소를 돌려줍니다. 알아낼 수 없으면 {@code 127.0.0.1}을 씁니다. */
    private String sourceIp() {
        String ip = sourceIp;
        if (ip == null) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                ip = "127.0.0.1";
            }
            sourceIp = ip;
        }
        return ip;
    }
}
