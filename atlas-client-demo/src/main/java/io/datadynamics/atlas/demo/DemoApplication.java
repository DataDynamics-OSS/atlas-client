package io.datadynamics.atlas.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 실행 중인 Atlas 서버를 대상으로 Atlas 클라이언트 라이브러리를 직접 써 볼 수 있는 웹 데모 애플리케이션입니다.
 * <p>
 * Spring Boot 애플리케이션의 시작점입니다. {@code @ConfigurationPropertiesScan}으로
 * {@link io.datadynamics.atlas.demo.config.DemoProperties} 같은 설정 record를 자동으로 찾아 바인딩합니다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoApplication {

    /**
     * 데모 웹 애플리케이션을 시작합니다.
     *
     * @param args 명령줄 인자. Spring Boot가 {@code --atlas.urls=...}처럼 설정 값으로 해석합니다.
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
