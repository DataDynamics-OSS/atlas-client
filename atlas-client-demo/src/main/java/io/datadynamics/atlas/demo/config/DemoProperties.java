package io.datadynamics.atlas.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings, bound from {@code atlas.*} (or {@code ATLAS_URLS}, {@code ATLAS_KAFKA_BOOTSTRAP_SERVERS}, ...).
 *
 * @param urls     comma separated Atlas server URLs; several enable HA
 * @param username basic authentication user
 * @param password basic authentication password
 * @param kafka    Kafka used by Atlas; leave {@code bootstrap-servers} empty to disable the Kafka pages
 */
@ConfigurationProperties("atlas")
public record DemoProperties(@DefaultValue("http://localhost:21000") String urls,
                                @DefaultValue("admin") String username,
                                @DefaultValue("admin") String password,
                                @DefaultValue Kafka kafka) {

    /**
     * @param bootstrapServers Kafka address, e.g. {@code localhost:9092}
     * @param user             user recorded in hook messages
     */
    public record Kafka(String bootstrapServers, @DefaultValue("atlas-client-demo") String user) {

        public boolean enabled() {
            return bootstrapServers != null && !bootstrapServers.isBlank();
        }
    }
}
