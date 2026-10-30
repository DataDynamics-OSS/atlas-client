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

class AtlasHookPublisherTest {

    private final MockProducer<String, String> producer =
            new MockProducer<>(true, new StringSerializer(), new StringSerializer());

    private AtlasHookPublisher publisher() {
        return AtlasHookPublisher.builder().producer(producer).user("etl").build();
    }

    private JsonNode sentMessage(int index) {
        return new NotificationCodec().decode(producer.history().get(index).value()).orElseThrow();
    }

    private static AtlasEntity table(String qualifiedName) {
        return new AtlasEntity("hive_table", Map.of("qualifiedName", qualifiedName, "name", "orders"));
    }

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

    @Test
    void messagesAtlasCannotProcessAreRejectedBeforeSending() {
        AtlasHookPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.delete(AtlasObjectId.byGuid("g-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uniqueAttributes");
        assertThatThrownBy(() -> publisher.createOrUpdate(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one entity");
        assertThatThrownBy(() -> publisher.createOrUpdate(new AtlasEntity()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without typeName");
        assertThat(producer.history()).isEmpty();
    }

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
