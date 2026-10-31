package io.datadynamics.atlas.demo.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 웹 요청마다 그 처리 중에 보낸 Atlas 요청을 모아, 뷰에서 {@code trace}라는 요청 속성으로 쓸 수 있게 합니다.
 * <p>
 * 컨트롤러가 실행되기 전에 {@link RequestTrace#begin()}으로 수집을 시작하고, 요청 처리가 끝나면
 * (예외가 나도) {@link RequestTrace#end()}로 정리합니다.
 */
@Component
public class TraceInterceptor implements HandlerInterceptor {

    /** 수집한 요청 목록을 담는 서블릿 요청 속성 이름입니다. */
    static final String ATTRIBUTE = "trace";

    private final RequestTrace trace;

    /**
     * 인터셉터를 만듭니다.
     *
     * @param trace 요청을 기록하는 {@link RequestTrace}
     */
    public TraceInterceptor(RequestTrace trace) {
        this.trace = trace;
    }

    /**
     * 요청 수집을 시작하고, 수집 목록을 {@link #ATTRIBUTE} 속성에 넣습니다.
     * 목록은 이후 요청이 계속 추가되는 살아 있는 목록이므로 뷰를 그릴 때는 컨트롤러가 보낸 요청이 모두 들어 있습니다.
     *
     * @return 항상 {@code true}(요청 처리를 계속합니다)
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTRIBUTE, trace.begin());
        return true;
    }

    /** 요청 처리가 끝나면(예외 여부와 관계없이) 현재 스레드의 수집을 끝냅니다. */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        trace.end();
    }
}
