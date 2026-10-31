package io.datadynamics.atlas.client.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.json.AtlasJson;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NotificationCodec}의 봉투 인코딩, GZIP 압축, 분할과 재조립, 잘못된 메시지 거부를 검증합니다.
 */
class NotificationCodecTest {

    // 생성 시각을 고정해 봉투의 msgCreationTime을 검증할 수 있게 합니다.
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    /**
     * 한도보다 작은 메시지는 압축·분할 없이 봉투 하나로 감싸지고, 버전·분할 정보·사용자·생성 시각이 채워지며
     * {@code msgId}가 없는지 확인합니다.
     */
    @Test
    void smallMessagesGetAPlainEnvelope() throws Exception {
        List<String> parts = new NotificationCodec(1000, true, CLOCK).encode(Map.of("type", "ENTITY_CREATE_V2"), "etl");

        assertThat(parts).hasSize(1);
        JsonNode envelope = AtlasJson.mapper().readTree(parts.get(0));
        assertThat(envelope.path("version").toString()).isEqualTo("{\"version\":\"1.0.0\",\"versionParts\":[1]}");
        assertThat(envelope.path("msgCompressionKind").asText()).isEqualTo("NONE");
        assertThat(envelope.path("msgSplitIdx").asInt()).isEqualTo(1);
        assertThat(envelope.path("msgSplitCount").asInt()).isEqualTo(1);
        assertThat(envelope.path("msgCreatedBy").asText()).isEqualTo("etl");
        assertThat(envelope.path("msgCreationTime").asLong()).isEqualTo(1_700_000_000_000L);
        assertThat(envelope.has("msgId")).isFalse();
        assertThat(envelope.path("message").path("type").asText()).isEqualTo("ENTITY_CREATE_V2");
    }

    /**
     * 잘 압축되는 큰 메시지는 GZIP으로 압축되어 {@code msgId}가 있는 메시지 하나가 되고, 디코딩하면 원래 내용이
     * 나오는지 확인합니다.
     */
    @Test
    void largeMessagesAreCompressed() throws Exception {
        Map<String, String> message = Map.of("text", "x".repeat(10_000));
        NotificationCodec codec = new NotificationCodec(2000, true, CLOCK);
        List<String> parts = codec.encode(message, "etl");

        assertThat(parts).hasSize(1);
        JsonNode envelope = AtlasJson.mapper().readTree(parts.get(0));
        assertThat(envelope.path("msgCompressionKind").asText()).isEqualTo("GZIP");
        assertThat(envelope.path("msgId").asText()).isNotBlank();
        assertThat(envelope.path("message").isTextual()).isTrue();
        assertThat(codec.decode(parts.get(0)).orElseThrow().path("text").asText()).hasSize(10_000);
    }

    /**
     * 압축해도 한도를 넘는 무작위 텍스트는 여러 조각으로 나뉩니다. 조각을 거꾸로 넣어도 마지막 조각 전까지는 빈 값을,
     * 마지막 조각에서 원래 메시지를 돌려주고 대기 중인 메시지가 없어지는지 확인합니다.
     */
    @Test
    void incompressibleMessagesAreSplitAndReassembledInAnyOrder() {
        String random = randomText(20_000);
        NotificationCodec codec = new NotificationCodec(4000, true, CLOCK);
        List<String> parts = new ArrayList<>(codec.encode(Map.of("text", random), "etl"));
        assertThat(parts.size()).isGreaterThan(1);

        Collections.reverse(parts);
        for (int i = 0; i < parts.size() - 1; i++) {
            assertThat(codec.decode(parts.get(i))).isEmpty();
        }
        assertThat(codec.pendingSplitMessages()).isEqualTo(1);
        assertThat(codec.decode(parts.get(parts.size() - 1)).orElseThrow().path("text").asText()).isEqualTo(random);
        assertThat(codec.pendingSplitMessages()).isZero();
    }

    /** 압축을 끄고 나누면 압축 종류가 NONE이고, 조각 번호는 0부터 시작하며, 합치면 원래 내용이 나오는지 확인합니다. */
    @Test
    void splitsWithoutCompressionUseBase64() throws Exception {
        NotificationCodec codec = new NotificationCodec(500, false, CLOCK);
        List<String> parts = codec.encode(Map.of("text", "y".repeat(2000)), "etl");

        assertThat(parts.size()).isGreaterThan(1);
        JsonNode first = AtlasJson.mapper().readTree(parts.get(0));
        assertThat(first.path("msgCompressionKind").asText()).isEqualTo("NONE");
        assertThat(first.path("msgSplitIdx").asInt()).isZero();
        assertThat(first.path("msgSplitCount").asInt()).isEqualTo(parts.size());
        JsonNode decoded = parts.stream().map(codec::decode).filter(o -> o.isPresent()).findFirst().orElseThrow().get();
        assertThat(decoded.path("text").asText()).hasSize(2000);
    }

    /** Atlas가 실제로 쓰는 형식의 알림을 디코딩해 본문을 꺼낼 수 있는지 확인합니다. */
    @Test
    void decodesNotificationsWrittenByAtlas() {
        String atlas = "{\"version\":{\"version\":\"1.0.0\",\"versionParts\":[1]},\"msgCompressionKind\":\"NONE\","
                + "\"msgSplitIdx\":1,\"msgSplitCount\":1,\"msgSourceIP\":\"172.17.0.2\",\"msgCreatedBy\":\"\","
                + "\"msgCreationTime\":1700000000000,\"spooled\":false,"
                + "\"message\":{\"type\":\"ENTITY_NOTIFICATION_V2\",\"operationType\":\"ENTITY_CREATE\"}}";

        assertThat(new NotificationCodec().decode(atlas).orElseThrow().path("operationType").asText())
                .isEqualTo("ENTITY_CREATE");
    }

    /** JSON이 아닌 값과 조각 번호가 범위를 벗어난 분할 메시지는 {@link AtlasClientException}으로 거부되는지 확인합니다. */
    @Test
    void invalidMessagesAreRejected() {
        NotificationCodec codec = new NotificationCodec();
        assertThatThrownBy(() -> codec.decode("not json")).isInstanceOf(AtlasClientException.class);
        assertThatThrownBy(() -> codec.decode("{\"version\":{},\"msgSplitCount\":2,\"msgSplitIdx\":5,"
                + "\"msgId\":\"m\",\"message\":\"\"}")).isInstanceOf(AtlasClientException.class);
    }

    /** 시드를 고정한, 잘 압축되지 않는 출력 가능한 ASCII 무작위 문자열을 만듭니다. */
    private static String randomText(int length) {
        Random random = new Random(42);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) ('!' + random.nextInt(90)));
        }
        return text.toString();
    }
}
