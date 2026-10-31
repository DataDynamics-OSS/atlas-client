package io.datadynamics.atlas.demo.config;

import io.datadynamics.atlas.demo.trace.TraceInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정입니다. 요청 추적용 {@link TraceInterceptor}를 등록합니다.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    private final TraceInterceptor traceInterceptor;

    /**
     * 설정을 만듭니다.
     *
     * @param traceInterceptor 웹 요청마다 Atlas 요청 수집을 시작하고 끝내는 인터셉터
     */
    public WebConfiguration(TraceInterceptor traceInterceptor) {
        this.traceInterceptor = traceInterceptor;
    }

    /**
     * {@link TraceInterceptor}를 등록합니다.
     * <p>
     * 정적 리소스(webjars, CSS, JS)와 SSE 스트림({@code /events/stream})은 Atlas를 호출하지 않거나
     * 연결이 오래 유지되므로 추적 대상에서 뺍니다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceInterceptor).excludePathPatterns("/webjars/**", "/css/**", "/js/**", "/events/stream");
    }
}
