package io.datadynamics.atlas.demo.config;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.cli.ConsistencyChecker;
import io.datadynamics.atlas.client.http.RetryPolicy;
import io.datadynamics.atlas.client.kafka.AtlasHookPublisher;
import io.datadynamics.atlas.client.lineage.ProcessTypeResolver;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import io.datadynamics.atlas.demo.trace.RequestTrace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 데모가 쓰는 Atlas 클라이언트 관련 빈을 만드는 Spring 설정입니다.
 * <p>
 * {@link AtlasClient} 하나를 만들어 두고, 타입 관리({@link TypeDefManager}), 리니지 타입 판별
 * ({@link ProcessTypeResolver}), 정합성 검사({@link ConsistencyChecker})가 모두 이 클라이언트를 공유합니다.
 * Kafka 주소가 설정된 경우에만 hook 메시지 발행용 {@link AtlasHookPublisher}를 추가로 만듭니다.
 */
@Configuration(proxyBeanMethods = false)
public class AtlasConfiguration {

    /**
     * Kafka 페이지를 켤지 판단하는 SpEL 조건식입니다.
     * {@code atlas.kafka.bootstrap-servers}가 비어 있지 않을 때 참이 됩니다.
     */
    static final String KAFKA_ENABLED = "'${atlas.kafka.bootstrap-servers:}' != ''";

    /**
     * 데모 전체가 공유하는 Atlas REST 클라이언트를 만듭니다.
     * <p>
     * 설정의 서버 URL(여러 개면 HA)과 basic 인증 정보를 쓰고, 보낸 모든 HTTP 시도를
     * {@link RequestTrace#record}로 넘겨 화면에 요청 내역을 보여 줄 수 있게 합니다.
     */
    @Bean
    AtlasClient atlasClient(DemoProperties properties, RequestTrace trace) {
        return AtlasClient.builder()
                .serverUrls(properties.urls())
                .basicAuth(properties.username(), properties.password())
                .userAgent("atlas-client-demo")
                .connectTimeout(Duration.ofSeconds(5))
                // Atlas가 아직 기동 중일 때도 데모 화면이 오래 멈추지 않고 빨리 응답하도록
                // 재시도를 2번, 첫 대기 시간을 200ms로 짧게 잡습니다.
                .retryPolicy(RetryPolicy.builder().maxAttempts(2).initialBackoff(Duration.ofMillis(200)).build())
                .requestListener(trace::record)
                .build();
    }

    /** 타입 정의의 plan/apply를 수행하는 {@link TypeDefManager}를 만듭니다. */
    @Bean
    TypeDefManager typeDefManager(AtlasClient client) {
        return new TypeDefManager(client);
    }

    /**
     * 타입마다 프로세스인지 데이터셋인지 판별한 결과를 리니지 페이지마다 다시 조회하지 않고
     * 한 번만 가져오도록 빈으로 만들어 공유합니다.
     * {@code AtlasLineage} 자체는 호출마다 다른 설정을 담으므로 공유하지 않고 컨트롤러가 각자 만듭니다.
     */
    @Bean
    ProcessTypeResolver processTypeResolver(AtlasClient client) {
        return new ProcessTypeResolver(client);
    }

    /** 메타데이터 정합성 검사({@code atlas check}와 같은 기능)를 수행하는 {@link ConsistencyChecker}를 만듭니다. */
    @Bean
    ConsistencyChecker consistencyChecker(AtlasClient client) {
        return new ConsistencyChecker(client);
    }

    /**
     * Atlas hook 메시지를 Kafka로 보내는 {@link AtlasHookPublisher}를 만듭니다.
     * <p>
     * {@link #KAFKA_ENABLED} 조건이 참일 때(Kafka 주소가 설정되어 있을 때)만 등록되며,
     * 애플리케이션이 종료될 때 {@code close}가 호출되어 Kafka producer를 닫습니다.
     * 전송 제한 시간은 15초, producer의 {@code max.block.ms}는 10초로 둡니다.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression(KAFKA_ENABLED)
    AtlasHookPublisher atlasHookPublisher(DemoProperties properties) {
        return AtlasHookPublisher.builder()
                .bootstrapServers(properties.kafka().bootstrapServers())
                .user(properties.kafka().user())
                .sendTimeout(Duration.ofSeconds(15))
                .property("max.block.ms", 10_000)
                .build();
    }
}
