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

@Configuration(proxyBeanMethods = false)
public class AtlasConfiguration {

    /** Set when Kafka pages are enabled. */
    static final String KAFKA_ENABLED = "'${atlas.kafka.bootstrap-servers:}' != ''";

    @Bean
    AtlasClient atlasClient(DemoProperties properties, RequestTrace trace) {
        return AtlasClient.builder()
                .serverUrls(properties.urls())
                .basicAuth(properties.username(), properties.password())
                .userAgent("atlas-client-demo")
                .connectTimeout(Duration.ofSeconds(5))
                // The demo should answer quickly while Atlas is still starting.
                .retryPolicy(RetryPolicy.builder().maxAttempts(2).initialBackoff(Duration.ofMillis(200)).build())
                .requestListener(trace::record)
                .build();
    }

    @Bean
    TypeDefManager typeDefManager(AtlasClient client) {
        return new TypeDefManager(client);
    }

    /**
     * Shared so the process/dataset answer per type is fetched once, not on every lineage page.
     * {@code AtlasLineage} itself holds per-call settings, so controllers create their own.
     */
    @Bean
    ProcessTypeResolver processTypeResolver(AtlasClient client) {
        return new ProcessTypeResolver(client);
    }

    @Bean
    ConsistencyChecker consistencyChecker(AtlasClient client) {
        return new ConsistencyChecker(client);
    }

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
