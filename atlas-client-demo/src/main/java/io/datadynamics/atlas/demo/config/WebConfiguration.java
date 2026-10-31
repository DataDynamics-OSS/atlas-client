package io.datadynamics.atlas.demo.config;

import io.datadynamics.atlas.demo.trace.TraceInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    private final TraceInterceptor traceInterceptor;

    public WebConfiguration(TraceInterceptor traceInterceptor) {
        this.traceInterceptor = traceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceInterceptor).excludePathPatterns("/webjars/**", "/css/**", "/js/**", "/events/stream");
    }
}
