package io.datadynamics.atlas.client.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.message.EntityCreateRequest;
import io.datadynamics.atlas.client.kafka.message.HookMessage;
import io.datadynamics.atlas.client.kafka.message.HookMessageType;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AtlasHookPublisher}가 보내는 hook 메시지의 내용, 사전 검증, 분할 전송, Kafka 오류 전달을
 * Kafka {@link MockProducer}로 검증합니다.
 */
class AtlasHookPublisherTest {

    // 보내는 즉시 성공으로 완료되는 producer입니다.
    private final MockProducer<String, String> producer =
            new MockProducer<>(true, new StringSerializer(), new StringSerializer());

    private AtlasHookPublisher publisher() {
        return AtlasHookPublisher.builder().producer(producer).user("etl").build();
    }

    /** {@code index}번째로 보낸 Kafka 메시지의 봉투를 풀어 hook 메시지 본문을 돌려줍니다. */
    private JsonNode sentMessage(int index) {
        return new NotificationCodec().decode(producer.history().get(index).value()).orElseThrow();
    }

    private static AtlasEntity table(String qualifiedName) {
        return new AtlasEntity("hive_table", Map.of("qualifiedName", qualifiedName, "name", "orders"));
    }

    /**
     * 엔티티 하나를 {@code createOrUpdate}로 보냅니다.
     * 기대 결과: ATLAS_HOOK 토픽에 키 없는 메시지 하나가 기록되고, 본문은 ENTITY_CREATE_V2 타입에 빌더에서 정한
     * 사용자와 엔티티를 담습니다.
     */
    @Test
    void createOrUpdateSendsEntityCreateV2() {
        PublishResult result = publisher().createOrUpdate(table("db.orders@prod"));

        assertThat(result.type()).isEqualTo(HookMessageType.ENTITY_CREATE_V2);
        assertThat(result.topic()).isEqualTo("ATLAS_HOOK");
        assertThat(result.parts()).isEqualTo(1);
        ProducerRecord<String, String> record = producer.history().get(0);
        assertThat(record.topic()).isEqualTo("ATLAS_HOOK");
        assertThat(record.key()).isNull();

        JsonNode message = sentMessage(0);
        assertThat(message.path("type").asText()).isEqualTo("ENTITY_CREATE_V2");
        assertThat(message.path("user").asText()).isEqualTo("etl");
        JsonNode entity = message.path("entities").path("entities").get(0);
        assertThat(entity.path("typeName").asText()).isEqualTo("hive_table");
        assertThat(entity.path("attributes").path("qualifiedName").asText()).isEqualTo("db.orders@prod");
    }

    /**
     * 부분 갱신과 삭제가 대상을 typeName과 qualifiedName(uniqueAttributes)으로 지정하고, 부분 갱신의
     * {@code patch}에 typeName이 채워지는지 확인합니다.
     */
    @Test
    void partialUpdateAndDeleteAddressEntitiesByQualifiedName() {
        AtlasHookPublisher publisher = publisher();
        AtlasEntity patch = new AtlasEntity();
        patch.setAttribute("owner", "data-team");
        publisher.partialUpdateByQualifiedName("hive_table", "db.orders@prod", patch);
        publisher.deleteByQualifiedName("hive_table", "db.tmp@prod");

        JsonNode update = sentMessage(0);
        assertThat(update.path("type").asText()).isEqualTo("ENTITY_PARTIAL_UPDATE_V2");
        assertThat(update.path("entityId").toString())
                .isEqualTo("{\"typeName\":\"hive_table\",\"uniqueAttributes\":{\"qualifiedName\":\"db.orders@prod\"}}");
        assertThat(update.path("entity").path("entity").path("typeName").asText()).isEqualTo("hive_table");
        assertThat(update.path("entity").path("entity").path("attributes").path("owner").asText()).isEqualTo("data-team");

        JsonNode delete = sentMessage(1);
        assertThat(delete.path("type").asText()).isEqualTo("ENTITY_DELETE_V2");
        assertThat(delete.path("entities").get(0).path("uniqueAttributes").path("qualifiedName").asText())
                .isEqualTo("db.tmp@prod");
    }

    /**
     * guid만 있거나 qualifiedName이 비어 있는 대상, 빈 엔티티 목록, typeName 없는 엔티티처럼 Atlas가 처리할 수 없는
     * 메시지는 {@link IllegalArgumentException}으로 거부되고 아무것도 보내지 않는지 확인합니다.
     */
    @Test
    void messagesAtlasCannotProcessAreRejectedBeforeSending() {
        AtlasHookPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.delete(AtlasObjectId.byGuid("g-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uniqueAttributes");
        assertThatThrownBy(() -> publisher.deleteByQualifiedName("hive_table", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uniqueAttributes");
        assertThatThrownBy(() -> publisher.partialUpdateByQualifiedName("hive_table", "", new AtlasEntity()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uniqueAttributes");
        assertThatThrownBy(() -> publisher.createOrUpdate(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one entity");
        assertThatThrownBy(() -> publisher.createOrUpdate(new AtlasEntity()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without typeName");
        assertThat(producer.history()).isEmpty();
    }

    /**
     * 압축을 끄고 최대 크기를 작게 잡아 메시지를 여러 조각으로 나눕니다.
     * 기대 결과: 조각 수가 결과의 {@code parts}와 같고, 모든 조각이 null이 아닌 같은 키를 쓰며, 다시 합치면 원래
     * 엔티티 두 개가 나옵니다.
     */
    @Test
    void largeMessagesAreSplitUnderOneKey() {
        AtlasHookPublisher publisher = AtlasHookPublisher.builder().producer(producer)
                .maxMessageBytes(300).compression(false).build();
        PublishResult result = publisher.createOrUpdate(table("db.big@prod"), table("db.big2@prod"));

        assertThat(result.parts()).isGreaterThan(1).isEqualTo(producer.history().size());
        assertThat(producer.history().stream().map(ProducerRecord::key).collect(Collectors.toSet())).hasSize(1)
                .doesNotContainNull();
        NotificationCodec codec = new NotificationCodec();
        JsonNode message = producer.history().stream().map(r -> codec.decode(r.value()))
                .filter(o -> o.isPresent()).findFirst().orElseThrow().get();
        assertThat(message.path("entities").path("entities")).hasSize(2);
    }

    /**
     * 수동 완료 {@link MockProducer}로 Kafka 응답을 조절합니다.
     * 기대 결과: {@code sendAsync}의 future는 Kafka가 확인한 뒤에야 완료되고, 동기 전송 중 Kafka 오류가 나면 원인 메시지를
     * 담은 {@link AtlasClientException}이 납니다.
     */
    @Test
    void kafkaFailuresSurfaceFromSynchronousSends() throws Exception {
        MockProducer<String, String> manual = new MockProducer<>(false, new StringSerializer(), new StringSerializer());
        AtlasHookPublisher publisher = AtlasHookPublisher.builder().producer(manual)
                .sendTimeout(Duration.ofSeconds(5)).build();

        CompletableFuture<PublishResult> async = publisher.sendAsync(new EntityCreateRequest(
                new io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo(List.of(table("db.a@prod")))));
        assertThat(async).isNotDone();
        manual.completeNext();
        assertThat(async.get().offset()).isZero();

        // 동기 전송이 시작되어 producer에 미완료 전송이 생길 때까지 기다렸다가 그 전송을 실패시킵니다.
        Thread failer = new Thread(() -> {
            while (!manual.errorNext(new RuntimeException("broker down"))) {
                Thread.onSpinWait();
            }
        });
        failer.start();
        assertThatThrownBy(() -> publisher.createOrUpdate(table("db.b@prod")))
                .isInstanceOf(AtlasClientException.class).hasMessageContaining("broker down");
        failer.join();
    }

    /**
     * hook 메시지 JSON을 {@link HookMessage}로 읽으면 {@code type}에 맞는 하위 클래스가 되고, 알 수 없는 속성이 남지
     * 않으며, 다시 쓰면 원래 JSON과 같은지 확인합니다.
     */
    @Test
    void hookMessagesRoundTripThroughJson() {
        String json = "{\"type\":\"ENTITY_DELETE_V2\",\"user\":\"hive\",\"entities\":[{\"typeName\":\"hive_table\","
                + "\"uniqueAttributes\":{\"qualifiedName\":\"db.t@prod\"}}]}";
        HookMessage message = AtlasJson.fromJson(json, HookMessage.class);

        assertThat(message.getType()).isEqualTo(HookMessageType.ENTITY_DELETE_V2);
        assertThat(message.getAdditionalProperties()).isEmpty();
        assertThat(AtlasJson.toJson(message)).isEqualTo(json);
    }
}
