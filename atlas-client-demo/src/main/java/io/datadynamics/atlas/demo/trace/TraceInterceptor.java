package io.datadynamics.atlas.demo.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Collects the Atlas requests of each web request and exposes them to the views as {@code trace}. */
@Component
public class TraceInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = "trace";

    private final RequestTrace trace;

    public TraceInterceptor(RequestTrace trace) {
        this.trace = trace;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTRIBUTE, trace.begin());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        trace.end();
    }
}
