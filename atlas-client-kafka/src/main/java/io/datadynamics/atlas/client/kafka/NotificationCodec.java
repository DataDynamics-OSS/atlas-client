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
 * Encodes and decodes the envelope Atlas wraps around every Kafka notification.
 * <pre>{@code
 * {"version":{"version":"1.0.0","versionParts":[1]},"msgCompressionKind":"NONE",
 *  "msgSplitIdx":1,"msgSplitCount":1,"msgSourceIP":"10.0.0.1","msgCreatedBy":"etl",
 *  "msgCreationTime":1700000000000,"spooled":false,"message":{...}}
 * }</pre>
 * Like Atlas, envelopes larger than {@code maxMessageBytes} are GZIP-compressed and Base64-encoded,
 * and split into several Kafka messages if they are still too large. Decoding reverses that and
 * reassembles split messages, so it keeps state and must see all parts of a message.
 */
public final class NotificationCodec {

    /** Atlas' default {@code atlas.notification.message.max.length.bytes}. */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 1_000_000;

    /** How long parts of a split message are kept waiting for the rest. */
    static final Duration SPLIT_TIMEOUT = Duration.ofMinutes(5);

    private static final String GZIP = "GZIP";
    private static final String NONE = "NONE";

    private final ObjectMapper mapper = AtlasJson.mapper();
    private final int maxMessageBytes;
    private final boolean compress;
    private final Clock clock;
    private final Map<String, Split> pending = new LinkedHashMap<>();
    private volatile String sourceIp;

    public NotificationCodec() {
        this(DEFAULT_MAX_MESSAGE_BYTES, true);
    }

    /**
     * @param maxMessageBytes size above which messages are compressed and, if still too large, split
     * @param compress        whether to GZIP large messages before splitting them
     */
    public NotificationCodec(int maxMessageBytes, boolean compress) {
        this(maxMessageBytes, compress, Clock.systemUTC());
    }

    NotificationCodec(int maxMessageBytes, boolean compress, Clock clock) {
        if (maxMessageBytes < 1) {
            throw new IllegalArgumentException("maxMessageBytes must be positive");
        }
        this.maxMessageBytes = maxMessageBytes;
        this.compress = compress;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- encoding

    /**
     * Wraps {@code message} in an envelope and returns the Kafka message values to send, in order.
     * More than one value means the message was split; all of them must be sent.
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

    private String stringMessage(String msgId, String kind, int idx, int count, String content) {
        ObjectNode node = header(kind, idx, count, msgId);
        node.put("message", content);
        return write(node);
    }

    // ---------------------------------------------------------------- decoding

    /**
     * Returns the {@code message} of a notification, or empty if {@code value} is one part of a
     * split message whose other parts have not been seen yet.
     *
     * @throws AtlasClientException if the value is not a valid notification
     */
    public synchronized Optional<JsonNode> decode(String value) {
        JsonNode node = read(value);
        if (!node.isObject() || !node.has("version") || !node.has("message")) {
            // Messages from very old hooks have no envelope.
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

    private JsonNode decodeInner(String kind, byte[] encoded) {
        byte[] bytes = GZIP.equals(kind) ? gunzip(encoded) : encoded;
        return decode(new String(bytes, StandardCharsets.UTF_8))
                .orElseThrow(() -> new AtlasClientException("Reassembled notification is itself a partial message"));
    }

    /** Stores a part and returns the decoded Base64 content once all parts are there. */
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

    private void expireParts(long now) {
        for (Iterator<Split> it = pending.values().iterator(); it.hasNext(); ) {
            if (now - it.next().firstSeen > SPLIT_TIMEOUT.toMillis()) {
                it.remove();
            }
        }
    }

    /** Number of split messages waiting for more parts. */
    public synchronized int pendingSplitMessages() {
        return pending.size();
    }

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

    // ---------------------------------------------------------------- helpers

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
