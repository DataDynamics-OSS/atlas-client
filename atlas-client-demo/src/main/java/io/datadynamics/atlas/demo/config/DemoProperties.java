package io.datadynamics.atlas.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 데모의 연결 설정입니다. {@code atlas.*} 속성(또는 {@code ATLAS_URLS}, {@code ATLAS_KAFKA_BOOTSTRAP_SERVERS} 같은
 * 환경 변수)에서 바인딩됩니다.
 *
 * @param urls     쉼표로 구분한 Atlas 서버 URL 목록. 여러 개를 주면 HA 구성으로 동작합니다.
 * @param username basic 인증 사용자 이름
 * @param password basic 인증 비밀번호
 * @param kafka    Atlas가 쓰는 Kafka 설정. {@code bootstrap-servers}를 비워 두면 Kafka 페이지가 꺼집니다.
 */
@ConfigurationProperties("atlas")
public record DemoProperties(@DefaultValue("http://localhost:21000") String urls,
                                @DefaultValue("admin") String username,
                                @DefaultValue("admin") String password,
                                @DefaultValue Kafka kafka) {

    /**
     * Atlas hook 메시지를 보내고 엔티티 알림을 받을 Kafka 설정입니다.
     *
     * @param bootstrapServers Kafka 주소. 예: {@code localhost:9092}
     * @param user             hook 메시지에 기록되는 사용자 이름
     */
    public record Kafka(String bootstrapServers, @DefaultValue("atlas-client-demo") String user) {

        /**
         * Kafka 기능을 쓸 수 있는지 알려 줍니다.
         *
         * @return {@code bootstrapServers}가 null이 아니고 공백이 아닌 값이면 {@code true}
         */
        public boolean enabled() {
            return bootstrapServers != null && !bootstrapServers.isBlank();
        }
    }
}
